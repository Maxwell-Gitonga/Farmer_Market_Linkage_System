package ke.ac.strathmore.fmls.dto;

import java.util.List;
import java.util.Map;

/** System reports. */
public record ReportView(
        String period,
        List<CropSummary> byCrop,
        List<MonthlySummary> monthly,
        Map<String, Long> usersByCounty,
        Map<String, Long> listingsByStatus,
        Map<String, Long> transactionsByStatus,
        Map<String, Object> modelMetrics) {
}
