package ke.ac.strathmore.fmls.module;

import ke.ac.strathmore.fmls.config.AppProperties;
import ke.ac.strathmore.fmls.service.ActivityLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Simulated sales feed – the sales equivalent of the simulated market-price feed.
 *
 * <p>The demo sales history is a fixed file, so after the database is seeded the weeks that follow have
 * almost no sales. Those empty weeks made the demand charts drop to zero and wrecked the demand-model
 * accuracy figures. While {@code app.simulate-market-feed} is on, this job fills any recent week whose
 * sales for a crop are far below the same week last year by replaying last year's sales for that week
 * (52 weeks later, same weekday, quantity varied by up to ±15%). Real sales are never touched; replayed
 * products are marked with the description {@code SIM_REPLAY:<source transaction id>} so the job never
 * replays the same sale twice and the rows can be found (or deleted) easily.</p>
 */
@Service
public class SalesFeedModule {

    public static final String MARKER = "SIM_REPLAY:";
    private static final int LOOKBACK_DAYS = 126;      // 18 weeks: what the demand charts and back-test look at
    private static final int YEAR_DAYS = 364;          // 52 weeks, so the weekday stays the same
    private static final double GAP_SHARE = 0.30;      // a week below 30% of last year's sales counts as a gap

    private static final Logger log = LoggerFactory.getLogger(SalesFeedModule.class);

    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private final ActivityLogService activity;

    public SalesFeedModule(JdbcTemplate jdbc, AppProperties props, ActivityLogService activity) {
        this.jdbc = jdbc;
        this.props = props;
        this.activity = activity;
    }

    /** Runs after the seed data is loaded and before the optional start-up retraining. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onReady() {
        fillGaps();
    }

    @Scheduled(cron = "0 10 6 * * *")
    public void daily() {
        fillGaps();
    }

    private record Sale(long id, long farmerId, long buyerId, String crop, double price, double qty,
                        LocalDateTime when, String location, String description) {}

    /** Fill sales gaps up to yesterday. Returns the number of sales added. */
    @Transactional
    public synchronized int fillGaps() {
        if (!props.isSimulateMarketFeed()) return 0;
        try {
            return doFill();
        } catch (RuntimeException e) {
            log.warn("Simulated sales feed skipped: {}", e.getMessage());
            return 0;
        }
    }

    private int doFill() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(LOOKBACK_DAYS).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sourceFrom = from.minusDays(YEAR_DAYS);

        List<Sale> sales = jdbc.query(
                "SELECT t.transaction_id, t.farmer_id, t.buyer_id, p.crop_type, t.agreed_price, t.quantity, "
                        + "t.transaction_date, p.location, p.description FROM transactions t "
                        + "JOIN products p ON p.product_id = t.product_id "
                        + "WHERE t.status <> 'CANCELLED' AND t.transaction_date >= ? AND t.transaction_date < ?",
                (rs, i) -> new Sale(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                        rs.getDouble(5), rs.getDouble(6), rs.getTimestamp(7).toLocalDateTime(),
                        rs.getString(8), rs.getString(9)),
                Timestamp.valueOf(sourceFrom.atStartOfDay()), Timestamp.valueOf(today.atStartOfDay()));
        if (sales.isEmpty()) return 0;

        Set<Long> alreadyReplayed = new HashSet<>();
        for (Sale s : sales) {
            if (s.description() != null && s.description().startsWith(MARKER)) {
                try { alreadyReplayed.add(Long.parseLong(s.description().substring(MARKER.length()))); }
                catch (NumberFormatException ignored) { }
            }
        }

        // kg per crop per week: this period, and the same weeks one year earlier
        Map<String, Double> actual = new HashMap<>();
        Map<String, List<Sale>> source = new HashMap<>();
        for (Sale s : sales) {
            LocalDate d = s.when().toLocalDate();
            if (!d.isBefore(from)) {
                actual.merge(key(s.crop(), monday(d)), s.qty(), Double::sum);
            } else {
                LocalDate target = d.plusDays(YEAR_DAYS);
                if (target.isBefore(today)) {
                    source.computeIfAbsent(key(s.crop(), monday(target)), k -> new ArrayList<>()).add(s);
                }
            }
        }

        Random rnd = new Random(today.toEpochDay());
        int added = 0;
        for (Map.Entry<String, List<Sale>> e : source.entrySet()) {
            double sourceKg = e.getValue().stream().mapToDouble(Sale::qty).sum();
            double actualKg = actual.getOrDefault(e.getKey(), 0.0);
            if (sourceKg <= 0 || actualKg >= GAP_SHARE * sourceKg) continue;   // week already has normal sales
            for (Sale s : e.getValue()) {
                if (alreadyReplayed.contains(s.id())) continue;
                replay(s, rnd);
                added++;
            }
        }
        if (added > 0) {
            log.info("Simulated sales feed added {} sales to fill gaps since {}", added, from);
            activity.log("SALES_FEED", "Simulated sales feed added " + added + " sales (replay of last year's weeks)");
        }
        return added;
    }

    private void replay(Sale s, Random rnd) {
        LocalDateTime sold = s.when().plusDays(YEAR_DAYS);
        double qty = Math.max(1, Math.round(s.qty() * (0.85 + 0.30 * rnd.nextDouble())));
        LocalDateTime listed = sold.toLocalDate().minusDays(1 + rnd.nextInt(3)).atTime(8, 0);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO products (farmer_id, crop_type, quantity, unit, "
                    + "harvest_date, status, price_per_unit, location, description, created_at, updated_at) "
                    + "VALUES (?, ?, 0, 'kg', ?, 'SOLD', ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, s.farmerId());
            ps.setString(2, s.crop());
            ps.setDate(3, Date.valueOf(listed.toLocalDate()));
            ps.setDouble(4, s.price());
            ps.setString(5, s.location());
            ps.setString(6, MARKER + s.id());
            ps.setTimestamp(7, Timestamp.valueOf(listed));
            ps.setTimestamp(8, Timestamp.valueOf(sold));
            return ps;
        }, keys);
        long productId = keys.getKey().longValue();
        jdbc.update("INSERT INTO transactions (product_id, farmer_id, buyer_id, agreed_price, quantity, status, "
                        + "transaction_date, updated_at) VALUES (?, ?, ?, ?, ?, 'COMPLETED', ?, ?)",
                productId, s.farmerId(), s.buyerId(), s.price(), qty, Timestamp.valueOf(sold), Timestamp.valueOf(sold));
    }

    private static LocalDate monday(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static String key(String crop, LocalDate week) {
        return crop + "|" + week;
    }
}
