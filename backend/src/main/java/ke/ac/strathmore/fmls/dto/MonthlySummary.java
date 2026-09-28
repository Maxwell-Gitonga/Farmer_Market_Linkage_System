package ke.ac.strathmore.fmls.dto;


/** Transactions per month (reports). */
public record MonthlySummary(
        String month,
        long transactions,
        double value) {
}
