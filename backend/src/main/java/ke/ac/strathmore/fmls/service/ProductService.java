package ke.ac.strathmore.fmls.service;

import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Buyer;
import ke.ac.strathmore.fmls.domain.Farmer;
import ke.ac.strathmore.fmls.domain.Product;
import ke.ac.strathmore.fmls.domain.ProductStatus;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.dto.CreateProductRequest;
import ke.ac.strathmore.fmls.dto.ListingCreatedResponse;
import ke.ac.strathmore.fmls.dto.MatchView;
import ke.ac.strathmore.fmls.dto.PricePoint;
import ke.ac.strathmore.fmls.dto.ProductDetailView;
import ke.ac.strathmore.fmls.dto.ProductView;
import ke.ac.strathmore.fmls.dto.PublishRequest;
import ke.ac.strathmore.fmls.dto.PublishResponse;
import ke.ac.strathmore.fmls.dto.RecommendationView;
import ke.ac.strathmore.fmls.dto.RecommendedProductView;
import ke.ac.strathmore.fmls.dto.UpdateProductRequest;
import ke.ac.strathmore.fmls.module.CropCatalog;
import ke.ac.strathmore.fmls.module.MarketDataModule;
import ke.ac.strathmore.fmls.module.PricePredictionModule;
import ke.ac.strathmore.fmls.module.RecommendationModule;
import ke.ac.strathmore.fmls.repository.ProductRepository;
import ke.ac.strathmore.fmls.security.AuthContext;
import ke.ac.strathmore.fmls.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Product Listing and Management Module (§3.8.2) and Market Access and Interaction Module (§3.8.3).
 */
@Service
public class ProductService {

    private final ProductRepository products;
    private final UserDirectory users;
    private final PricePredictionModule pricing;
    private final RecommendationModule recommender;
    private final MarketDataModule market;
    private final ViewMapper mapper;
    private final ActivityLogService activity;

    public ProductService(ProductRepository products, UserDirectory users, PricePredictionModule pricing,
                          RecommendationModule recommender, MarketDataModule market, ViewMapper mapper,
                          ActivityLogService activity) {
        this.products = products;
        this.users = users;
        this.pricing = pricing;
        this.recommender = recommender;
        this.market = market;
        this.mapper = mapper;
        this.activity = activity;
    }

    /** addListing() → returns productID, then generatePricePrediction(product) (sequence diagram 1). */
    @Transactional
    public ListingCreatedResponse addListing(CreateProductRequest req) {
        Farmer farmer = users.currentFarmer();
        if (req.harvestDate().isBefore(LocalDate.now().minusDays(30))) {
            throw ApiException.badRequest("Harvest date is too far in the past");
        }
        Product p = new Product();
        p.setFarmer(farmer);
        p.setCropType(CropCatalog.canonical(req.cropType()));
        p.setQuantity(req.quantity());
        p.setUnit(req.unit() == null || req.unit().isBlank() ? "kg" : req.unit().trim());
        p.setHarvestDate(req.harvestDate());
        p.setLocation(CropCatalog.canonicalCounty(req.location()));
        p.setDescription(req.description());
        p.setStatus(ProductStatus.DRAFT);
        p = products.save(p);
        RecommendationView rec = pricing.generatePricePrediction(p);
        products.save(p);
        activity.log("LISTING_CREATED", p.getCropType() + " " + p.getQuantity() + p.getUnit() + " (product #"
                + p.getProductId() + ") – recommended " + (rec.recommendedPrice() == null ? "n/a" : "KES " + rec.recommendedPrice()));
        return new ListingCreatedResponse(mapper.product(p), rec);
    }

    /** "List at this price" / "Set own price" → listing becomes AVAILABLE and buyers are matched. */
    @Transactional
    public PublishResponse publish(Long id, PublishRequest req) {
        Product p = owned(id);
        if (p.getStatus() == ProductStatus.SOLD || p.getStatus() == ProductStatus.REMOVED) {
            throw ApiException.badRequest("This listing can no longer be published");
        }
        if (p.getFarmer().getStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("Your farmer account is awaiting administrator approval. Your listing is "
                    + "saved as a draft and can be published once you are approved.");
        }
        p.setPricePerUnit(Math.round(req.price() * 100) / 100.0);
        p.setStatus(ProductStatus.AVAILABLE);
        products.save(p);
        List<MatchView> matched = recommender.matchedBuyersForProduct(p);
        activity.log("LISTING_PUBLISHED", p.getCropType() + " #" + p.getProductId() + " at KES " + p.getPricePerUnit()
                + (req.acceptedRecommendation() ? " (accepted recommendation)" : " (custom price)")
                + " – " + matched.size() + " buyers matched");
        return new PublishResponse(mapper.product(p), matched);
    }

    @Transactional
    public ProductView update(Long id, UpdateProductRequest req) {
        Product p = owned(id);
        if (p.getStatus() == ProductStatus.SOLD || p.getStatus() == ProductStatus.REMOVED) {
            throw ApiException.badRequest("Sold or removed listings cannot be edited");
        }
        if (req.quantity() != null) p.setQuantity(req.quantity());
        if (req.harvestDate() != null) p.setHarvestDate(req.harvestDate());
        if (req.location() != null && !req.location().isBlank()) p.setLocation(CropCatalog.canonicalCounty(req.location()));
        if (req.description() != null) p.setDescription(req.description());
        if (req.pricePerUnit() != null) p.setPricePerUnit(Math.round(req.pricePerUnit() * 100) / 100.0);
        products.save(p);
        activity.log("LISTING_UPDATED", p.getCropType() + " #" + p.getProductId());
        return mapper.product(p);
    }

