package ke.ac.strathmore.fmls.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Application settings bound from application.properties (prefix "app"). */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String jwtSecret = "change-me-fmls-strathmore-2026-very-long-secret-key";
    private int jwtExpiryHours = 12;
    private String mlServiceUrl = "http://localhost:8000";
    private int mlTimeoutSeconds = 20;
    private int mlTrainTimeoutSeconds = 600;
    private boolean seedEnabled = true;
    private boolean autoApproveFarmers = false;
    private boolean simulateMarketFeed = true;
    private boolean trainOnStartup = false;
    private List<String> corsOrigins = new ArrayList<>(List.of("http://localhost:4200"));

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public int getJwtExpiryHours() { return jwtExpiryHours; }
    public void setJwtExpiryHours(int jwtExpiryHours) { this.jwtExpiryHours = jwtExpiryHours; }
    public String getMlServiceUrl() { return mlServiceUrl; }
    public void setMlServiceUrl(String mlServiceUrl) { this.mlServiceUrl = mlServiceUrl; }
    public int getMlTimeoutSeconds() { return mlTimeoutSeconds; }
    public void setMlTimeoutSeconds(int mlTimeoutSeconds) { this.mlTimeoutSeconds = mlTimeoutSeconds; }
    public int getMlTrainTimeoutSeconds() { return mlTrainTimeoutSeconds; }
    public void setMlTrainTimeoutSeconds(int mlTrainTimeoutSeconds) { this.mlTrainTimeoutSeconds = mlTrainTimeoutSeconds; }
    public boolean isSeedEnabled() { return seedEnabled; }
    public void setSeedEnabled(boolean seedEnabled) { this.seedEnabled = seedEnabled; }
    public boolean isAutoApproveFarmers() { return autoApproveFarmers; }
    public void setAutoApproveFarmers(boolean autoApproveFarmers) { this.autoApproveFarmers = autoApproveFarmers; }
    public boolean isSimulateMarketFeed() { return simulateMarketFeed; }
    public void setSimulateMarketFeed(boolean simulateMarketFeed) { this.simulateMarketFeed = simulateMarketFeed; }
    public boolean isTrainOnStartup() { return trainOnStartup; }
    public void setTrainOnStartup(boolean trainOnStartup) { this.trainOnStartup = trainOnStartup; }
    public List<String> getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(List<String> corsOrigins) { this.corsOrigins = corsOrigins; }
}
