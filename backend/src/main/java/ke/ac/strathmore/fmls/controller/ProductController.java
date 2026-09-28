package ke.ac.strathmore.fmls.controller;

import jakarta.validation.Valid;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.dto.CreateProductRequest;
import ke.ac.strathmore.fmls.dto.ListingCreatedResponse;
import ke.ac.strathmore.fmls.dto.MatchView;
import ke.ac.strathmore.fmls.dto.ProductDetailView;
import ke.ac.strathmore.fmls.dto.ProductView;
import ke.ac.strathmore.fmls.dto.PublishRequest;
import ke.ac.strathmore.fmls.dto.PublishResponse;
import ke.ac.strathmore.fmls.dto.RecommendationView;
import ke.ac.strathmore.fmls.dto.UpdateProductRequest;
import ke.ac.strathmore.fmls.security.RequireRole;
import ke.ac.strathmore.fmls.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Product listings: farmers add / modify / delete, buyers browse and search. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    /** Browse / search available listings. */
    @GetMapping
    public List<ProductView> search(@RequestParam(required = false) String crop,
                                    @RequestParam(required = false) String county,
                                    @RequestParam(required = false) Double maxPrice,
                                    @RequestParam(required = false, defaultValue = "newest") String sort) {
        return products.getAvailableListings(crop, county, maxPrice, sort);
    }

    @GetMapping("/{id}")
    public ProductDetailView detail(@PathVariable Long id) {
        return products.detail(id);
    }

    @RequireRole(Role.FARMER)
    @GetMapping("/mine")
    public List<ProductView> mine() {
        return products.myListings();
    }

    /** addListing(): creates the listing (DRAFT) and returns it with its price recommendation. */
    @RequireRole(Role.FARMER)
    @PostMapping
    public ListingCreatedResponse create(@Valid @RequestBody CreateProductRequest req) {
        return products.addListing(req);
    }

    @RequireRole(Role.FARMER)
    @PostMapping("/{id}/publish")
    public PublishResponse publish(@PathVariable Long id, @Valid @RequestBody PublishRequest req) {
        return products.publish(id, req);
    }

    @RequireRole(Role.FARMER)
    @GetMapping("/{id}/recommendation")
    public RecommendationView recommendation(@PathVariable Long id,
                                             @RequestParam(defaultValue = "false") boolean refresh) {
        return products.recommendation(id, refresh);
    }

    @RequireRole(Role.FARMER)
    @GetMapping("/{id}/matches")
    public List<MatchView> matches(@PathVariable Long id) {
        return products.matches(id);
    }

    @RequireRole(Role.FARMER)
    @PutMapping("/{id}")
    public ProductView update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest req) {
        return products.update(id, req);
    }

    @RequireRole(Role.FARMER)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        products.delete(id);
        return ResponseEntity.ok(Map.of("message", "Listing deleted"));
    }
}
