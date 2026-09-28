package ke.ac.strathmore.fmls.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import ke.ac.strathmore.fmls.domain.ProductStatus;

/** A listing as shown to farmers, buyers and admins. */
public record ProductView(
        Long id,
        String cropType,
        String emoji,
        Double quantity,
        String unit,
        LocalDate harvestDate,
        String location,
        String description,
        ProductStatus status,
        Double pricePerUnit,
        Double recommendedPrice,
        Long farmerId,
        String farmerName,
        String farmerPhone,
        String farmerLocation,
        Double marketPrice,
        Double priceVsMarketPct,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
