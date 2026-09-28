package ke.ac.strathmore.fmls.module;

import com.fasterxml.jackson.databind.JsonNode;
import ke.ac.strathmore.fmls.domain.DemandForecast;
import ke.ac.strathmore.fmls.domain.ForecastTrend;
import ke.ac.strathmore.fmls.domain.TransactionStatus;
import ke.ac.strathmore.fmls.dto.DemandView;
import ke.ac.strathmore.fmls.dto.QuantityPoint;
import ke.ac.strathmore.fmls.repository.DemandForecastRepository;
import ke.ac.strathmore.fmls.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Demand analysis: sends the historical demand (platform transactions) of a crop to the ML module's
 * time-series forecaster ("Get demand forecast" → "Analyse historical demand data") and stores the result.
 */
@Service
public class DemandForecastModule {

    private final MlClient ml;
    private final TransactionRepository transactions;
    private final DemandForecastRepository forecasts;

    public DemandForecastModule(MlClient ml, TransactionRepository transactions, DemandForecastRepository forecasts) {
        this.ml = ml;
        this.transactions = transactions;
        this.forecasts = forecasts;
    }

    /** Historical demand rows (transaction date + quantity) for the ML module. */
    public List<Map<String, Object>> historyPayload(String crop) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : transactions.demandHistory(CropCatalog.canonical(crop), TransactionStatus.CANCELLED)) {
            Map<String, Object> m = new HashMap<>();
            m.put("transaction_date", ((LocalDateTime) r[0]).toLocalDate().toString());
            m.put("quantity", r[1]);
            rows.add(m);
        }
        return rows;
    }

    @Transactional
    public DemandView forecastDemand(String cropType) {
        String crop = CropCatalog.canonical(cropType);
        List<Map<String, Object>> history = historyPayload(crop);
        DemandView view;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("crop_type", crop);
            body.put("horizon_weeks", 4);
            body.put("reference_date", LocalDate.now().toString());
            body.put("transactions", history);
            view = fromJson(ml.post("/forecast/demand", body), crop);
        } catch (MlUnavailableException e) {
            view = fallback(crop, history);
        }
        persist(view);
        return view;
    }

    static DemandView fromJson(JsonNode d, String crop) {
        return new DemandView(crop, d.path("sufficient_data").asBoolean(false), Json.str(d.path("trend")),
                Json.dbl(d.path("change_pct")), Json.dbl(d.path("recent_weekly_avg")),
                Json.dbl(d.path("forecast_weekly_avg")), Json.str(d.path("forecast_period")),
                Json.str(d.path("model")), Json.integer(d.path("based_on_weeks")),
                points(d.path("history")), points(d.path("forecast")), Json.str(d.path("message")));
    }

    private static List<QuantityPoint> points(JsonNode arr) {
        List<QuantityPoint> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(p -> out.add(new QuantityPoint(Json.date(p.path("week_start")), Json.dbl(p.path("quantity")))));
        }
        return out;
    }

    /** Offline fallback: compare the last 4 complete weeks with the 4 before them. */
    DemandView fallback(String crop, List<Map<String, Object>> history) {
        LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        TreeMap<LocalDate, Double> weeks = new TreeMap<>();
        for (int i = 1; i <= 8; i++) weeks.put(thisMonday.minusWeeks(i), 0.0);
        for (Map<String, Object> r : history) {
            LocalDate d = LocalDate.parse((String) r.get("transaction_date"));
            LocalDate w = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (weeks.containsKey(w)) weeks.merge(w, ((Number) r.get("quantity")).doubleValue(), Double::sum);
        }
        List<Double> v = new ArrayList<>(weeks.values());
        double prev = v.subList(0, 4).stream().mapToDouble(Double::doubleValue).sum() / 4;
        double recent = v.subList(4, 8).stream().mapToDouble(Double::doubleValue).sum() / 4;
        if (recent == 0 && prev == 0) {
            return new DemandView(crop, false, "STABLE", 0.0, 0.0, null, null, "none", 0, List.of(), List.of(),
                    "Limited demand history for " + crop + ".");
        }
        double change = prev > 0 ? (recent - prev) / prev : 0;
        String trend = change > 0.05 ? "RISING" : change < -0.05 ? "FALLING" : "STABLE";
        List<QuantityPoint> hist = new ArrayList<>();
        weeks.forEach((w, q) -> hist.add(new QuantityPoint(w, q)));
        return new DemandView(crop, true, trend, Math.round(change * 1000) / 10.0, recent, recent, null,
                "recent_weeks_comparison", 8, hist, List.of(),
                "Decision-support service offline – trend estimated from the last 8 weeks of sales.");
    }

    /** Store the forecast (one row per crop per day, updated in place). */
    @Transactional
    public void persist(DemandView v) {
        if (!v.sufficientData() || v.trend() == null) return;
        DemandForecast f = forecasts.findTopByCropTypeIgnoreCaseOrderByGeneratedAtDesc(v.cropType())
                .filter(x -> x.getGeneratedAt().toLocalDate().equals(LocalDate.now()))
                .orElseGet(DemandForecast::new);
        f.setCropType(v.cropType());
        f.setForecastPeriod(v.forecastPeriod());
        f.setTrend(ForecastTrend.valueOf(v.trend()));
        f.setPredictedQuantity(v.forecastWeeklyAvg());
        f.setChangePct(v.changePct() == null ? null : Math.max(-999.0, Math.min(999.0, v.changePct())));
        f.setModel(v.model());
        f.setGeneratedAt(LocalDateTime.now());
        forecasts.save(f);
    }

    /** All transactions in the ML training format. */
    public List<Map<String, Object>> trainingPayload() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : transactions.trainingRows(TransactionStatus.CANCELLED)) {
            Map<String, Object> m = new HashMap<>();
            m.put("crop_type", r[0]);
            m.put("transaction_date", ((LocalDateTime) r[1]).toLocalDate().toString());
            m.put("quantity", r[2]);
            out.add(m);
        }
        return out;
    }
}
