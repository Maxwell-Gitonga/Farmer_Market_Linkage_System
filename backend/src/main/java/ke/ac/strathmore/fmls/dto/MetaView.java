package ke.ac.strathmore.fmls.dto;

import java.util.List;

/** Reference data for forms (crops and county markets). */
public record MetaView(
        List<CropInfo> crops,
        List<String> counties) {
}
