package ke.ac.strathmore.fmls.module;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Small helpers for reading ML module JSON responses safely. */
final class Json {
    private Json() { }

    static Double dbl(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() || !n.isNumber() ? null : n.asDouble();
    }

    static Integer integer(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() || !n.isNumber() ? null : n.asInt();
    }

    static String str(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.asText();
    }

    static LocalDate date(JsonNode n) {
        String s = str(n);
        return s == null || s.isBlank() ? null : LocalDate.parse(s.substring(0, 10));
    }

    static List<String> strings(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) n.forEach(x -> out.add(x.asText()));
        return out;
    }
}
