package ke.ac.strathmore.fmls.dto;

import java.time.LocalDateTime;
import ke.ac.strathmore.fmls.domain.Role;

/** System activity log entry. */
public record LogView(
        Long id,
        LocalDateTime createdAt,
        Role actorRole,
        Long actorId,
        String actorName,
        String action,
        String details) {
}
