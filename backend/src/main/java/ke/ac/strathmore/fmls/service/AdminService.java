package ke.ac.strathmore.fmls.service;

import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Admin;
import ke.ac.strathmore.fmls.domain.Buyer;
import ke.ac.strathmore.fmls.domain.Farmer;
import ke.ac.strathmore.fmls.domain.Product;
import ke.ac.strathmore.fmls.domain.ProductStatus;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.domain.SystemLog;
import ke.ac.strathmore.fmls.domain.Transaction;
import ke.ac.strathmore.fmls.domain.TransactionStatus;
import ke.ac.strathmore.fmls.domain.User;
import ke.ac.strathmore.fmls.dto.AdminOverview;
import ke.ac.strathmore.fmls.dto.AdminUserView;
import ke.ac.strathmore.fmls.dto.CreateAdminRequest;
import ke.ac.strathmore.fmls.dto.CropSummary;
import ke.ac.strathmore.fmls.dto.LogView;
import ke.ac.strathmore.fmls.dto.MonthlySummary;
import ke.ac.strathmore.fmls.dto.ProductView;
import ke.ac.strathmore.fmls.dto.ReportView;
import ke.ac.strathmore.fmls.module.MarketDataModule;
import ke.ac.strathmore.fmls.repository.AdminRepository;
import ke.ac.strathmore.fmls.repository.BuyerRepository;
import ke.ac.strathmore.fmls.repository.FarmerRepository;
import ke.ac.strathmore.fmls.repository.PricePredictionRepository;
import ke.ac.strathmore.fmls.repository.ProductRepository;
import ke.ac.strathmore.fmls.repository.SystemLogRepository;
import ke.ac.strathmore.fmls.repository.TransactionRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Administration (use case diagram – Admin): manage users, approve farmer accounts, remove unwanted
 * listings, monitor system activity and view system reports.
 */
@Service
public class AdminService {

    private final FarmerRepository farmers;
    private final BuyerRepository buyers;
    private final AdminRepository admins;
    private final ProductRepository products;
    private final TransactionRepository transactions;
    private final SystemLogRepository logs;
    private final PricePredictionRepository predictions;
    private final MarketDataModule market;
    private final ViewMapper mapper;
    private final ActivityLogService activity;
    private final MlAdminService mlAdmin;
    private final BCryptPasswordEncoder encoder;

    public AdminService(FarmerRepository farmers, BuyerRepository buyers, AdminRepository admins,
                        ProductRepository products, TransactionRepository transactions, SystemLogRepository logs,
                        PricePredictionRepository predictions, MarketDataModule market, ViewMapper mapper,
                        ActivityLogService activity, MlAdminService mlAdmin, BCryptPasswordEncoder encoder) {
        this.encoder = encoder;
        this.farmers = farmers;
        this.buyers = buyers;
        this.admins = admins;
        this.products = products;
        this.transactions = transactions;
        this.logs = logs;
        this.predictions = predictions;
        this.market = market;
        this.mapper = mapper;
        this.activity = activity;
        this.mlAdmin = mlAdmin;
    }

    @Transactional(readOnly = true)
    public AdminOverview overview() {
        LocalDateTime month = LocalDateTime.now().minusDays(30);
        double volume = transactions.summaryByCrop(TransactionStatus.CANCELLED, month).stream()
                .mapToDouble(r -> r[3] == null ? 0 : ((Number) r[3]).doubleValue()).sum();
        Map<String, Object> ml = mlAdmin.status();
        Map<String, Object> mlBrief = new LinkedHashMap<>();
        mlBrief.put("status", ml.get("status"));
        mlBrief.put("retraining", ml.get("retraining"));
        return new AdminOverview(farmers.count(), farmers.countByStatus(AccountStatus.PENDING), buyers.count(),
                products.countByStatus(ProductStatus.AVAILABLE), products.countByStatus(ProductStatus.SOLD),
                products.countByStatus(ProductStatus.REMOVED), transactions.countByTransactionDateAfter(month),
                Math.round(volume), predictions.countByCreatedAtAfter(month),
                logs.countByCreatedAtAfter(LocalDateTime.now().minusHours(24)), market.count(), market.latestDate(), mlBrief);
    }

