package ke.ac.strathmore.fmls.dto;

import java.time.LocalDateTime;
import ke.ac.strathmore.fmls.domain.TransactionStatus;

/** A purchase as seen by farmer, buyer or admin. */
public record TransactionView(
        Long id,
        Long productId,
        String cropType,
        Double quantity,
        String unit,
        Double agreedPrice,
        Double total,
        TransactionStatus status,
        LocalDateTime transactionDate,
        Long farmerId,
        String farmerName,
        String farmerPhone,
        Long buyerId,
        String buyerName,
        String buyerPhone) {
}
