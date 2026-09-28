package ke.ac.strathmore.fmls.controller;

import jakarta.validation.Valid;
import ke.ac.strathmore.fmls.dto.CropInfo;
import ke.ac.strathmore.fmls.dto.DemandView;
import ke.ac.strathmore.fmls.dto.LatestPriceView;
import ke.ac.strathmore.fmls.dto.MarketOption;
import ke.ac.strathmore.fmls.dto.MetaView;
import ke.ac.strathmore.fmls.dto.PriceForecastView;
import ke.ac.strathmore.fmls.dto.PricePoint;
import ke.ac.strathmore.fmls.dto.QuickEstimateRequest;
import ke.ac.strathmore.fmls.dto.RecommendationView;
import ke.ac.strathmore.fmls.module.CropCatalog;
import ke.ac.strathmore.fmls.module.DemandForecastModule;
import ke.ac.strathmore.fmls.module.MarketDataModule;
import ke.ac.strathmore.fmls.module.PricePredictionModule;
import ke.ac.strathmore.fmls.security.PublicEndpoint;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** Market insights for farmers and buyers: prices, price forecasts, demand forecasts, best markets. */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDataModule market;
    private final PricePredictionModule pricing;
    private final DemandForecastModule demand;

    public MarketController(MarketDataModule market, PricePredictionModule pricing, DemandForecastModule demand) {
        this.market = market;
        this.pricing = pricing;
        this.demand = demand;
    }

    @PublicEndpoint
    @GetMapping("/meta")
    public MetaView meta() {
        List<CropInfo> crops = CropCatalog.crops().stream()
                .map(c -> new CropInfo(c.name(), c.emoji(), c.shelfLifeDays(), "kg", c.markets()))
                .collect(Collectors.toList());
        return new MetaView(crops, CropCatalog.COUNTIES);
    }

    @GetMapping("/prices/latest")
    public List<LatestPriceView> latest(@RequestParam(required = false) String crop) {
        return market.latestPriceTable(crop);
    }

    @GetMapping("/prices/history")
    public List<PricePoint> history(@RequestParam String crop, @RequestParam(required = false) String county,
                                    @RequestParam(defaultValue = "90") int days) {
        return market.history(crop, county, Math.min(Math.max(days, 7), 1200));
    }

    @GetMapping("/forecast")
    public PriceForecastView forecast(@RequestParam String crop, @RequestParam String county,
                                      @RequestParam(defaultValue = "4") int weeks) {
        return pricing.forecast(crop, county, Math.min(Math.max(weeks, 1), 8));
    }

    @GetMapping("/demand")
    public DemandView demand(@RequestParam String crop) {
        return demand.forecastDemand(crop);
    }

    @GetMapping("/best-markets")
    public List<MarketOption> bestMarkets(@RequestParam String crop, @RequestParam String county,
                                          @RequestParam(required = false) Double quantity,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return pricing.bestMarkets(crop, county, quantity, date);
    }

    /** Live preview on the listing form. */
    @PostMapping("/quick-estimate")
    public RecommendationView quickEstimate(@Valid @RequestBody QuickEstimateRequest req) {
        return pricing.preview(req.cropType(), req.location(), req.harvestDate(), req.quantity());
    }
}
