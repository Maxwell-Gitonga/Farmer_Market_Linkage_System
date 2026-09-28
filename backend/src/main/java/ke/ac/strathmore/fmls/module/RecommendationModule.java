package ke.ac.strathmore.fmls.module;

import com.fasterxml.jackson.databind.JsonNode;
import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Buyer;
import ke.ac.strathmore.fmls.domain.Match;
import ke.ac.strathmore.fmls.domain.Product;
import ke.ac.strathmore.fmls.domain.TransactionStatus;
import ke.ac.strathmore.fmls.dto.MatchView;
import ke.ac.strathmore.fmls.repository.BuyerRepository;
import ke.ac.strathmore.fmls.repository.MatchRepository;
import ke.ac.strathmore.fmls.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RecommendationModule (sequence diagram 2): matchBuyerToProducts(buyer) → rankMatches(matches), and the
 * reverse direction used for the farmer's "View matched buyers". Scores come from the ML module's
 * recommendation engine (crop preference, proximity, price vs local market, purchase history, freshness).
 */
@Service
public class RecommendationModule {

    public record Ranked(Long productId, Long buyerId, double score, List<String> reasons) { }

    private final MlClient ml;
    private final BuyerRepository buyers;
    private final MatchRepository matches;
    private final TransactionRepository transactions;
    private final MarketDataModule market;

    public RecommendationModule(MlClient ml, BuyerRepository buyers, MatchRepository matches,
                                TransactionRepository transactions, MarketDataModule market) {
        this.ml = ml;
        this.buyers = buyers;
        this.matches = matches;
        this.transactions = transactions;
        this.market = market;
    }

    /** matchBuyerToProducts(buyer) + rankMatches: listings ranked for one buyer. */
    public List<Ranked> matchBuyerToProducts(Buyer buyer, List<Product> listings, int topK) {
        if (listings.isEmpty()) return List.of();
        return rank("products_for_buyer", listings, List.of(buyer), topK);
    }

    /** Matched buyers for a listing – recomputed and stored in the matches table. */
    @Transactional
    public List<MatchView> matchedBuyersForProduct(Product product) {
        List<Buyer> active = buyers.findByStatus(AccountStatus.ACTIVE);
        List<Ranked> ranked = rank("buyers_for_product", List.of(product), active, 10);
        Map<Long, Buyer> byId = active.stream().collect(Collectors.toMap(Buyer::getId, b -> b));
        matches.deleteForProduct(product.getProductId());
        List<MatchView> out = new ArrayList<>();
        for (Ranked r : ranked) {
            Buyer b = byId.get(r.buyerId());
            if (b == null || r.score() < 0.3) continue;
            Match m = new Match();
            m.setProduct(product);
            m.setBuyer(b);
            m.setMatchScore(r.score());
            String reasons = String.join("; ", r.reasons());
            m.setReasons(reasons.length() > 500 ? reasons.substring(0, 500) : reasons);
            matches.save(m);
            out.add(view(b, product, r.score(), r.reasons()));
        }
        return out;
    }

    /** Stored matches for a listing (computed on first request if missing). */
    @Transactional
    public List<MatchView> storedMatches(Product product) {
        List<Match> stored = matches.forProduct(product.getProductId());
        if (stored.isEmpty()) return matchedBuyersForProduct(product);
        return stored.stream().map(RecommendationModule::view).collect(Collectors.toList());
    }

    public static MatchView view(Match m) {
        List<String> reasons = m.getReasons() == null || m.getReasons().isBlank() ? List.of()
                : Arrays.asList(m.getReasons().split("; "));
        return view(m.getBuyer(), m.getProduct(), m.getMatchScore(), reasons);
    }

    private static MatchView view(Buyer b, Product p, Double score, List<String> reasons) {
        return new MatchView(b.getId(), b.getName(), b.getLocation(), b.getPhone(), score, reasons,
                p.getProductId(), p.getCropType());
    }

