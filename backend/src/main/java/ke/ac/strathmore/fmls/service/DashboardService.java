package ke.ac.strathmore.fmls.service;

import ke.ac.strathmore.fmls.domain.Farmer;
import ke.ac.strathmore.fmls.domain.PricePrediction;
import ke.ac.strathmore.fmls.domain.ProductStatus;
import ke.ac.strathmore.fmls.domain.Transaction;
import ke.ac.strathmore.fmls.domain.TransactionStatus;
import ke.ac.strathmore.fmls.dto.DemandView;
import ke.ac.strathmore.fmls.dto.FarmerDashboard;
import ke.ac.strathmore.fmls.dto.MatchView;
import ke.ac.strathmore.fmls.dto.ProductView;
import ke.ac.strathmore.fmls.dto.TransactionView;
import ke.ac.strathmore.fmls.module.DemandForecastModule;
import ke.ac.strathmore.fmls.module.RecommendationModule;
import ke.ac.strathmore.fmls.repository.MatchRepository;
import ke.ac.strathmore.fmls.repository.PricePredictionRepository;
import ke.ac.strathmore.fmls.repository.ProductRepository;
import ke.ac.strathmore.fmls.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Farmer dashboard (wireframe "Farmer — Dashboard"). */
@Service
public class DashboardService {

    private final UserDirectory users;
    private final ProductService productService;
    private final ProductRepository products;
    private final PricePredictionRepository predictions;
    private final MatchRepository matches;
    private final TransactionRepository transactions;
    private final DemandForecastModule demand;

    public DashboardService(UserDirectory users, ProductService productService, ProductRepository products,
                            PricePredictionRepository predictions, MatchRepository matches,
                            TransactionRepository transactions, DemandForecastModule demand) {
        this.users = users;
        this.productService = productService;
        this.products = products;
        this.predictions = predictions;
        this.matches = matches;
        this.transactions = transactions;
        this.demand = demand;
    }

    @Transactional
    public FarmerDashboard farmer() {
        Farmer f = users.currentFarmer();
        List<ProductView> listings = productService.myListings();
        Optional<PricePrediction> latest = predictions.findTopByProductFarmerFarmerIdOrderByCreatedAtDesc(f.getId());
        String recCrop = latest.map(p -> p.getProduct().getCropType()).orElse(null);
        Double recPrice = latest.map(PricePrediction::getRecommendedPrice).orElse(null);
        String recConf = latest.map(PricePrediction::getConfidenceLabel).orElse(null);

        // Demand trend for the farmer's main crop (most recent open listing, else latest prediction)
        String demandCrop = listings.stream().filter(p -> p.status() != ProductStatus.SOLD).map(ProductView::cropType)
                .findFirst().orElse(recCrop);
        String trend = null;
        Double change = null;
        if (demandCrop != null) {
            DemandView d = demand.forecastDemand(demandCrop);
            if (d.sufficientData()) {
                trend = d.trend();
                change = d.changePct();
            }
        }

        // Matched buyers across active listings (best match per buyer)
        Set<Long> seen = new HashSet<>();
        List<MatchView> matched = matches.forFarmer(f.getId(), ProductStatus.AVAILABLE).stream()
                .map(RecommendationModule::view).filter(m -> seen.add(m.buyerId())).limit(6).collect(Collectors.toList());

        List<Transaction> sales = transactions.forFarmerSince(f.getId(), LocalDateTime.now().minusDays(30));
        double total = sales.stream().filter(t -> t.getStatus() != TransactionStatus.CANCELLED)
                .mapToDouble(t -> t.getAgreedPrice() * (t.getQuantity() == null ? 0 : t.getQuantity())).sum();
        long pending = sales.stream().filter(t -> t.getStatus() == TransactionStatus.CONFIRMED).count();
        List<TransactionView> recent = sales.stream().limit(8).map(ViewMapper::transaction).collect(Collectors.toList());

        return new FarmerDashboard(f.getStatus(), recCrop, recPrice, recConf, demandCrop, trend, change,
                products.countByFarmerFarmerIdAndStatus(f.getId(), ProductStatus.AVAILABLE),
                products.countByFarmerFarmerIdAndStatus(f.getId(), ProductStatus.DRAFT),
                listings, matched, recent, Math.round(total * 100) / 100.0, pending);
    }
}