    @Transactional(readOnly = true)
    public List<AdminUserView> users(Role role, AccountStatus status) {
        List<AdminUserView> out = new ArrayList<>();
        if (role == null || role == Role.FARMER) farmers.findAllByOrderByCreatedAtDesc().forEach(f -> out.add(view(f)));
        if (role == null || role == Role.BUYER) buyers.findAllByOrderByCreatedAtDesc().forEach(b -> out.add(view(b)));
        if (role == null || role == Role.ADMIN) admins.findAll().forEach(a -> out.add(view(a)));
        return out.stream().filter(u -> status == null || u.status() == status)
                .sorted(Comparator.comparing((AdminUserView u) -> u.status() != AccountStatus.PENDING)
                        .thenComparing(AdminUserView::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private static AdminUserView view(User u) {
        return new AdminUserView(u.getId(), u.getRole(), u.getName(), u.getPhone(), u.getEmail(), u.getLocation(),
                u.getStatus(), u.getCreatedAt(), u.getLastLoginAt(), u instanceof Buyer b ? b.getPreferences() : null);
    }

    /** "Add administrator": an existing administrator creates another administrator account. */
    @Transactional
    public AdminUserView createAdmin(CreateAdminRequest req) {
        String email = req.email().trim().toLowerCase();
        if (admins.existsByEmailIgnoreCase(email) || farmers.existsByEmailIgnoreCase(email)
                || buyers.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("This email is already registered");
        }
        String phone = req.phone() == null || req.phone().isBlank() ? null : UserDirectory.normalizePhone(req.phone());
        if (phone != null && (admins.existsByPhone(phone) || farmers.existsByPhone(phone) || buyers.existsByPhone(phone))) {
            throw ApiException.conflict("This phone number is already registered");
        }
        Admin a = new Admin();
        a.setName(req.name().trim());
        a.setEmail(email);
        a.setPhone(phone);
        a.setLocation(req.location() == null || req.location().isBlank() ? null : req.location().trim());
        a.setStatus(AccountStatus.ACTIVE);
        a.setPasswordHash(encoder.encode(req.password()));
        a = admins.save(a);
        activity.log("ADMIN_CREATED", "Administrator account created for " + a.getName() + " (" + email + ")");
        return view(a);
    }

    /** Approve (ACTIVE), suspend, ban or reactivate a farmer/buyer account. */
    @Transactional
    public AdminUserView setStatus(Role role, Long id, AccountStatus status, String reason) {
        User u;
        if (role == Role.FARMER) {
            Farmer f = farmers.findById(id).orElseThrow(() -> ApiException.notFound("Farmer not found"));
            AccountStatus before = f.getStatus();
            f.setStatus(status);
            farmers.save(f);
            if (status != AccountStatus.ACTIVE) {
                // Hide the listings of suspended / banned farmers
                products.findByFarmerFarmerIdAndStatusInOrderByUpdatedAtDesc(f.getId(), List.of(ProductStatus.AVAILABLE))
                        .forEach(p -> { p.setStatus(ProductStatus.DRAFT); products.save(p); });
            }
            u = f;
            String action = before == AccountStatus.PENDING && status == AccountStatus.ACTIVE ? "FARMER_APPROVED" : "USER_STATUS_CHANGED";
            activity.log(action, "Farmer " + f.getName() + " (#" + id + "): " + before + " -> " + status
                    + (reason == null || reason.isBlank() ? "" : " – " + reason));
        } else if (role == Role.BUYER) {
            Buyer b = buyers.findById(id).orElseThrow(() -> ApiException.notFound("Buyer not found"));
            AccountStatus before = b.getStatus();
            if (status == AccountStatus.PENDING) throw ApiException.badRequest("Buyers do not require approval");
            b.setStatus(status);
            buyers.save(b);
            u = b;
            activity.log("USER_STATUS_CHANGED", "Buyer " + b.getName() + " (#" + id + "): " + before + " -> " + status
                    + (reason == null || reason.isBlank() ? "" : " – " + reason));
        } else {
            throw ApiException.badRequest("Administrator accounts cannot be changed here");
        }
        return view(u);
    }

    @Transactional(readOnly = true)
    public List<ProductView> listings(ProductStatus status) {
        Map<String, Double> prices = market.latestPrices();
        List<Product> list = status == null
                ? products.findByStatusesWithFarmer(List.of(ProductStatus.AVAILABLE, ProductStatus.DRAFT, ProductStatus.REMOVED))
                : products.findByStatusWithFarmer(status);
        return list.stream().limit(300).map(p -> mapper.product(p, prices)).collect(Collectors.toList());
    }

    /** "Removal of any unwanted product listings". */
    @Transactional
    public ProductView removeListing(Long id, String reason) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (p.getStatus() == ProductStatus.SOLD) throw ApiException.badRequest("Sold listings cannot be removed");
        p.setStatus(ProductStatus.REMOVED);
        products.save(p);
        activity.log("LISTING_REMOVED_BY_ADMIN", p.getCropType() + " #" + id + " by " + p.getFarmer().getName()
                + (reason == null || reason.isBlank() ? "" : " – " + reason));
        return mapper.product(p);
    }

    @Transactional(readOnly = true)
    public List<LogView> logs(String action) {
        List<SystemLog> list = action == null || action.isBlank() ? logs.findTop300ByOrderByCreatedAtDesc()
                : logs.findTop300ByActionOrderByCreatedAtDesc(action);
        return list.stream().map(l -> new LogView(l.getLogId(), l.getCreatedAt(), l.getActorRole(), l.getActorId(),
                l.getActorName(), l.getAction(), l.getDetails())).collect(Collectors.toList());
    }

    /** "Viewing system reports". */
    @Transactional(readOnly = true)
    public ReportView reports(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        List<CropSummary> byCrop = transactions.summaryByCrop(TransactionStatus.CANCELLED, from).stream()
                .map(r -> new CropSummary((String) r[0], ((Number) r[1]).longValue(),
                        r[2] == null ? 0 : Math.round(((Number) r[2]).doubleValue()),
                        r[3] == null ? 0 : Math.round(((Number) r[3]).doubleValue())))
                .sorted(Comparator.comparingDouble(CropSummary::value).reversed()).collect(Collectors.toList());
        List<MonthlySummary> monthly = transactions.monthlyVolume(TransactionStatus.CANCELLED, from).stream()
                .map(r -> new MonthlySummary(String.format("%04d-%02d", ((Number) r[0]).intValue(), ((Number) r[1]).intValue()),
                        ((Number) r[2]).longValue(), r[3] == null ? 0 : Math.round(((Number) r[3]).doubleValue())))
                .collect(Collectors.toList());
        Map<String, Long> byCounty = new TreeMap<>();
        farmers.findAll().forEach(f -> byCounty.merge(f.getLocation() == null ? "Unknown" : f.getLocation(), 1L, Long::sum));
        buyers.findAll().forEach(b -> byCounty.merge(b.getLocation() == null ? "Unknown" : b.getLocation(), 1L, Long::sum));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ProductStatus s : ProductStatus.values()) byStatus.put(s.name(), products.countByStatus(s));
        Map<String, Long> txByStatus = new LinkedHashMap<>();
        for (TransactionStatus s : TransactionStatus.values()) txByStatus.put(s.name(), transactions.countByStatus(s));
        Object metrics = mlAdmin.status().get("metrics");
        Map<String, Object> modelMetrics = new LinkedHashMap<>();
        if (metrics instanceof Map<?, ?> m) m.forEach((k, v) -> modelMetrics.put(String.valueOf(k), v));
        return new ReportView("Last " + days + " days", byCrop, monthly, byCounty, byStatus, txByStatus, modelMetrics);
    }

    /** CSV export of transactions, listings or users. */
    @Transactional
    public String exportCsv(String type, int days) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        switch (type) {
            case "transactions" -> {
                sb.append("transaction_id,date,crop_type,quantity,agreed_price,total,status,farmer,buyer\n");
                for (Transaction t : transactions.since(LocalDateTime.now().minusDays(days))) {
                    double q = t.getQuantity() == null ? 0 : t.getQuantity();
                    sb.append(t.getTransactionId()).append(',').append(t.getTransactionDate().format(fmt)).append(',')
                            .append(csv(t.getProduct().getCropType())).append(',').append(q).append(',')
                            .append(t.getAgreedPrice()).append(',').append(Math.round(q * t.getAgreedPrice() * 100) / 100.0)
                            .append(',').append(t.getStatus()).append(',').append(csv(t.getFarmer().getName())).append(',')
                            .append(csv(t.getBuyer().getName())).append('\n');
                }
            }
            case "listings" -> {
                sb.append("product_id,crop_type,quantity,unit,price_per_unit,recommended_price,location,status,farmer,created_at\n");
                for (ProductView p : listings(null)) {
                    sb.append(p.id()).append(',').append(csv(p.cropType())).append(',').append(p.quantity()).append(',')
                            .append(p.unit()).append(',').append(p.pricePerUnit() == null ? "" : p.pricePerUnit()).append(',')
                            .append(p.recommendedPrice() == null ? "" : p.recommendedPrice()).append(',')
                            .append(csv(p.location())).append(',').append(p.status()).append(',')
                            .append(csv(p.farmerName())).append(',').append(p.createdAt() == null ? "" : p.createdAt().format(fmt))
                            .append('\n');
                }
            }
            case "users" -> {
                sb.append("role,id,name,phone,email,location,status,created_at\n");
                for (AdminUserView u : users(null, null)) {
                    sb.append(u.role()).append(',').append(u.id()).append(',').append(csv(u.name())).append(',')
                            .append(u.phone() == null ? "" : u.phone()).append(',').append(u.email() == null ? "" : u.email())
                            .append(',').append(csv(u.location())).append(',').append(u.status()).append(',')
                            .append(u.createdAt() == null ? "" : u.createdAt().format(fmt)).append('\n');
                }
            }
            default -> throw ApiException.badRequest("Unknown report type: " + type);
        }
        activity.log("REPORT_EXPORTED", type + " (" + days + " days)");
        return sb.toString();
    }

    private static String csv(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
