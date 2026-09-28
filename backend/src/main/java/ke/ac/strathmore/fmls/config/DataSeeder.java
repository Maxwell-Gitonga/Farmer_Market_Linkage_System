package ke.ac.strathmore.fmls.config;

import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Admin;
import ke.ac.strathmore.fmls.domain.Buyer;
import ke.ac.strathmore.fmls.domain.Farmer;
import ke.ac.strathmore.fmls.domain.Message;
import ke.ac.strathmore.fmls.domain.Product;
import ke.ac.strathmore.fmls.domain.ProductStatus;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.domain.Transaction;
import ke.ac.strathmore.fmls.domain.TransactionStatus;
import ke.ac.strathmore.fmls.module.CropCatalog;
import ke.ac.strathmore.fmls.module.MarketDataModule;
import ke.ac.strathmore.fmls.repository.AdminRepository;
import ke.ac.strathmore.fmls.repository.BuyerRepository;
import ke.ac.strathmore.fmls.repository.FarmerRepository;
import ke.ac.strathmore.fmls.repository.MessageRepository;
import ke.ac.strathmore.fmls.repository.ProductRepository;
import ke.ac.strathmore.fmls.repository.TransactionRepository;
import ke.ac.strathmore.fmls.service.ActivityLogService;
import ke.ac.strathmore.fmls.service.MlAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Loads the simulated Kenyan market dataset and demo accounts on first start-up (empty database).
 *
 * <p>Dates in the dataset are shifted by whole weeks so that the most recent market data falls in the
 * current week – the decision-support features therefore work no matter when the system is run.</p>
 *
 * <p>Demo accounts (password in brackets):
 * admin@fmls.co.ke (Admin@123) · farmer 0711000001 (Farmer@123) · buyer 0722000001 (Buyer@123) ·
 * pending farmer 0711000099 (Farmer@123).</p>
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties props;
    private final JdbcTemplate jdbc;
    private final FarmerRepository farmers;
    private final BuyerRepository buyers;
    private final AdminRepository admins;
    private final ProductRepository products;
    private final TransactionRepository transactions;
    private final MessageRepository messages;
    private final BCryptPasswordEncoder encoder;
    private final MarketDataModule market;
    private final ActivityLogService activity;
    private final MlAdminService mlAdmin;

    public DataSeeder(AppProperties props, JdbcTemplate jdbc, FarmerRepository farmers, BuyerRepository buyers,
                      AdminRepository admins, ProductRepository products, TransactionRepository transactions,
                      MessageRepository messages, BCryptPasswordEncoder encoder, MarketDataModule market,
                      ActivityLogService activity, MlAdminService mlAdmin) {
        this.props = props;
        this.jdbc = jdbc;
        this.farmers = farmers;
        this.buyers = buyers;
        this.admins = admins;
        this.products = products;
        this.transactions = transactions;
        this.messages = messages;
        this.encoder = encoder;
        this.market = market;
        this.activity = activity;
        this.mlAdmin = mlAdmin;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!props.isSeedEnabled()) return;
        if (admins.count() > 0 || farmers.count() > 0) {
            log.info("Database already initialised – skipping seed data");
            return;
        }
        long t0 = System.currentTimeMillis();
        log.info("Seeding database with the simulated Kenyan market dataset…");

        // ---- users
        Admin admin = new Admin();
        admin.setName("System Administrator");
        admin.setEmail("admin@fmls.co.ke");
        admin.setPhone("0700000000");
        admin.setLocation("Nairobi");
        admin.setStatus(AccountStatus.ACTIVE);
        admin.setPasswordHash(encoder.encode("Admin@123"));
        admins.save(admin);

        String farmerHash = encoder.encode("Farmer@123");
        String buyerHash = encoder.encode("Buyer@123");
        Map<Integer, Farmer> farmerByRef = new HashMap<>();
        for (String[] r : readCsv("seed/seed_farmers.csv")) {
            Farmer f = new Farmer();
            f.setName(r[1]);
            f.setPhone(r[2]);
            f.setEmail(r[3]);
            f.setLocation(r[4]);
            f.setStatus(AccountStatus.ACTIVE);
            f.setPasswordHash(farmerHash);
            f.setCreatedAt(LocalDateTime.now().minusYears(3));
            farmerByRef.put(Integer.parseInt(r[0]), farmers.save(f));
        }
        Farmer pending = new Farmer();
        pending.setName("Grace Wanjiku (awaiting approval)");
        pending.setPhone("0711000099");
        pending.setEmail("pending.farmer@fmls.demo");
        pending.setLocation("Kiambu");
        pending.setStatus(AccountStatus.PENDING);
        pending.setPasswordHash(farmerHash);
        farmers.save(pending);

        Map<Integer, Buyer> buyerByRef = new HashMap<>();
        for (String[] r : readCsv("seed/seed_buyers.csv")) {
            Buyer b = new Buyer();
            b.setName(r[1]);
            b.setPhone(r[2]);
            b.setEmail(r[3]);
            b.setLocation(r[4]);
            b.setPreferences(r[5]);
            b.setStatus(AccountStatus.ACTIVE);
            b.setPasswordHash(buyerHash);
            b.setCreatedAt(LocalDateTime.now().minusYears(3));
            buyerByRef.put(Integer.parseInt(r[0]), buyers.save(b));
        }
        farmers.flush();
        buyers.flush();

        // ---- market prices (shifted to the current week)
        List<String[]> prices = readCsv("seed/market_prices.csv");
        LocalDate end = prices.stream().map(r -> LocalDate.parse(r[3])).max(LocalDate::compareTo).orElse(LocalDate.now());
        long shiftDays = Math.floorDiv(ChronoUnit.DAYS.between(end, LocalDate.now()), 7) * 7;
        List<Object[]> priceRows = new ArrayList<>(prices.size());
        for (String[] r : prices) {
            priceRows.add(new Object[]{r[0], r[1], Double.parseDouble(r[2]),
                    Date.valueOf(LocalDate.parse(r[3]).plusDays(shiftDays)), r[4]});
        }
        jdbc.batchUpdate("INSERT INTO market_prices (crop_type, market_location, price, recorded_date, source) "
                + "VALUES (?, ?, ?, ?, ?)", priceRows);

        // ---- historical sales: one SOLD product + one COMPLETED transaction each (demand history)
        List<String[]> txns = readCsv("seed/historical_transactions.csv");
        List<Object[]> productRows = new ArrayList<>(txns.size());
        List<Object[]> txnRows = new ArrayList<>(txns.size());
        Random rnd = new Random(7);
        long id = 1;
        for (String[] r : txns) {
            Farmer f = farmerByRef.get(Integer.parseInt(r[0]));
            Buyer b = buyerByRef.get(Integer.parseInt(r[1]));
            LocalDate d = LocalDate.parse(r[6]).plusDays(shiftDays);
            double qty = Double.parseDouble(r[4]);
            double price = Double.parseDouble(r[5]);
            LocalDateTime listed = d.minusDays(1 + rnd.nextInt(4)).atTime(8, 0);
            LocalDateTime sold = d.atTime(9 + rnd.nextInt(8), rnd.nextInt(60));
            productRows.add(new Object[]{id, f.getId(), r[2], 0.0, "kg", Date.valueOf(listed.toLocalDate()), "SOLD",
                    price, r[3], Timestamp.valueOf(listed), Timestamp.valueOf(sold)});
            txnRows.add(new Object[]{id, f.getId(), b.getId(), price, qty, "COMPLETED", Timestamp.valueOf(sold),
                    Timestamp.valueOf(sold)});
            id++;
        }
        jdbc.batchUpdate("INSERT INTO products (product_id, farmer_id, crop_type, quantity, unit, harvest_date, status, "
                + "price_per_unit, location, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", productRows);
        jdbc.batchUpdate("INSERT INTO transactions (product_id, farmer_id, buyer_id, agreed_price, quantity, status, "
                + "transaction_date, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", txnRows);
        // (MySQL moves AUTO_INCREMENT past the explicit product ids automatically)

        // ---- current listings for the demo
        Farmer demoFarmer = farmerByRef.get(1);
        Buyer demoBuyer = buyerByRef.get(1);
        Product maize = listing(demoFarmer, "Maize", 1500, 5, "Dry, well-shelled maize – 90kg bags available");
        Product potatoes = listing(demoFarmer, "Irish Potatoes", 800, 2, "Shangi variety, sorted");
        List<Farmer> all = new ArrayList<>(farmerByRef.values());
        for (CropCatalog.Crop crop : CropCatalog.crops()) {
            int made = 0;
            for (Farmer f : all) {
                if (made >= 2) break;
                if (f == demoFarmer || !crop.markets().contains(f.getLocation())) continue;
                if (rnd.nextDouble() < 0.35) {
                    listing(f, crop.name(), 100 + 50 * rnd.nextInt(30), rnd.nextInt(12) - 2, null);
                    made++;
                }
            }
        }

        // A recent confirmed order awaiting delivery + a conversation (demo)
        Transaction order = new Transaction();
        order.setProduct(potatoes);
        order.setFarmer(demoFarmer);
        order.setBuyer(demoBuyer);
        order.setQuantity(300.0);
        order.setAgreedPrice(potatoes.getPricePerUnit());
        order.setStatus(TransactionStatus.CONFIRMED);
        order.setTransactionDate(LocalDateTime.now().minusDays(1));
        transactions.save(order);
        potatoes.setQuantity(500.0);
        products.save(potatoes);
        message(demoFarmer, demoBuyer, maize, Role.BUYER, "Hello, is the maize still available? I need about 800kg for next week.", 26);
        message(demoFarmer, demoBuyer, maize, Role.FARMER, "Yes it is. I can have it ready by Friday. Delivery to Nairobi can be arranged.", 25);
        message(demoFarmer, demoBuyer, maize, Role.BUYER, "Great, I will confirm the order after checking my stock.", 3);

        activity.log(admin, "SEED", "Database initialised: " + priceRows.size() + " market prices, " + txnRows.size()
                + " historical transactions, " + farmerByRef.size() + " farmers, " + buyerByRef.size() + " buyers");
        log.info("Seed data loaded in {} ms ({} prices, {} transactions, dates shifted by {} days)",
                System.currentTimeMillis() - t0, priceRows.size(), txnRows.size(), shiftDays);
    }

    private Product listing(Farmer f, String crop, double qty, int harvestOffsetDays, String description) {
        Double mp = market.latestPrice(crop, f.getLocation());
        double factor = 0.94 + (Math.abs((f.getName() + crop).hashCode()) % 100) / 1000.0;
        Product p = new Product();
        p.setFarmer(f);
        p.setCropType(crop);
        p.setQuantity(qty);
        p.setUnit("kg");
        p.setHarvestDate(LocalDate.now().plusDays(harvestOffsetDays));
        p.setLocation(f.getLocation());
        p.setDescription(description);
        p.setPricePerUnit(mp == null ? null : Math.round(mp * factor * 100) / 100.0);
        p.setStatus(mp == null ? ProductStatus.DRAFT : ProductStatus.AVAILABLE);
        return products.save(p);
    }

    private void message(Farmer f, Buyer b, Product p, Role sender, String text, int hoursAgo) {
        Message m = new Message();
        m.setFarmer(f);
        m.setBuyer(b);
        m.setProduct(p);
        m.setSenderRole(sender);
        m.setContent(text);
        m.setRead(hoursAgo > 20);
        m.setCreatedAt(LocalDateTime.now().minusHours(hoursAgo));
        messages.save(m);
    }

    /** Minimal CSV reader (handles quoted fields containing commas); skips the header row. */
    static List<String[]> readCsv(String resource) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> fields = new ArrayList<>();
                StringBuilder cur = new StringBuilder();
                boolean quoted = false;
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '"') {
                        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            cur.append('"');
                            i++;
                        } else {
                            quoted = !quoted;
                        }
                    } else if (c == ',' && !quoted) {
                        fields.add(cur.toString());
                        cur.setLength(0);
                    } else {
                        cur.append(c);
                    }
                }
                fields.add(cur.toString());
                rows.add(fields.toArray(new String[0]));
            }
        }
        return rows;
    }
}
