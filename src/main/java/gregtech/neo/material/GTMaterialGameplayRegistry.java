package gregtech.neo.material;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import gregtech.neo.GT6Neo;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GTMaterialGameplayRegistry {
    public static final long MATERIAL_UNIT = 648_648_000L;
    private static final Gson GSON = new Gson();
    private static volatile boolean bootstrapped;
    private static Map<String, MaterialGameplay> materialsBySlug = Map.of();

    private GTMaterialGameplayRegistry() {
    }

    public record MaterialGameplay(
        int id,
        String name,
        String slug,
        String symbol,
        @SerializedName("melting_kelvin") double meltingKelvin,
        @SerializedName("boiling_kelvin") double boilingKelvin,
        @SerializedName("plasma_kelvin") double plasmaKelvin,
        @SerializedName("tool_types") int toolTypes,
        @SerializedName("tool_speed") float toolSpeed,
        @SerializedName("tool_durability") long toolDurability,
        @SerializedName("tool_quality") int toolQuality,
        @SerializedName("density_g_cm3") double densityGramsPerCubicCentimeter,
        boolean acidic,
        @SerializedName("solidifying_material") String solidifyingMaterial,
        @SerializedName("solidifying_amount") long solidifyingAmount,
        @SerializedName("heat_confidence") String heatConfidence,
        @SerializedName("tool_confidence") String toolConfidence,
        @SerializedName("density_confidence") String densityConfidence,
        @SerializedName("source_line") int sourceLine
    ) {
        public double weightKilograms(long materialUnits) {
            return densityGramsPerCubicCentimeter * 111.111111 * materialUnits / MATERIAL_UNIT;
        }
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        try (var stream = GTMaterialGameplayRegistry.class.getResourceAsStream(
            "/data/gregtech/gt6/materials_gameplay.json"
        )) {
            Objects.requireNonNull(stream, "Missing generated GT6 material gameplay data");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                List<MaterialGameplay> materials = GSON.fromJson(
                    reader,
                    new TypeToken<List<MaterialGameplay>>() {
                    }.getType()
                );
                Map<String, MaterialGameplay> index = new LinkedHashMap<>();
                for (MaterialGameplay material : materials) {
                    index.put(material.slug(), material);
                }
                materialsBySlug = Map.copyOf(index);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load generated GT6 material gameplay data", exception);
        }
        bootstrapped = true;
        GT6Neo.LOGGER.info(
            "Loaded GT6 material gameplay data: {} materials, {} explicit tool profiles",
            materialsBySlug.size(),
            materialsBySlug.values().stream()
                .filter(material -> material.toolTypes() > 0)
                .count()
        );
    }

    public static MaterialGameplay material(String slug) {
        bootstrap();
        return materialsBySlug.get(slug);
    }
}
