package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ke.ac.strathmore.fmls.domain.AccountStatus;

/** Approve / suspend / ban / reactivate an account. */
public record UserStatusRequest(
        @NotNull AccountStatus status,
        @Size(max = 300) String reason) {
}
