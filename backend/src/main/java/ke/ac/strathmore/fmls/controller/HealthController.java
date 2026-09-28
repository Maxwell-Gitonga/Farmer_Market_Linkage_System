package ke.ac.strathmore.fmls.controller;

import ke.ac.strathmore.fmls.module.MarketDataModule;
import ke.ac.strathmore.fmls.module.MlClient;
import ke.ac.strathmore.fmls.security.PublicEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Integration health check: backend, database and ML module. */
@RestController
public class HealthController {

    private final MarketDataModule market;
    private final MlClient ml;

    public HealthController(MarketDataModule market, MlClient ml) {
        this.market = market;
        this.ml = ml;
    }

    @PublicEndpoint
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("backend", "UP");
        try {
            out.put("database", "UP");
            out.put("marketPriceRecords", market.count());
            out.put("latestMarketDate", String.valueOf(market.latestDate()));
        } catch (Exception e) {
            out.put("database", "DOWN: " + e.getMessage());
        }
        out.put("mlModule", ml.isUp() ? "UP" : "DOWN");
        return out;
    }
}
