package ke.ac.strathmore.fmls.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Decision support for a listing: predicted price, confidence, demand, recommended price and advice. */
public record RecommendationView(
        Long productId,
        String cropType,
        String county,
        LocalDate harvestDate,
        Double quantity,
        boolean sufficientData,
        Double predictedPrice,
        Double recommendedPrice,
        Double lowerBound,
        Double upperBound,
        Double confidence,
        String confidenceLabel,
        String basis,
        Integer basedOnRecords,
        Integer recentRecords,
        String message,
        String modelVersion,
        String algorithm,
        DemandView demand,
        String decisionPath,
        Double adjustmentPct,
        List<String> advice,
        LocalDate bestSellDate,
        Double bestSellPrice,
        List<PricePoint> sellWindow,
        List<PricePoint> priceTrend,
        String priceTrendBasis,
        boolean fallback,
        LocalDateTime generatedAt) {
}
