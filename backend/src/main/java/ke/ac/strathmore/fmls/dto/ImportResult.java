package ke.ac.strathmore.fmls.dto;

import java.util.List;

/** Result of a market-price CSV import. */
public record ImportResult(
        int imported,
        int skipped,
        List<String> errors) {
}
