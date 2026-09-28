package ke.ac.strathmore.fmls.dto;

import java.time.LocalDate;
import java.util.Map;

/** Admin dashboard counters. */
public record AdminOverview(
        long farmers,
        long pendingFarmers,
        long buyers,
        long activeListings,
        long soldListings,
        long removedListings,
        long transactions30d,
        double volume30dKes,
        long predictions30d,
        long logs24h,
        long marketPriceRecords,
        LocalDate latestMarketDate,
        Map<String, Object> ml) {
}
