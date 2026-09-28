package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** purchaseProduct(productID). */
public record PurchaseRequest(
        @NotNull Long productId,
        @NotNull @Positive Double quantity) {
}
