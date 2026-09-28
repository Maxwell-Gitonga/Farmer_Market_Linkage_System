package ke.ac.strathmore.fmls.dto;

import jakarta.validation.constraints.NotNull;
import ke.ac.strathmore.fmls.domain.TransactionStatus;

/** Change a transaction status (complete / cancel). */
public record StatusChangeRequest(
        @NotNull TransactionStatus status) {
}
