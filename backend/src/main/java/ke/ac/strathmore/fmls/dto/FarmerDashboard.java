package ke.ac.strathmore.fmls.dto;

import java.util.List;
import ke.ac.strathmore.fmls.domain.AccountStatus;

/** Farmer dashboard (wireframe: recommended price, demand trend, active listings, listings, matched buyers). */
public record FarmerDashboard(
        AccountStatus accountStatus,
        String recommendedPriceCrop,
        Double recommendedPrice,
        String recommendedConfidence,
        String demandCrop,
        String demandTrend,
        Double demandChangePct,
        long activeListings,
        long draftListings,
        List<ProductView> listings,
        List<MatchView> matchedBuyers,
        List<TransactionView> recentSales,
        double salesLast30Days,
        long pendingOrders) {
}
