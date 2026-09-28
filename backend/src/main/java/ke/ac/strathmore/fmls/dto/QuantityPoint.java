package ke.ac.strathmore.fmls.dto;

import java.time.LocalDate;

/** Weekly demand (kg). */
public record QuantityPoint(
        LocalDate weekStart,
        Double quantity) {
}
