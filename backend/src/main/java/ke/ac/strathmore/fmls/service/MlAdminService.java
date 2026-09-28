package ke.ac.strathmore.fmls.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ke.ac.strathmore.fmls.module.DemandForecastModule;
import ke.ac.strathmore.fmls.module.MarketDataModule;
import ke.ac.strathmore.fmls.module.MlClient;
import ke.ac.strathmore.fmls.module.MlUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ke.ac.strathmore.fmls.config.AppProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Model lifecycle: retrains the ML models on the data stored in MySQL (market prices + transactions), so
 * predictions improve as the platform is used (Proposal §3.2.3 – models trained on historical market data).
 */
@Service
public class MlAdminService {

    private static final Logger log = LoggerFactory.getLogger(MlAdminService.class);

    private final MlClient ml;
    private final MarketDataModule market;
    private final DemandForecastModule demand;
    private final ActivityLogService activity;
    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Map<String, Object> lastResult = new LinkedHashMap<>();

    public MlAdminService(MlClient ml, MarketDataModule market, DemandForecastModule demand, ActivityLogService activity,
                          AppProperties props) {
        this.props = props;
        this.ml = ml;
        this.market = market;
        this.demand = demand;
        this.activity = activity;
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            JsonNode health = ml.get("/health");
            out.put("status", health.path("status").asText("UNKNOWN"));
            out.put("health", mapper.convertValue(health, Map.class));
            out.put("metrics", mapper.convertValue(ml.get("/metrics"), Map.class));
        } catch (MlUnavailableException e) {
            out.put("status", "DOWN");
            out.put("message", "The Python ML module is not reachable. Start it with: uvicorn app.main:app --port 8000");
        }
        out.put("retraining", running.get());
        out.put("lastRetrain", lastResult);
        return out;
    }

    /** Retrain in the background (admin button, weekly schedule, optional startup). */
    @Async
    public void retrainAsync(String trigger) {
        retrain(trigger);
    }

    /** Optional retraining once the application (and the seed data) is fully started. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (props.isTrainOnStartup()) retrain("startup");
    }

    @Scheduled(cron = "0 0 2 * * SUN")
    public void weeklyRetrain() {
        retrain("weekly schedule");
    }

    public Map<String, Object> retrain(String trigger) {
        if (!running.compareAndSet(false, true)) {
            return Map.of("status", "ALREADY_RUNNING");
        }
        long t0 = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("market_prices", market.trainingPayload());
            body.put("transactions", demand.trainingPayload());
            JsonNode r = ml.postLong("/train", body);
            JsonNode pm = r.path("price_model");
            JsonNode dm = r.path("demand_model");
            result.put("status", "COMPLETED");
            result.put("trigger", trigger);
            result.put("modelVersion", pm.path("version").asText());
            result.put("priceModelMape", pm.path("comparison").path(pm.path("selected_model").asText()).path("mape").asDouble());
            result.put("demandModelMape", dm.path("mean_mape").asDouble());
            result.put("seconds", (System.currentTimeMillis() - t0) / 1000);
            activity.log("ML_RETRAINED", "Models retrained (" + trigger + "): " + result.get("modelVersion")
                    + ", price MAPE " + result.get("priceModelMape") + ", demand MAPE " + result.get("demandModelMape"));
        } catch (MlUnavailableException e) {
            result.put("status", "FAILED");
            result.put("trigger", trigger);
            result.put("message", e.getMessage());
            log.warn("Retraining failed: {}", e.getMessage());
            activity.log("ML_RETRAIN_FAILED", trigger + ": " + e.getMessage());
        } finally {
            running.set(false);
        }
        result.put("finishedAt", java.time.LocalDateTime.now().toString());
        lastResult = result;
        return result;
    }
}
