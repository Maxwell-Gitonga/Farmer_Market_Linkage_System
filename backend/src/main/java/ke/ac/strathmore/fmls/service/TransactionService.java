package ke.ac.strathmore.fmls.service;

import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Buyer;
import ke.ac.strathmore.fmls.domain.Product;
import ke.ac.strathmore.fmls.domain.ProductStatus;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.domain.Transaction;
import ke.ac.strathmore.fmls.domain.TransactionStatus;
import ke.ac.strathmore.fmls.dto.PurchaseRequest;
import ke.ac.strathmore.fmls.dto.PurchaseResponse;
import ke.ac.strathmore.fmls.dto.TransactionView;
import ke.ac.strathmore.fmls.module.MarketDataModule;
import ke.ac.strathmore.fmls.repository.ProductRepository;
import ke.ac.strathmore.fmls.repository.TransactionRepository;
import ke.ac.strathmore.fmls.security.AuthContext;
import ke.ac.strathmore.fmls.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Purchases (sequence diagram 2): purchaseProduct(productID) → createTransaction() → return transactionID
 * → updateStatus('sold') → "Purchase confirmed". Payment processing and logistics are out of scope (§1.6).
 */
@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final ProductRepository products;
    private final UserDirectory users;
    private final MarketDataModule market;
    private final ActivityLogService activity;

    public TransactionService(TransactionRepository transactions, ProductRepository products, UserDirectory users,
                              MarketDataModule market, ActivityLogService activity) {
        this.transactions = transactions;
        this.products = products;
        this.users = users;
        this.market = market;
        this.activity = activity;
    }

    @Transactional
    public PurchaseResponse purchaseProduct(PurchaseRequest req) {
        Buyer buyer = users.currentBuyer();
        Product p = products.findByIdForUpdate(req.productId()).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (p.getStatus() != ProductStatus.AVAILABLE) throw ApiException.conflict("This produce is no longer available");
        if (p.getFarmer().getStatus() != AccountStatus.ACTIVE) throw ApiException.conflict("This seller is not active");
        double available = p.getQuantity() == null ? 0 : p.getQuantity();
        if (req.quantity() > available + 1e-9) {
            throw ApiException.badRequest("Only " + available + " " + p.getUnit() + " available");
        }
        Transaction t = createTransaction(p, buyer, req.quantity());
        updateStatus(p, available - req.quantity());
        activity.log("PURCHASE", buyer.getName() + " bought " + req.quantity() + p.getUnit() + " of " + p.getCropType()
                + " from " + p.getFarmer().getName() + " at KES " + p.getPricePerUnit() + " (transaction #"
                + t.getTransactionId() + ")");
        return new PurchaseResponse(t.getTransactionId(), "Purchase confirmed", ViewMapper.transaction(t));
    }

    /** createTransaction(): persists the transaction and returns it (with its transactionID). */
    Transaction createTransaction(Product p, Buyer buyer, double quantity) {
        Transaction t = new Transaction();
        t.setProduct(p);
        t.setFarmer(p.getFarmer());
        t.setBuyer(buyer);
        t.setQuantity(quantity);
        t.setAgreedPrice(p.getPricePerUnit());
        t.setStatus(TransactionStatus.CONFIRMED);
        t.setTransactionDate(LocalDateTime.now());
        return transactions.save(t);
    }

    /** updateStatus('sold') when everything is bought; otherwise the remaining quantity stays listed. */
    void updateStatus(Product p, double remaining) {
        if (remaining <= 1e-9) {
            p.setQuantity(0.0);
            p.setStatus(ProductStatus.SOLD);
        } else {
            p.setQuantity(Math.round(remaining * 100) / 100.0);
        }
        products.save(p);
    }

    @Transactional(readOnly = true)
    public List<TransactionView> mine() {
        CurrentUser me = AuthContext.get();
        List<Transaction> list = switch (me.role()) {
            case BUYER -> transactions.forBuyer(me.id());
            case FARMER -> transactions.forFarmerSince(me.id(), LocalDateTime.now().minusDays(365));
            case ADMIN -> transactions.since(LocalDateTime.now().minusDays(30));
        };
        return list.stream().limit(200).map(ViewMapper::transaction).collect(Collectors.toList());
    }

    /**
     * Farmer: CONFIRMED → COMPLETED (delivered) or CANCELLED. Buyer: CONFIRMED → CANCELLED.
     * Cancelling returns the quantity to the listing; completing records the sale as market data.
     */
    @Transactional
    public TransactionView changeStatus(Long id, TransactionStatus target) {
        CurrentUser me = AuthContext.get();
        Transaction t = transactions.findById(id).orElseThrow(() -> ApiException.notFound("Transaction not found"));
        boolean isFarmer = me.role() == Role.FARMER && t.getFarmer().getId().equals(me.id());
        boolean isBuyer = me.role() == Role.BUYER && t.getBuyer().getId().equals(me.id());
        if (!isFarmer && !isBuyer && me.role() != Role.ADMIN) throw ApiException.forbidden("Not your transaction");
        if (t.getStatus() != TransactionStatus.CONFIRMED && t.getStatus() != TransactionStatus.PENDING) {
            throw ApiException.badRequest("Transaction is already " + t.getStatus().name().toLowerCase());
        }
        if (target == TransactionStatus.COMPLETED) {
            if (!isFarmer && me.role() != Role.ADMIN) throw ApiException.forbidden("Only the farmer can mark a sale as completed");
            t.setStatus(TransactionStatus.COMPLETED);
            market.recordSale(t);
        } else if (target == TransactionStatus.CANCELLED) {
            t.setStatus(TransactionStatus.CANCELLED);
            Product p = t.getProduct();
            double qty = (p.getQuantity() == null ? 0 : p.getQuantity()) + (t.getQuantity() == null ? 0 : t.getQuantity());
            p.setQuantity(Math.round(qty * 100) / 100.0);
            if (p.getStatus() == ProductStatus.SOLD) p.setStatus(ProductStatus.AVAILABLE);
            products.save(p);
        } else {
            throw ApiException.badRequest("Status can only be changed to COMPLETED or CANCELLED");
        }
        transactions.save(t);
        activity.log("TRANSACTION_" + target.name(), "Transaction #" + t.getTransactionId() + " (" + t.getProduct().getCropType() + ")");
        return ViewMapper.transaction(t);
    }
}
