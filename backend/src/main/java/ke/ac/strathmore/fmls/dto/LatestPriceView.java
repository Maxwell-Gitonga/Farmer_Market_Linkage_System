package ke.ac.strathmore.fmls.dto;

import java.time.LocalDate;

/** Latest market price of a crop in a county. */
public record LatestPriceView(
        String cropType,
        String market,
        Double price,
        LocalDate date,
        Double change7dPct) {
}
