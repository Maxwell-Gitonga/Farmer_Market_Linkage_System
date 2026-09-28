package ke.ac.strathmore.fmls.dto;

import java.time.LocalDate;

/** A price at a date. */
public record PricePoint(
        LocalDate date,
        Double price) {
}
