package ke.ac.strathmore.fmls.dto;

import java.time.LocalDateTime;
import ke.ac.strathmore.fmls.domain.Role;

/** A message in a conversation. */
public record MessageView(
        Long id,
        Role senderRole,
        String senderName,
        String content,
        LocalDateTime createdAt,
        Long productId,
        String productLabel,
        boolean mine) {
}
