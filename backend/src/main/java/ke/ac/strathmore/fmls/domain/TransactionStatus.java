package ke.ac.strathmore.fmls.domain;

/** Purchase lifecycle. Payment processing is out of scope (§1.6). */
public enum TransactionStatus {
    PENDING, CONFIRMED, COMPLETED, CANCELLED
}
