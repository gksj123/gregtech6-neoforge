package gregtech.neo.casting;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import gregtech.neo.GT6Neo;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialPrefix;
import gregtech.neo.material.GTMaterialRegistry;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GTMoldRegistry {
    private static final Gson GSON = new Gson();
    private static volatile boolean bootstrapped;
    private static List<MoldVariant> molds = List.of();
    private static List<MoldVariant> faucets = List.of();
    private static List<MoldShape> canonicalShapes = List.of();
    private static Map<Integer, MaskLookup> lookup = Map.of();
    private static String defaultUnknownPrefix = "nugget";

    private GTMoldRegistry() {
    }

    public record MoldVariant(
        @SerializedName("display_name") String displayName,
        @SerializedName("material") String material,
        @SerializedName("source_line") int sourceLine,
        @SerializedName("acid_proof") boolean acidProof
    ) {
    }

    public record MoldShape(
        @SerializedName("name") String name,
        @SerializedName("mask") int mask,
        @SerializedName("prefix") String prefix,
        @SerializedName("source_line") int sourceLine
    ) {
    }

    public record MaskLookup(
        @SerializedName("mask") int mask,
        @SerializedName("prefix") String prefix,
        @SerializedName("source_shape") String sourceShape,
        @SerializedName("source_line") int sourceLine
    ) {
    }

    private record MoldData(
        @SerializedName("mold_variants") List<MoldVariant> moldVariants,
        @SerializedName("faucet_variants") List<MoldVariant> faucetVariants,
        @SerializedName("canonical_shapes") List<MoldShape> canonicalShapes,
        @SerializedName("mask_lookup") List<MaskLookup> maskLookup,
        @SerializedName("default_unknown_prefix") String defaultUnknownPrefix
    ) {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        try (var stream = GTMoldRegistry.class.getResourceAsStream(
            "/data/gregtech/gt6/molds.json"
        )) {
            Objects.requireNonNull(stream, "Missing generated GT6 mold data");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                MoldData data = GSON.fromJson(reader, MoldData.class);
                molds = data.moldVariants().stream()
                    .filter(row -> GTMaterialRegistry.material(row.material()) != null)
                    .toList();
                faucets = data.faucetVariants().stream()
                    .filter(row -> GTMaterialRegistry.material(row.material()) != null)
                    .toList();
                canonicalShapes = List.copyOf(data.canonicalShapes());
                Map<Integer, MaskLookup> index = new LinkedHashMap<>();
                for (MaskLookup row : data.maskLookup()) {
                    index.putIfAbsent(row.mask(), row);
                }
                lookup = Map.copyOf(index);
                defaultUnknownPrefix = data.defaultUnknownPrefix();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load generated GT6 mold data", exception);
        }
        bootstrapped = true;
        GT6Neo.LOGGER.info(
            "Loaded GT6 casting data: {} molds, {} faucets, {} canonical shapes, {} rotated masks",
            molds.size(),
            faucets.size(),
            canonicalShapes.size(),
            lookup.size()
        );
    }

    public static List<MoldVariant> molds() {
        bootstrap();
        return molds;
    }

    public static List<MoldVariant> faucets() {
        bootstrap();
        return faucets;
    }

    public static List<MoldShape> canonicalShapes() {
        bootstrap();
        return canonicalShapes;
    }

    public static int ingotMask() {
        bootstrap();
        return canonicalShapes.stream()
            .filter(shape -> "ingot".equals(shape.name()))
            .mapToInt(MoldShape::mask)
            .findFirst()
            .orElse(0);
    }

    public static MaskLookup lookup(int mask) {
        bootstrap();
        MaskLookup exact = lookup.get(mask & 0x1FFFFFF);
        if (exact != null) {
            return exact;
        }
        return new MaskLookup(
            mask & 0x1FFFFFF,
            defaultUnknownPrefix,
            "nugget",
            0
        );
    }

    public static String prefixForMask(int mask) {
        return lookup(mask).prefix();
    }

    public static long requiredUnits(int mask) {
        String prefixId = prefixForMask(mask);
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(prefixId);
        if (prefix == null) {
            return 0;
        }
        if ("nugget".equals(prefixId)) {
            return (long) Integer.bitCount(mask & 0x1FFFFFF)
                * (GTMaterialGameplayRegistry.MATERIAL_UNIT / 9L);
        }
        return prefix.amount();
    }

    public static int rotateClockwise(int mask) {
        int result = 0;
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                int sourceX = y;
                int sourceY = 4 - x;
                if ((mask & (1 << (sourceX + sourceY * 5))) != 0) {
                    result |= 1 << (x + y * 5);
                }
            }
        }
        return result;
    }

    public static int carve(int mask, double localX, double localZ) {
        if (localX < 2.0 / 16.0 || localX >= 14.0 / 16.0
            || localZ < 2.0 / 16.0 || localZ >= 14.0 / 16.0) {
            return mask;
        }
        int x = Math.min(4, (int) ((localX - 2.0 / 16.0) * 5.0 / (12.0 / 16.0)));
        int z = Math.min(4, (int) ((localZ - 2.0 / 16.0) * 5.0 / (12.0 / 16.0)));
        return mask | (1 << (x * 5 + z));
    }
}
