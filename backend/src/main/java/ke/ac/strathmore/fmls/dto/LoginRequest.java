package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.NotBlank;
import ke.ac.strathmore.fmls.domain.Role;

/** Single sign-in: login(phone, password). The role hint is optional – the system determines the user type. */
public record LoginRequest(
        @NotBlank String phone,
        @NotBlank String password,
        Role role) {
}
