package gregtech.neo.material;

import java.util.Locale;

public record GTMaterial(int id, String name, String symbol, String constructor, String color) {
    public String slug() {
        return slug(name);
    }

    public String prettyName() {
        return name;
    }

    public int provisionalColor() {
        if (color != null && color.matches("#[0-9A-Fa-f]{6}")) {
            return Integer.parseInt(color.substring(1), 16);
        }
        int hash = slug().hashCode();
        int red = 72 + Math.floorMod(hash, 128);
        int green = 72 + Math.floorMod(hash >> 8, 128);
        int blue = 72 + Math.floorMod(hash >> 16, 128);
        return red << 16 | green << 8 | blue;
    }

    public static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
