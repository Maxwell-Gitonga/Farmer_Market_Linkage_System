package ke.ac.strathmore.fmls.dto;


/** Best-market recommendation row (predicted price net of transport). */
public record MarketOption(
        int rank,
        String market,
        Double predictedPrice,
        Double distanceKm,
        Double transportCostPerKg,
        Double netPricePerKg,
        String confidenceLabel,
        Double estimatedTotal) {
}
