package ke.ac.strathmore.fmls.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ke.ac.strathmore.fmls.domain.MarketPrice;
import ke.ac.strathmore.fmls.domain.PricePrediction;
import ke.ac.strathmore.fmls.domain.Product;
import ke.ac.strathmore.fmls.dto.DemandView;
import ke.ac.strathmore.fmls.dto.ForecastPoint;
import ke.ac.strathmore.fmls.dto.MarketOption;
import ke.ac.strathmore.fmls.dto.PriceForecastView;
import ke.ac.strathmore.fmls.dto.PricePoint;
import ke.ac.strathmore.fmls.dto.RecommendationView;
import ke.ac.strathmore.fmls.repository.PricePredictionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * PricePredictionModule (sequence diagram 1): generatePricePrediction(product).
 *
 * <p>Flow (activity diagram): fetch historical prices → ML regression prediction (extractFeatures happens in
 * the Python module) → [sufficient historical data?] → demand forecast → [demand rising?] → recommended price
 * + advice → farmer accepts the recommendation or sets a custom price.</p>
 */
@Service
public class PricePredictionModule {

    private static final Logger log = LoggerFactory.getLogger(PricePredictionModule.class);
    private static final int HISTORY_DAYS = 400;

    private final MlClient ml;
    private final MarketDataModule market;
    private final DemandForecastModule demand;
    private final PricePredictionRepository predictions;
    private final ObjectMapper mapper;

    public PricePredictionModule(MlClient ml, MarketDataModule market, DemandForecastModule demand,
                                 PricePredictionRepository predictions, ObjectMapper mapper) {
        this.ml = ml;
        this.market = market;
        this.demand = demand;
        this.predictions = predictions;
        this.mapper = mapper;
    }

    /** generatePricePrediction(product): compute, persist to price_predictions and return the recommendation. */
    @Transactional
    public RecommendationView generatePricePrediction(Product product) {
        RecommendationView v = compute(product.getCropType(), product.getLocation(), product.getHarvestDate(),
                product.getQuantity(), product.getProductId());
        PricePrediction p = new PricePrediction();
        p.setProduct(product);
        p.setPredictedPrice(v.predictedPrice());
        p.setRecommendedPrice(v.recommendedPrice());
        p.setConfidence(v.confidence());
        p.setConfidenceLabel(v.confidenceLabel());
        p.setLowerBound(v.lowerBound());
        p.setUpperBound(v.upperBound());
        p.setDemandTrend(v.demand() == null ? null : v.demand().trend());
        p.setBasedOnRecords(v.basedOnRecords());
        p.setModelVersion(v.modelVersion());
        try {
            p.setDetailsJson(mapper.writeValueAsString(v));
        } catch (Exception e) {
            log.warn("Could not serialise recommendation: {}", e.getMessage());
        }
        predictions.save(p);
        product.setRecommendedPrice(v.recommendedPrice());
        if (v.demand() != null) demand.persist(v.demand());
        return v;
    }

