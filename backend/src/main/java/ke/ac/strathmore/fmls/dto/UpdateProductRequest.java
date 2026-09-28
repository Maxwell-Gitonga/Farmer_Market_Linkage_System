package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Modify listing information (Product Listing and Management Module). */
public record UpdateProductRequest(
        @Positive Double quantity,
        LocalDate harvestDate,
        @Size(max = 100) String location,
        @Size(max = 500) String description,
        @Positive Double pricePerUnit) {
}
