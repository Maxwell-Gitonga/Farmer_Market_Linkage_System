package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Manual market price entry by an administrator. */
public record MarketPriceRequest(
        @NotBlank @Size(max = 50) String cropType,
        @NotBlank @Size(max = 100) String marketLocation,
        @NotNull @Positive Double price,
        LocalDate recordedDate) {
}
