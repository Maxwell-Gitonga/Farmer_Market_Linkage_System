package ke.ac.strathmore.fmls.domain;

/** Account lifecycle – farmers start PENDING until an administrator approves them (§3.8.1). */
public enum AccountStatus {
    PENDING, ACTIVE, SUSPENDED, BANNED
}
