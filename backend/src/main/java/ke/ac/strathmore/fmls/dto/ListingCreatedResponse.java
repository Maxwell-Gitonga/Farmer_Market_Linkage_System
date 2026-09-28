package ke.ac.strathmore.fmls.dto;


/** Result of addListing(): the new product ID plus its price recommendation. */
public record ListingCreatedResponse(
        ProductView product,
        RecommendationView recommendation) {
}
