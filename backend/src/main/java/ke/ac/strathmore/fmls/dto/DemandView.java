package ke.ac.strathmore.fmls.dto;

import java.util.List;

/** Demand forecast for a crop (time-series module). */
public record DemandView(
        String cropType,
        boolean sufficientData,
        String trend,
        Double changePct,
        Double recentWeeklyAvg,
        Double forecastWeeklyAvg,
        String forecastPeriod,
        String model,
        Integer basedOnWeeks,
        List<QuantityPoint> history,
        List<QuantityPoint> forecast,
        String message) {
}
