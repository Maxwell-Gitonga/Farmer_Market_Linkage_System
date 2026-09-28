package ke.ac.strathmore.fmls.dto;

import java.util.List;

/** Market insights: recent prices and the forecast for the coming weeks. */
public record PriceForecastView(
        String cropType,
        String county,
        List<PricePoint> history,
        List<ForecastPoint> forecast,
        String message) {
}