    /** The most recent stored recommendation for a listing. */
    public Optional<RecommendationView> latestFor(Long productId) {
        return predictions.findTopByProductProductIdOrderByCreatedAtDesc(productId).flatMap(p -> {
            try {
                return p.getDetailsJson() == null ? Optional.empty()
                        : Optional.of(mapper.readValue(p.getDetailsJson(), RecommendationView.class));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    /** Live preview for the listing form (not persisted). */
    public RecommendationView preview(String crop, String county, LocalDate harvest, Double quantity) {
        return compute(crop, county, harvest, quantity, null);
    }

    private RecommendationView compute(String cropType, String county, LocalDate harvest, Double quantity, Long productId) {
        String crop = CropCatalog.canonical(cropType);
        String loc = CropCatalog.canonicalCounty(county);
        LocalDate h = harvest == null ? LocalDate.now() : harvest;
        List<Map<String, Object>> history = market.historyPayload(crop, HISTORY_DAYS);
        List<Map<String, Object>> txns = demand.historyPayload(crop);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("crop_type", crop);
            body.put("county", loc);
            body.put("harvest_date", h.toString());
            body.put("quantity", quantity);
            body.put("reference_date", LocalDate.now().toString());
            body.put("price_history", history);
            body.put("transactions", txns);
            return fromJson(ml.post("/recommend/price", body), productId, crop, loc, h, quantity);
        } catch (MlUnavailableException e) {
            return fallback(productId, crop, loc, h, quantity, history, txns);
        }
    }

    private RecommendationView fromJson(JsonNode r, Long productId, String crop, String county, LocalDate harvest,
                                        Double quantity) {
        JsonNode pp = r.path("price_prediction");
        JsonNode rec = r.path("recommendation");
        DemandView d = DemandForecastModule.fromJson(r.path("demand_forecast"), crop);
        List<PricePoint> window = new ArrayList<>();
        r.path("sell_window").forEach(w -> window.add(new PricePoint(Json.date(w.path("date")), Json.dbl(w.path("price")))));
        List<PricePoint> trend = new ArrayList<>();
        r.path("price_trend").path("points").forEach(w -> trend.add(new PricePoint(Json.date(w.path("date")), Json.dbl(w.path("price")))));
        return new RecommendationView(productId, crop, county, harvest, quantity,
                pp.path("sufficient_data").asBoolean(false), Json.dbl(pp.path("predicted_price")),
                Json.dbl(rec.path("recommended_price")), Json.dbl(pp.path("lower_bound")), Json.dbl(pp.path("upper_bound")),
                Json.dbl(pp.path("confidence")), Json.str(pp.path("confidence_label")), Json.str(pp.path("basis")),
                Json.integer(pp.path("based_on_records")), Json.integer(pp.path("recent_records")),
                Json.str(pp.path("message")), Json.str(pp.path("model_version")), Json.str(pp.path("algorithm")), d,
                Json.str(rec.path("decision_path")), Json.dbl(rec.path("adjustment_pct")), Json.strings(rec.path("advice")),
                Json.date(rec.path("best_sell_date")), Json.dbl(rec.path("best_sell_price")), window, trend,
                Json.str(r.path("price_trend").path("basis")), false, LocalDateTime.now());
    }

    /** Offline fallback: moving average of recent market prices, clearly flagged as low confidence. */
    private RecommendationView fallback(Long productId, String crop, String county, LocalDate harvest, Double quantity,
                                        List<Map<String, Object>> history, List<Map<String, Object>> txns) {
        LocalDate cutoff = LocalDate.now().minusDays(60);
        List<Double> local = new ArrayList<>();
        List<Double> all = new ArrayList<>();
        List<PricePoint> trend = new ArrayList<>();
        for (Map<String, Object> r : history) {
            LocalDate d = LocalDate.parse((String) r.get("recorded_date"));
            double p = ((Number) r.get("price")).doubleValue();
            if (d.isAfter(cutoff)) {
                all.add(p);
                if (county.equalsIgnoreCase((String) r.get("market_location"))) {
                    local.add(p);
                    if (d.isAfter(LocalDate.now().minusDays(30))) trend.add(new PricePoint(d, p));
                }
            }
        }
        DemandView dv = demand.fallback(crop, txns);
        List<Double> base = local.size() >= 3 ? local : all;
        if (base.size() < 3) {
            return new RecommendationView(productId, crop, county, harvest, quantity, false, null, null, null, null, 0.0,
                    "low", "none", history.size(), local.size(),
                    "Limited data: not enough recent market data for " + crop + ". Please set your own price.",
                    "fallback-moving-average", "moving_average", dv, "insufficient_data", 0.0,
                    List.of("Limited data – set your own price using local market knowledge."), null, null, List.of(),
                    trend, "county", true, LocalDateTime.now());
        }
        List<Double> last = base.subList(Math.max(0, base.size() - 8), base.size());
        double avg = last.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double adj = "RISING".equals(dv.trend()) ? 0.04 : "FALLING".equals(dv.trend()) ? -0.03 : 0.0;
        double pred = Math.round(avg * 100) / 100.0;
        double rec = Math.round(avg * (1 + adj) * 100) / 100.0;
        List<String> advice = new ArrayList<>();
        advice.add("The decision-support service is offline – this is a simple moving average of recent "
                + (local.size() >= 3 ? county : "national") + " market prices.");
        if (adj > 0) advice.add("Recent sales suggest demand is rising – a slightly higher price is recommended.");
        if (adj < 0) advice.add("Recent sales suggest demand is falling – a slightly lower price will help you sell quickly.");
        return new RecommendationView(productId, crop, county, harvest, quantity, true, pred, rec,
                Math.round(pred * 0.9 * 100) / 100.0, Math.round(pred * 1.1 * 100) / 100.0, 0.3, "low",
                local.size() >= 3 ? "county" : "national", history.size(), local.size(),
                "Decision-support service offline – moving-average estimate.", "fallback-moving-average",
                "moving_average", dv, adj > 0 ? "demand_rising" : "demand_not_rising", adj * 100, advice, null, null,
                List.of(), trend, "county", true, LocalDateTime.now());
    }

    /** Market insights: last 60 days of prices and weekly price forecasts for the next `weeks` weeks. */
    public PriceForecastView forecast(String cropType, String county, int weeks) {
        String crop = CropCatalog.canonical(cropType);
        String loc = CropCatalog.canonicalCounty(county);
        List<PricePoint> hist = market.history(crop, loc, 60);
        if (hist.size() < 4) hist = market.history(crop, null, 60);
        List<Map<String, Object>> history = market.historyPayload(crop, HISTORY_DAYS);
        List<ForecastPoint> out = new ArrayList<>();
        String message = null;
        try {
            for (int w = 1; w <= weeks; w++) {
                LocalDate target = LocalDate.now().plusWeeks(w);
                Map<String, Object> body = new HashMap<>();
                body.put("crop_type", crop);
                body.put("county", loc);
                body.put("target_date", target.toString());
                body.put("reference_date", LocalDate.now().toString());
                body.put("history", history);
                JsonNode r = ml.post("/predict/price", body);
                if (!r.path("sufficient_data").asBoolean(false)) {
                    message = Json.str(r.path("message"));
                    break;
                }
                out.add(new ForecastPoint(target, Json.dbl(r.path("predicted_price")), Json.dbl(r.path("lower_bound")),
                        Json.dbl(r.path("upper_bound")), Json.str(r.path("confidence_label"))));
            }
        } catch (MlUnavailableException e) {
            message = "The decision-support service is offline – price forecasts are unavailable right now.";
        }
        return new PriceForecastView(crop, loc, hist, out, message);
    }

    /** "Recommending the best market" – county markets ranked by predicted price net of transport. */
    public List<MarketOption> bestMarkets(String cropType, String farmerCounty, Double quantity, LocalDate target) {
        String crop = CropCatalog.canonical(cropType);
        Map<String, Object> body = new HashMap<>();
        body.put("crop_type", crop);
        body.put("farmer_county", CropCatalog.canonicalCounty(farmerCounty));
        body.put("quantity", quantity);
        body.put("target_date", (target == null ? LocalDate.now() : target).toString());
        body.put("reference_date", LocalDate.now().toString());
        body.put("history", market.historyPayload(crop, HISTORY_DAYS));
        try {
            JsonNode r = ml.post("/recommend/markets", body);
            List<MarketOption> out = new ArrayList<>();
            r.path("markets").forEach(m -> out.add(new MarketOption(m.path("rank").asInt(), Json.str(m.path("market")),
                    Json.dbl(m.path("predicted_price")), Json.dbl(m.path("distance_km")),
                    Json.dbl(m.path("transport_cost_per_kg")), Json.dbl(m.path("net_price_per_kg")),
                    Json.str(m.path("confidence_label")), Json.dbl(m.path("estimated_total")))));
            return out;
        } catch (MlUnavailableException e) {
            // Fallback: latest observed prices, no transport adjustment
            List<MarketOption> out = new ArrayList<>();
            Map<String, Double> latest = market.latestPrices();
            List<Map.Entry<String, Double>> rows = latest.entrySet().stream()
                    .filter(x -> x.getKey().startsWith(crop + "|") && !x.getKey().endsWith("|*"))
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).collect(Collectors.toList());
            int rank = 1;
            for (Map.Entry<String, Double> x : rows) {
                out.add(new MarketOption(rank++, x.getKey().substring(crop.length() + 1), x.getValue(), null, null,
                        x.getValue(), "low", quantity == null ? null : x.getValue() * quantity));
            }
            return out;
        }
    }

    /** Recent market prices exist for the crop? (used by the listing form hint) */
    public boolean hasData(String crop) {
        List<MarketPrice> rows = market.fetchHistoricalPrices(crop, 60);
        return rows.size() >= 6;
    }
}
