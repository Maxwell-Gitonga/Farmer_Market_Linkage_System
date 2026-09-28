package ke.ac.strathmore.fmls.dto;

import java.time.LocalDate;

/** A forecast price with its prediction interval. */
public record ForecastPoint(
        LocalDate date,
        Double price,
        Double lower,
        Double upper,
        String confidenceLabel) {
}
