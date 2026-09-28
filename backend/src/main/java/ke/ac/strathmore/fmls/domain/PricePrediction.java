package ke.ac.strathmore.fmls.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Output of the price prediction model for a listing ("Product receives a price prediction"). */
@Entity
@Table(name = "price_predictions")
public class PricePrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prediction_id")
    private Long predictionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Null when there was insufficient historical data (sequence diagram alt path). */
    @Column(name = "predicted_price", columnDefinition = "DECIMAL(10,2)")
    private Double predictedPrice;

    @Column(name = "recommended_price", columnDefinition = "DECIMAL(10,2)")
    private Double recommendedPrice;

    @Column(columnDefinition = "DECIMAL(4,3)")
    private Double confidence;

    @Column(name = "confidence_label", length = 10)
    private String confidenceLabel;

    @Column(name = "lower_bound", columnDefinition = "DECIMAL(10,2)")
    private Double lowerBound;

    @Column(name = "upper_bound", columnDefinition = "DECIMAL(10,2)")
    private Double upperBound;

    @Column(name = "demand_trend", length = 10)
    private String demandTrend;

    @Column(name = "based_on_records")
    private Integer basedOnRecords;

    @Column(name = "model_version", length = 80)
    private String modelVersion;

    /** Full decision-support view (advice, 30-day trend, sell window) shown on the detail screen. */
    @Lob
    @Column(name = "details_json")
    private String detailsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getPredictionId() { return predictionId; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Double getPredictedPrice() { return predictedPrice; }
    public void setPredictedPrice(Double predictedPrice) { this.predictedPrice = predictedPrice; }
    public Double getRecommendedPrice() { return recommendedPrice; }
    public void setRecommendedPrice(Double recommendedPrice) { this.recommendedPrice = recommendedPrice; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getConfidenceLabel() { return confidenceLabel; }
    public void setConfidenceLabel(String confidenceLabel) { this.confidenceLabel = confidenceLabel; }
    public Double getLowerBound() { return lowerBound; }
    public void setLowerBound(Double lowerBound) { this.lowerBound = lowerBound; }
    public Double getUpperBound() { return upperBound; }
    public void setUpperBound(Double upperBound) { this.upperBound = upperBound; }
    public String getDemandTrend() { return demandTrend; }
    public void setDemandTrend(String demandTrend) { this.demandTrend = demandTrend; }
    public Integer getBasedOnRecords() { return basedOnRecords; }
    public void setBasedOnRecords(Integer basedOnRecords) { this.basedOnRecords = basedOnRecords; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
