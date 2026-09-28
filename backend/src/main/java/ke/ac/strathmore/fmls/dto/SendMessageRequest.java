package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Send a message to a farmer (buyer) or buyer (farmer). */
public record SendMessageRequest(
        @NotNull Long counterpartId,
        Long productId,
        @NotBlank @Size(max = 1000) String content) {
}
