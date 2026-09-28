package ke.ac.strathmore.fmls.module;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Selected agricultural produce and county markets of the simulated Kenyan market (Proposal §1.6).
 * Mirrors ml-service/app/reference.py so the backend keeps working when the ML module is offline.
 */
public final class CropCatalog {

    public record Crop(String name, String emoji, int shelfLifeDays, List<String> markets) { }

    public static final List<String> COUNTIES = List.of("Nairobi", "Kiambu", "Nakuru", "Uasin Gishu", "Trans Nzoia",
            "Meru", "Nyeri", "Machakos", "Kisumu", "Mombasa");

    private static final Map<String, Crop> CROPS = new LinkedHashMap<>();

    static {
        add("Maize", "🌽", 180);
        add("Beans", "🫘", 180);
        add("Irish Potatoes", "🥔", 45);
        add("Tomatoes", "🍅", 7);
        add("Onions", "🧅", 60);
        add("Cabbages", "🥬", 14);
        add("Sukuma Wiki", "🥗", 3);
        add("Bananas", "🍌", 10);
        CROPS.put("Avocados", new Crop("Avocados", "🥑", 14,
                List.of("Nairobi", "Kiambu", "Meru", "Nyeri", "Nakuru", "Machakos")));
        add("Green Grams", "🫛", 180);
    }

    private static void add(String name, String emoji, int shelf) {
        CROPS.put(name, new Crop(name, emoji, shelf, COUNTIES));
    }

    private CropCatalog() { }

    public static List<Crop> crops() { return List.copyOf(CROPS.values()); }

    /** Case-insensitive lookup with common aliases (kale → Sukuma Wiki, potatoes → Irish Potatoes). */
    public static Optional<Crop> find(String name) {
        if (name == null) return Optional.empty();
        String key = name.trim().toLowerCase();
        for (Crop c : CROPS.values()) {
            if (c.name().toLowerCase().equals(key)) return Optional.of(c);
        }
        Map<String, String> aliases = Map.of("kale", "Sukuma Wiki", "potatoes", "Irish Potatoes",
                "potato", "Irish Potatoes", "ndengu", "Green Grams", "tomato", "Tomatoes", "onion", "Onions",
                "cabbage", "Cabbages", "banana", "Bananas", "avocado", "Avocados", "corn", "Maize");
        String alias = aliases.get(key);
        return alias == null ? Optional.empty() : Optional.ofNullable(CROPS.get(alias));
    }

    /** Canonical crop name, or the trimmed title-cased input for crops outside the catalogue. */
    public static String canonical(String name) {
        return find(name).map(Crop::name).orElseGet(() -> titleCase(name));
    }

    public static String canonicalCounty(String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase();
        for (String c : COUNTIES) {
            if (c.toLowerCase().equals(key)) return c;
        }
        if (key.equals("eldoret")) return "Uasin Gishu";
        if (key.equals("kitale")) return "Trans Nzoia";
        if (key.equals("thika")) return "Kiambu";
        return titleCase(name);
    }

    public static String emoji(String crop) {
        return find(crop).map(Crop::emoji).orElse("🌱");
    }

    private static String titleCase(String s) {
        if (s == null || s.isBlank()) return s;
        StringBuilder b = new StringBuilder();
        for (String w : s.trim().split("\\s+")) {
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return b.toString();
    }
}
