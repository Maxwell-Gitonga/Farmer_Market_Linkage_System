package ke.ac.strathmore.fmls.module;

import ke.ac.strathmore.fmls.config.AppProperties;
import ke.ac.strathmore.fmls.domain.MarketPrice;
import ke.ac.strathmore.fmls.domain.Transaction;
import ke.ac.strathmore.fmls.dto.ImportResult;
import ke.ac.strathmore.fmls.dto.LatestPriceView;
import ke.ac.strathmore.fmls.dto.MarketPriceRequest;
import ke.ac.strathmore.fmls.dto.PricePoint;
import ke.ac.strathmore.fmls.repository.MarketPriceRepository;
import ke.ac.strathmore.fmls.service.ActivityLogService;
import ke.ac.strathmore.fmls.service.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * MarketDataModule (sequence diagram 1): stores and serves historical market prices
 * – fetchHistoricalPrices(cropType) and updateMarketData().
 *
 * <p>Because the study runs in a simulated Kenyan market (Proposal §1.6), updateMarketData() acts as a
 * simulated real-time market feed: it adds prices for every market day (Tuesday/Friday) since the last
 * record, continuing each crop/county series. Completed platform sales and administrator entries/CSV
 * imports are recorded as market data too.</p>
 */
@Service
public class MarketDataModule {

    public static final String SRC_SEED = "KAMIS_SIMULATED";
    public static final String SRC_FEED = "SIMULATED_FEED";
    public static final String SRC_ADMIN = "ADMIN_ENTRY";
    public static final String SRC_IMPORT = "CSV_IMPORT";
    public static final String SRC_SALE = "PLATFORM_SALE";

    private static final Logger log = LoggerFactory.getLogger(MarketDataModule.class);
    private static final Map<String, Double> VOLATILITY = Map.of("Tomatoes", 0.08, "Cabbages", 0.07,
            "Sukuma Wiki", 0.06, "Avocados", 0.06, "Irish Potatoes", 0.05, "Onions", 0.05);

    private final MarketPriceRepository repo;
    private final AppProperties props;
    private final ActivityLogService activity;

    private volatile LocalDate lastFeedCheck;
    private volatile Map<String, Double> latestCache;
    private volatile long latestCacheAt;

    public MarketDataModule(MarketPriceRepository repo, AppProperties props, ActivityLogService activity) {
        this.repo = repo;
        this.props = props;
        this.activity = activity;
    }

    /** fetchHistoricalPrices(cropType): calls updateMarketData() first, then returns the history. */
    @Transactional
    public List<MarketPrice> fetchHistoricalPrices(String cropType, int days) {
        updateMarketData();
        return repo.findByCropTypeIgnoreCaseAndRecordedDateGreaterThanEqualOrderByRecordedDateAsc(
                CropCatalog.canonical(cropType), LocalDate.now().minusDays(days));
    }

