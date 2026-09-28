package ke.ac.strathmore.fmls.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Output of the time-series demand forecaster for a crop. */
@Entity
@Table(name = "demand_forecasts")
public class DemandForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "forecast_id")
    private Long forecastId;

    @Column(name = "crop_type", nullable = false, length = 50)
    private String cropType;

    @Column(name = "forecast_period", length = 60)
    private String forecastPeriod;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ForecastTrend trend;

    /** Average forecast weekly demand (kg) over the forecast period. */
    @Column(name = "predicted_quantity", columnDefinition = "DECIMAL(12,2)")
    private Double predictedQuantity;

    @Column(name = "change_pct", columnDefinition = "DECIMAL(10,2)")
    private Double changePct;

    @Column(length = 40)
    private String model;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) generatedAt = LocalDateTime.now();
    }

    public Long getForecastId() { return forecastId; }
    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }
    public String getForecastPeriod() { return forecastPeriod; }
    public void setForecastPeriod(String forecastPeriod) { this.forecastPeriod = forecastPeriod; }
    public ForecastTrend getTrend() { return trend; }
    public void setTrend(ForecastTrend trend) { this.trend = trend; }
    public Double getPredictedQuantity() { return predictedQuantity; }
    public void setPredictedQuantity(Double predictedQuantity) { this.predictedQuantity = predictedQuantity; }
    public Double getChangePct() { return changePct; }
    public void setChangePct(Double changePct) { this.changePct = changePct; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
