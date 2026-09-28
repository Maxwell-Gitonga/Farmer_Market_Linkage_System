package ke.ac.strathmore.fmls.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Historical market price of a crop at a county market ("Market history informs the prediction"). */
@Entity
@Table(name = "market_prices", indexes = {
        @Index(name = "idx_market_crop_loc_date", columnList = "crop_type, market_location, recorded_date"),
        @Index(name = "idx_market_date", columnList = "recorded_date")})
public class MarketPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_id")
    private Long priceId;

    @Column(name = "crop_type", nullable = false, length = 50)
    private String cropType;

    @Column(name = "market_location", length = 100)
    private String marketLocation;

    @Column(nullable = false, columnDefinition = "DECIMAL(10,2)")
    private Double price;

    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    /** KAMIS_SIMULATED (seed data), SIMULATED_FEED (daily feed), ADMIN_ENTRY, CSV_IMPORT or PLATFORM_SALE. */
    @Column(length = 50)
    private String source;

    public MarketPrice() { }

    public MarketPrice(String cropType, String marketLocation, Double price, LocalDate recordedDate, String source) {
        this.cropType = cropType;
        this.marketLocation = marketLocation;
        this.price = price;
        this.recordedDate = recordedDate;
        this.source = source;
    }

    public Long getPriceId() { return priceId; }
    public void setPriceId(Long priceId) { this.priceId = priceId; }
    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }
    public String getMarketLocation() { return marketLocation; }
    public void setMarketLocation(String marketLocation) { this.marketLocation = marketLocation; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public LocalDate getRecordedDate() { return recordedDate; }
    public void setRecordedDate(LocalDate recordedDate) { this.recordedDate = recordedDate; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
