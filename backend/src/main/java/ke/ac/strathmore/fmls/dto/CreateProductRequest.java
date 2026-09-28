package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Farmer listing form (crop type, quantity, harvest date, location). */
public record CreateProductRequest(
        @NotBlank @Size(max = 50) String cropType,
        @NotNull @Positive Double quantity,
        @Size(max = 20) String unit,
        @NotNull LocalDate harvestDate,
        @NotBlank @Size(max = 100) String location,
        @Size(max = 500) String description) {
}
