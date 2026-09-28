package ke.ac.strathmore.fmls.dto;

import java.util.List;

/** Listing detail with its 30-day local price trend. */
public record ProductDetailView(
        ProductView product,
        List<PricePoint> priceTrend,
        String trendBasis) {
}