    /** Delete a listing (soft delete – status REMOVED keeps transaction history intact). */
    @Transactional
    public void delete(Long id) {
        Product p = owned(id);
        if (p.getStatus() == ProductStatus.SOLD) throw ApiException.badRequest("Sold listings cannot be deleted");
        p.setStatus(ProductStatus.REMOVED);
        products.save(p);
        activity.log("LISTING_DELETED", p.getCropType() + " #" + p.getProductId());
    }

    /** Regenerate or return the stored recommendation for one of the farmer's listings. */
    @Transactional
    public RecommendationView recommendation(Long id, boolean refresh) {
        Product p = owned(id);
        if (!refresh) {
            var stored = pricing.latestFor(p.getProductId());
            if (stored.isPresent()) return stored.get();
        }
        RecommendationView v = pricing.generatePricePrediction(p);
        products.save(p);
        return v;
    }

    @Transactional
    public List<MatchView> matches(Long id) {
        Product p = owned(id);
        if (p.getStatus() != ProductStatus.AVAILABLE) return List.of();
        return recommender.storedMatches(p);
    }

    public List<ProductView> myListings() {
        Farmer f = users.currentFarmer();
        Map<String, Double> prices = market.latestPrices();
        List<Product> open = products.findByFarmerFarmerIdAndStatusInOrderByUpdatedAtDesc(f.getId(),
                List.of(ProductStatus.DRAFT, ProductStatus.AVAILABLE));
        List<Product> sold = products.findTop20ByFarmerFarmerIdAndStatusOrderByUpdatedAtDesc(f.getId(), ProductStatus.SOLD);
        return java.util.stream.Stream.concat(open.stream(), sold.stream())
                .map(p -> mapper.product(p, prices)).collect(Collectors.toList());
    }

    /** getAvailableListings(cropType) with optional county / max price filters and sorting. */
    @Transactional(readOnly = true)
    public List<ProductView> getAvailableListings(String crop, String county, Double maxPrice, String sort) {
        String c = crop == null || crop.isBlank() ? null : crop.trim();
        String k = county == null || county.isBlank() ? null : CropCatalog.canonicalCounty(county);
        Map<String, Double> prices = market.latestPrices();
        List<ProductView> out = products.search(ProductStatus.AVAILABLE, AccountStatus.ACTIVE, c, k, maxPrice).stream()
                .map(p -> mapper.product(p, prices)).collect(Collectors.toList());
        Comparator<ProductView> cmp = switch (sort == null ? "newest" : sort) {
            case "price_asc" -> Comparator.comparing(ProductView::pricePerUnit, Comparator.nullsLast(Comparator.naturalOrder()));
            case "price_desc" -> Comparator.comparing(ProductView::pricePerUnit, Comparator.nullsLast(Comparator.reverseOrder()));
            case "quantity" -> Comparator.comparing(ProductView::quantity, Comparator.nullsLast(Comparator.reverseOrder()));
            case "harvest" -> Comparator.comparing(ProductView::harvestDate, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(ProductView::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
        out.sort(cmp);
        return out;
    }

    /** "View recommended products" – listings ranked for the signed-in buyer. */
    @Transactional(readOnly = true)
    public List<RecommendedProductView> recommendedForBuyer(int limit) {
        Buyer buyer = users.currentBuyer();
        List<Product> listings = products.search(ProductStatus.AVAILABLE, AccountStatus.ACTIVE, null, null, null);
        if (listings.size() > 300) listings = listings.subList(0, 300);
        Map<Long, Product> byId = listings.stream().collect(Collectors.toMap(Product::getProductId, Function.identity()));
        Map<String, Double> prices = market.latestPrices();
        return recommender.matchBuyerToProducts(buyer, listings, limit).stream()
                .filter(r -> byId.containsKey(r.productId()))
                .map(r -> new RecommendedProductView(mapper.product(byId.get(r.productId()), prices), r.score(), r.reasons()))
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductDetailView detail(Long id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Listing not found"));
        CurrentUser me = AuthContext.get();
        boolean owner = me.role() == Role.FARMER && p.getFarmer().getId().equals(me.id());
        if (p.getStatus() != ProductStatus.AVAILABLE && !owner && me.role() != Role.ADMIN
                && p.getStatus() != ProductStatus.SOLD) {
            throw ApiException.notFound("Listing not available");
        }
        List<PricePoint> trend = market.history(p.getCropType(), p.getLocation(), 30);
        String basis = "county";
        if (trend.size() < 4) {
            trend = market.history(p.getCropType(), null, 30);
            basis = "national";
        }
        return new ProductDetailView(mapper.product(p), trend, basis);
    }

    Product owned(Long id) {
        Farmer f = users.currentFarmer();
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (!p.getFarmer().getId().equals(f.getId())) throw ApiException.forbidden("This listing belongs to another farmer");
        return p;
    }
}
