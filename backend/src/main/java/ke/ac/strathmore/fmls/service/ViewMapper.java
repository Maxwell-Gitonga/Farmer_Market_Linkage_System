package ke.ac.strathmore.fmls.service;

import ke.ac.strathmore.fmls.domain.Farmer;
import ke.ac.strathmore.fmls.domain.Message;
import ke.ac.strathmore.fmls.domain.Product;
import ke.ac.strathmore.fmls.domain.Transaction;
import ke.ac.strathmore.fmls.dto.MessageView;
import ke.ac.strathmore.fmls.dto.ProductView;
import ke.ac.strathmore.fmls.dto.TransactionView;
import ke.ac.strathmore.fmls.module.CropCatalog;
import ke.ac.strathmore.fmls.module.MarketDataModule;
import ke.ac.strathmore.fmls.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Maps entities to API views (adds the local market price so buyers can compare). */
@Component
public class ViewMapper {

    private final MarketDataModule market;

    public ViewMapper(MarketDataModule market) {
        this.market = market;
    }

    public ProductView product(Product p) {
        return product(p, market.latestPrices());
    }

    public ProductView product(Product p, Map<String, Double> latestPrices) {
        Farmer f = p.getFarmer();
        Double mp = latestPrices.get(p.getCropType() + "|" + p.getLocation());
        if (mp == null) mp = latestPrices.get(p.getCropType() + "|*");
        Double vs = mp != null && p.getPricePerUnit() != null
                ? Math.round(1000.0 * (p.getPricePerUnit() / mp - 1)) / 10.0 : null;
        return new ProductView(p.getProductId(), p.getCropType(), CropCatalog.emoji(p.getCropType()), p.getQuantity(),
                p.getUnit(), p.getHarvestDate(), p.getLocation(), p.getDescription(), p.getStatus(), p.getPricePerUnit(),
                p.getRecommendedPrice(), f.getId(), f.getName(), f.getPhone(), f.getLocation(), mp, vs,
                p.getCreatedAt(), p.getUpdatedAt());
    }

    public static TransactionView transaction(Transaction t) {
        double qty = t.getQuantity() == null ? 0 : t.getQuantity();
        return new TransactionView(t.getTransactionId(), t.getProduct().getProductId(), t.getProduct().getCropType(),
                t.getQuantity(), t.getProduct().getUnit(), t.getAgreedPrice(),
                Math.round(qty * t.getAgreedPrice() * 100) / 100.0, t.getStatus(), t.getTransactionDate(),
                t.getFarmer().getId(), t.getFarmer().getName(), t.getFarmer().getPhone(), t.getBuyer().getId(),
                t.getBuyer().getName(), t.getBuyer().getPhone());
    }

    public static MessageView message(Message m, CurrentUser me) {
        String sender = m.getSenderRole().name().equals("FARMER") ? m.getFarmer().getName() : m.getBuyer().getName();
        String label = m.getProduct() == null ? null
                : m.getProduct().getCropType() + " (" + m.getProduct().getQuantity() + " " + m.getProduct().getUnit() + ")";
        return new MessageView(m.getMessageId(), m.getSenderRole(), sender, m.getContent(), m.getCreatedAt(),
                m.getProduct() == null ? null : m.getProduct().getProductId(), label, m.getSenderRole() == me.role());
    }
}
