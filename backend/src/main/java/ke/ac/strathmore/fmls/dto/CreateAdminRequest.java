package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** An administrator creating another administrator account (Users page, "Add administrator"). */
public record CreateAdminRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 120) String email,
        @Pattern(regexp = "^$|^[+]?[0-9 -]{9,16}$", message = "must be a valid Kenyan phone number e.g. 0712345678") String phone,
        @NotBlank @Size(min = 6, max = 72, message = "must be at least 6 characters") String password,
        @Size(max = 100) String location) {
}
