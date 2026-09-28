package ke.ac.strathmore.fmls.dto;

import java.util.List;

/** A supported crop. */
public record CropInfo(
        String name,
        String emoji,
        int shelfLifeDays,
        String unit,
        List<String> markets) {
}
