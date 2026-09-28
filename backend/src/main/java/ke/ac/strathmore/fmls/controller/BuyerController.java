package ke.ac.strathmore.fmls.controller;

import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.dto.RecommendedProductView;
import ke.ac.strathmore.fmls.security.RequireRole;
import ke.ac.strathmore.fmls.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/buyer")
@RequireRole(Role.BUYER)
public class BuyerController {

    private final ProductService products;

    public BuyerController(ProductService products) {
        this.products = products;
    }

    /** "View recommended products" (matchBuyerToProducts → rankMatches). */
    @GetMapping("/recommendations")
    public List<RecommendedProductView> recommendations(@RequestParam(defaultValue = "6") int limit) {
        return products.recommendedForBuyer(Math.min(Math.max(limit, 1), 30));
    }
}