    private List<Ranked> rank(String mode, List<Product> products, List<Buyer> buyerList, int topK) {
        Map<String, Integer> history = purchaseHistory();
        Map<String, Double> prices = market.latestPrices();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("mode", mode);
            body.put("top_k", topK);
            body.put("market_prices", prices);
            body.put("purchase_history", history);
            List<Map<String, Object>> ps = new ArrayList<>();
            for (Product p : products) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getProductId());
                m.put("crop_type", p.getCropType());
                m.put("location", p.getLocation());
                m.put("price_per_unit", p.getPricePerUnit());
                m.put("quantity", p.getQuantity());
                m.put("harvest_date", p.getHarvestDate() == null ? null : p.getHarvestDate().toString());
                ps.add(m);
            }
            List<Map<String, Object>> bs = new ArrayList<>();
            for (Buyer b : buyerList) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", b.getId());
                m.put("name", b.getName());
                m.put("location", b.getLocation());
                m.put("preferences", b.getPreferences());
                bs.add(m);
            }
            body.put("products", ps);
            body.put("buyers", bs);
            JsonNode r = ml.post("/recommend/matches", body);
            List<Ranked> out = new ArrayList<>();
            r.path("matches").forEach(x -> out.add(new Ranked(x.path("product_id").asLong(), x.path("buyer_id").asLong(),
                    x.path("score").asDouble(), Json.strings(x.path("reasons")))));
            return out;
        } catch (MlUnavailableException e) {
            return fallbackRank(mode, products, buyerList, history, prices, topK);
        }
    }

    /** Offline fallback scoring (same weights, without distance): preference, same county, price, history. */
    private List<Ranked> fallbackRank(String mode, List<Product> products, List<Buyer> buyerList,
                                      Map<String, Integer> history, Map<String, Double> prices, int topK) {
        List<Ranked> all = new ArrayList<>();
        for (Product p : products) {
            for (Buyer b : buyerList) {
                List<String> reasons = new ArrayList<>();
                String prefs = b.getPreferences() == null ? "" : b.getPreferences().toLowerCase();
                double pref = Arrays.stream(prefs.split(",")).map(String::trim)
                        .anyMatch(x -> x.equalsIgnoreCase(p.getCropType())) ? 1 : 0;
                if (pref > 0) reasons.add("Interested in " + p.getCropType());
                double prox = Objects.equals(p.getLocation(), b.getLocation()) ? 1 : 0.4;
                if (prox == 1) reasons.add("Same county");
                Double mp = prices.get(p.getCropType() + "|" + b.getLocation());
                double price = 0.5;
                if (mp != null && p.getPricePerUnit() != null) {
                    double diff = (mp - p.getPricePerUnit()) / mp;
                    price = Math.max(0, Math.min(1, 0.5 + diff * 2.5));
                }
                int n = history.getOrDefault(b.getId() + "|" + p.getCropType(), 0);
                if (n > 0) reasons.add("Bought " + p.getCropType() + " " + n + " time(s) before");
                double score = 0.35 * pref + 0.25 * prox + 0.20 * price + 0.15 * Math.min(1, n / 10.0) + 0.025;
                all.add(new Ranked(p.getProductId(), b.getId(), Math.round(score * 10000) / 10000.0, reasons));
            }
        }
        all.sort(Comparator.comparingDouble(Ranked::score).reversed());
        Map<Long, Integer> perKey = new HashMap<>();
        List<Ranked> out = new ArrayList<>();
        for (Ranked r : all) {
            Long key = mode.equals("buyers_for_product") ? r.productId() : r.buyerId();
            int c = perKey.merge(key, 1, Integer::sum);
            if (c <= topK) out.add(r);
        }
        return out;
    }

    /** "buyerId|Crop" → number of purchases. */
    private Map<String, Integer> purchaseHistory() {
        Map<String, Integer> out = new HashMap<>();
        for (Object[] r : transactions.purchaseCounts(TransactionStatus.CANCELLED)) {
            out.put(r[0] + "|" + r[1], ((Number) r[2]).intValue());
        }
        return out;
    }
}
