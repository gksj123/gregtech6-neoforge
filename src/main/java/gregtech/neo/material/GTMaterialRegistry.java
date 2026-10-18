package gregtech.neo.material;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import gregtech.neo.GT6Neo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GTMaterialRegistry {
    private static final Gson GSON = new Gson();
    private static volatile boolean bootstrapped;
    private static Map<String, GTMaterial> materialsBySlug = Map.of();
    private static Map<String, GTMaterialPrefix> prefixesById = Map.of();

    private GTMaterialRegistry() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        List<GTMaterial> materials = readList(
            "/data/gregtech/gt6/materials.json",
            new TypeToken<List<GTMaterial>>() {
            }.getType()
        );
        List<GTMaterialPrefix> prefixes = readList(
            "/data/gregtech/gt6/prefixes.json",
            new TypeToken<List<GTMaterialPrefix>>() {
            }.getType()
        );

        Map<String, GTMaterial> materialMap = new LinkedHashMap<>();
        for (GTMaterial material : materials) {
            materialMap.put(material.slug(), material);
        }
        Map<String, GTMaterialPrefix> prefixMap = new LinkedHashMap<>();
        for (GTMaterialPrefix prefix : prefixes) {
            prefixMap.put(prefix.id(), prefix);
        }
        materialsBySlug = Map.copyOf(materialMap);
        prefixesById = Map.copyOf(prefixMap);
        bootstrapped = true;
        GT6Neo.LOGGER.info(
            "Loaded GT6 source inventory: {} materials, {} material prefixes",
            materialsBySlug.size(),
            prefixesById.size()
        );
    }

    private static <T> List<T> readList(String path, java.lang.reflect.Type type) {
        try (InputStream stream = GTMaterialRegistry.class.getResourceAsStream(path)) {
            Objects.requireNonNull(stream, "Missing GT6 generated resource " + path);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, type);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load generated GT6 inventory " + path, exception);
        }
    }

    public static Collection<GTMaterial> materials() {
        bootstrap();
        return materialsBySlug.values();
    }

    public static Collection<GTMaterialPrefix> prefixes() {
        bootstrap();
        return prefixesById.values();
    }

    public static GTMaterial material(String slug) {
        bootstrap();
        return materialsBySlug.get(slug);
    }

    public static GTMaterialPrefix prefix(String id) {
        bootstrap();
        return prefixesById.get(id);
    }
}
