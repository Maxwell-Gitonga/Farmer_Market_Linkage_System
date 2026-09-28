package ke.ac.strathmore.fmls.dto;

import java.util.List;

/** A buyer matched to a listing. */
public record MatchView(
        Long buyerId,
        String buyerName,
        String buyerLocation,
        String buyerPhone,
        Double score,
        List<String> reasons,
        Long productId,
        String cropType) {
}
