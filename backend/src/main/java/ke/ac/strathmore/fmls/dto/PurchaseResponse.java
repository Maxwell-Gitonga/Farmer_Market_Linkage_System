package ke.ac.strathmore.fmls.dto;


/** Result of a purchase ("Purchase confirmed"). */
public record PurchaseResponse(
        Long transactionId,
        String message,
        TransactionView transaction) {
}
