package ke.ac.strathmore.fmls.dto;

import java.util.List;

/** A listing recommended to a buyer. */
public record RecommendedProductView(
        ProductView product,
        Double score,
        List<String> reasons) {
}
