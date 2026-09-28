package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Profile update. */
public record UpdateProfileRequest(
        @Size(max = 100) String name,
        @Email @Size(max = 120) String email,
        @Size(max = 100) String location,
        @Size(max = 255) String preferences) {
}
