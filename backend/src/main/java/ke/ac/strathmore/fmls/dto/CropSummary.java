package ke.ac.strathmore.fmls.dto;


/** Transactions per crop (reports). */
public record CropSummary(
        String cropType,
        long transactions,
        double quantity,
        double value) {
}
