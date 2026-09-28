package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ke.ac.strathmore.fmls.domain.Role;

/** Registration of a farmer or buyer (one sign-up form, role chosen by the user). */
public record RegisterRequest(
        @NotNull Role role,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^[+]?[0-9 -]{9,16}$", message = "must be a valid Kenyan phone number e.g. 0712345678") String phone,
        @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 6, max = 72, message = "must be at least 6 characters") String password,
        @NotBlank @Size(max = 100) String location,
        @Size(max = 255) String preferences) {
}
