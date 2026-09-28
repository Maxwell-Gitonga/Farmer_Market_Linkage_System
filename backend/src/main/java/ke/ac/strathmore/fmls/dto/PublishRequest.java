package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** "List at this price" (acceptedRecommendation = true) or "Set own price". */
public record PublishRequest(
        @NotNull @Positive Double price,
        boolean acceptedRecommendation) {
}