    /** Price history rows in the format expected by the ML module. */
    public List<Map<String, Object>> historyPayload(String cropType, int days) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MarketPrice m : fetchHistoricalPrices(cropType, days)) {
            Map<String, Object> r = new HashMap<>();
            r.put("recorded_date", m.getRecordedDate().toString());
            r.put("price", m.getPrice());
            r.put("market_location", m.getMarketLocation());
            rows.add(r);
        }
        return rows;
    }

    /** Scheduled daily at 06:00 so market data stays current even when nobody is using the system. */
    @Scheduled(cron = "0 0 6 * * *")
    public void scheduledFeed() {
        updateMarketData();
    }

    @Transactional
    public synchronized int updateMarketData() {
        if (!props.isSimulateMarketFeed()) return 0;
        LocalDate today = LocalDate.now();
        if (today.equals(lastFeedCheck)) return 0;
        LocalDate latest = repo.latestDate();
        lastFeedCheck = today;
        if (latest == null) return 0;
        List<LocalDate> days = new ArrayList<>();
        LocalDate start = latest.plusDays(1).isBefore(today.minusDays(120)) ? today.minusDays(120) : latest.plusDays(1);
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.TUESDAY || d.getDayOfWeek() == DayOfWeek.FRIDAY) days.add(d);
        }
        if (days.isEmpty()) return 0;

        Map<String, List<MarketPrice>> series = repo.findByRecordedDateGreaterThanEqual(latest.minusDays(60)).stream()
                .collect(Collectors.groupingBy(m -> m.getCropType() + "|" + m.getMarketLocation()));
        Random rnd = new Random(today.toEpochDay());
        List<MarketPrice> fresh = new ArrayList<>();
        for (Map.Entry<String, List<MarketPrice>> e : series.entrySet()) {
            List<MarketPrice> s = e.getValue();
            s.sort(Comparator.comparing(MarketPrice::getRecordedDate));
            double mean = s.stream().mapToDouble(MarketPrice::getPrice).average().orElse(0);
            double last = s.get(s.size() - 1).getPrice();
            String crop = s.get(0).getCropType();
            String county = s.get(0).getMarketLocation();
            double vol = VOLATILITY.getOrDefault(crop, 0.04);
            for (LocalDate d : days) {
                double p = last * (1 + 0.15 * (mean / last - 1)) * Math.exp(rnd.nextGaussian() * vol * 0.6);
                p = Math.round(Math.max(5, p) * 100.0) / 100.0;
                fresh.add(new MarketPrice(crop, county, p, d, SRC_FEED));
                last = p;
            }
        }
        repo.saveAll(fresh);
        latestCache = null;
        log.info("Market feed: added {} prices for {} market day(s)", fresh.size(), days.size());
        activity.log("MARKET_FEED", "Simulated market feed added " + fresh.size() + " prices for "
                + days.size() + " market day(s) up to " + days.get(days.size() - 1));
        return fresh.size();
    }

    /** Latest price per "Crop|County" plus a national average per "Crop|*" (cached for 5 minutes). */
    public Map<String, Double> latestPrices() {
        Map<String, Double> cache = latestCache;
        if (cache != null && System.currentTimeMillis() - latestCacheAt < 300_000) return cache;
        LocalDate latest = repo.latestDate();
        Map<String, Double> out = new HashMap<>();
        if (latest != null) {
            Map<String, MarketPrice> newest = new HashMap<>();
            for (MarketPrice m : repo.findByRecordedDateGreaterThanEqual(latest.minusDays(21))) {
                String k = m.getCropType() + "|" + m.getMarketLocation();
                MarketPrice cur = newest.get(k);
                if (cur == null || m.getRecordedDate().isAfter(cur.getRecordedDate())) newest.put(k, m);
            }
            Map<String, List<Double>> byCrop = new HashMap<>();
            newest.forEach((k, m) -> {
                out.put(k, m.getPrice());
                byCrop.computeIfAbsent(m.getCropType(), c -> new ArrayList<>()).add(m.getPrice());
            });
            byCrop.forEach((c, v) -> out.put(c + "|*",
                    Math.round(v.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100) / 100.0));
        }
        latestCache = out;
        latestCacheAt = System.currentTimeMillis();
        return out;
    }

    public Double latestPrice(String crop, String county) {
        Map<String, Double> m = latestPrices();
        Double p = m.get(CropCatalog.canonical(crop) + "|" + CropCatalog.canonicalCounty(county));
        return p != null ? p : m.get(CropCatalog.canonical(crop) + "|*");
    }

    /** Latest prices table with the change versus about a week earlier. */
    public List<LatestPriceView> latestPriceTable(String cropFilter) {
        updateMarketData();
        LocalDate latest = repo.latestDate();
        if (latest == null) return List.of();
        String crop = cropFilter == null || cropFilter.isBlank() ? null : CropCatalog.canonical(cropFilter);
        Map<String, List<MarketPrice>> series = repo.findByRecordedDateGreaterThanEqual(latest.minusDays(21)).stream()
                .filter(m -> crop == null || m.getCropType().equalsIgnoreCase(crop))
                .collect(Collectors.groupingBy(m -> m.getCropType() + "|" + m.getMarketLocation()));
        List<LatestPriceView> out = new ArrayList<>();
        for (List<MarketPrice> s : series.values()) {
            s.sort(Comparator.comparing(MarketPrice::getRecordedDate));
            MarketPrice last = s.get(s.size() - 1);
            MarketPrice weekAgo = s.stream().filter(m -> !m.getRecordedDate().isAfter(last.getRecordedDate().minusDays(7)))
                    .reduce((a, b) -> b).orElse(s.get(0));
            Double change = weekAgo == last ? null
                    : Math.round(1000.0 * (last.getPrice() / weekAgo.getPrice() - 1)) / 10.0;
            out.add(new LatestPriceView(last.getCropType(), last.getMarketLocation(), last.getPrice(),
                    last.getRecordedDate(), change));
        }
        out.sort(Comparator.comparing(LatestPriceView::cropType).thenComparing(LatestPriceView::market));
        return out;
    }

    /** Price history for charts: one county, or the national average when county is empty. */
    public List<PricePoint> history(String cropType, String county, int days) {
        List<MarketPrice> rows = fetchHistoricalPrices(cropType, days);
        String c = county == null || county.isBlank() ? null : CropCatalog.canonicalCounty(county);
        TreeMap<LocalDate, List<Double>> byDate = new TreeMap<>();
        for (MarketPrice m : rows) {
            if (c == null || m.getMarketLocation().equalsIgnoreCase(c)) {
                byDate.computeIfAbsent(m.getRecordedDate(), d -> new ArrayList<>()).add(m.getPrice());
            }
        }
        List<PricePoint> out = new ArrayList<>();
        byDate.forEach((d, v) -> out.add(new PricePoint(d,
                Math.round(v.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100) / 100.0)));
        return out;
    }

    /** A completed platform sale becomes a market observation (feeds future predictions). */
    @Transactional
    public void recordSale(Transaction t) {
        repo.save(new MarketPrice(t.getProduct().getCropType(), t.getProduct().getLocation(), t.getAgreedPrice(),
                LocalDate.now(), SRC_SALE));
        latestCache = null;
    }

    @Transactional
    public MarketPrice addPrice(MarketPriceRequest req) {
        MarketPrice m = repo.save(new MarketPrice(CropCatalog.canonical(req.cropType()),
                CropCatalog.canonicalCounty(req.marketLocation()), req.price(),
                req.recordedDate() == null ? LocalDate.now() : req.recordedDate(), SRC_ADMIN));
        latestCache = null;
        activity.log("MARKET_PRICE_ADDED", m.getCropType() + " @ " + m.getMarketLocation() + " = KES " + m.getPrice());
        return m;
    }

    /** CSV import: header crop_type,market_location,price,recorded_date (county/date also accepted). */
    @Transactional
    public ImportResult importCsv(InputStream in) {
        List<String> errors = new ArrayList<>();
        List<MarketPrice> rows = new ArrayList<>();
        int skipped = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = r.readLine();
            if (header == null) throw ApiException.badRequest("The CSV file is empty");
            String[] cols = header.replace("﻿", "").toLowerCase().split(",");
            Map<String, Integer> idx = new LinkedHashMap<>();
            for (int i = 0; i < cols.length; i++) idx.put(cols[i].trim(), i);
            Integer ic = idx.getOrDefault("crop_type", idx.get("crop"));
            Integer il = idx.getOrDefault("market_location", idx.get("county"));
            Integer ip = idx.get("price");
            Integer id = idx.getOrDefault("recorded_date", idx.get("date"));
            if (ic == null || il == null || ip == null) {
                throw ApiException.badRequest("CSV must have columns crop_type, market_location, price[, recorded_date]");
            }
            String line;
            int n = 1;
            while ((line = r.readLine()) != null) {
                n++;
                if (line.isBlank()) continue;
                String[] f = line.split(",");
                try {
                    LocalDate d = id != null && id < f.length && !f[id].isBlank() ? LocalDate.parse(f[id].trim()) : LocalDate.now();
                    double price = Double.parseDouble(f[ip].trim());
                    if (price <= 0 || price >= 10_000_000) throw new IllegalArgumentException("price must be between 0 and 10,000,000");
                    rows.add(new MarketPrice(CropCatalog.canonical(f[ic]), CropCatalog.canonicalCounty(f[il]), price, d, SRC_IMPORT));
                } catch (Exception ex) {
                    skipped++;
                    if (errors.size() < 20) errors.add("Line " + n + ": " + ex.getMessage());
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Could not read CSV: " + e.getMessage());
        }
        repo.saveAll(rows);
        latestCache = null;
        activity.log("MARKET_PRICES_IMPORTED", rows.size() + " prices imported, " + skipped + " skipped");
        return new ImportResult(rows.size(), skipped, errors);
    }

    /** All market prices in the ML training format. */
    public List<Map<String, Object>> trainingPayload() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : repo.trainingRows()) {
            Map<String, Object> m = new HashMap<>();
            m.put("crop_type", r[0]);
            m.put("market_location", r[1]);
            m.put("price", r[2]);
            m.put("recorded_date", r[3].toString());
            out.add(m);
        }
        return out;
    }

    public long count() { return repo.count(); }

    public LocalDate latestDate() { return repo.latestDate(); }
}
