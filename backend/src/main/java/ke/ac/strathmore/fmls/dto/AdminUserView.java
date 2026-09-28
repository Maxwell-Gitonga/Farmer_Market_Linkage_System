package ke.ac.strathmore.fmls.dto;

import java.time.LocalDateTime;
import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Role;

/** User row in the admin user-management screen. */
public record AdminUserView(
        Long id,
        Role role,
        String name,
        String phone,
        String email,
        String location,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        String preferences) {
}
