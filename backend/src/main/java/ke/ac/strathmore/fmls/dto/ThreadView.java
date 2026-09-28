package ke.ac.strathmore.fmls.dto;

import java.time.LocalDateTime;

/** A conversation summary. */
public record ThreadView(
        Long counterpartId,
        String counterpartName,
        String counterpartLocation,
        String counterpartPhone,
        String lastMessage,
        LocalDateTime lastAt,
        long unread) {
}
