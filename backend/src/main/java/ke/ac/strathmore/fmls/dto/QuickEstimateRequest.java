package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** Live preview on the listing form ("System-generated (preview)"). */
public record QuickEstimateRequest(
        @NotBlank String cropType,
        @NotBlank String location,
        LocalDate harvestDate,
        Double quantity) {
}
