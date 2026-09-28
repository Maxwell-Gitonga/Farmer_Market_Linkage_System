package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.Size;

/** Removal of an unwanted listing by an administrator. */
public record RemoveListingRequest(
        @Size(max = 300) String reason) {
}
