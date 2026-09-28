package ke.ac.strathmore.fmls.dto;

import java.time.LocalDateTime;
import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Role;

/** Profile of the signed-in user. */
public record UserView(
        Long id,
        Role role,
        String name,
        String phone,
        String email,
        String location,
        String preferences,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt) {
}
