package ke.ac.strathmore.fmls.dto;

import java.util.List;

/** Published listing with the buyers matched to it. */
public record PublishResponse(
        ProductView product,
        List<MatchView> matchedBuyers) {
}
