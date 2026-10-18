package gregtech.neo.recipe;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import gregtech.neo.GT6Neo;
import gregtech.neo.material.GTMaterialGameplayRegistry;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Runtime port of the crucible alloy selector in GT6's smeltery classes.
 *
 * <p>The generated data comes from all source {@code setAloy}, {@code uumAloy}
 * and explicit {@code addAlloyingRecipe} declarations. Air is represented by
 * the crucible's KU-generated oxygen buffer.</p>
 */
public final class GTAlloyRegistry {
    private static final Gson GSON = new Gson();
    private static volatile boolean bootstrapped;
    private static List<GTAlloyRecipe> recipes = List.of();
    private static int unresolvedCount;

    private GTAlloyRegistry() {
    }

    private record AlloyData(
        @SerializedName("recipes") List<GTAlloyRecipe> recipes,
        @SerializedName("unresolved_count") int unresolvedCount
    ) {
    }

    public interface Contents {
        long amount(String material);

        boolean consume(String material, long units);

        void add(String material, long units);
    }

    public record Result(boolean converted, long oxygenUsed, String outputMaterial, long outputUnits) {
        private static final Result NONE = new Result(false, 0, "", 0);
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        try (var stream = GTAlloyRegistry.class.getResourceAsStream(
            "/data/gregtech/gt6/alloys.json"
        )) {
            Objects.requireNonNull(stream, "Missing generated GT6 alloy data");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                AlloyData data = GSON.fromJson(reader, AlloyData.class);
                recipes = data.recipes().stream()
                    .filter(recipe -> "resolved".equals(recipe.status()))
                    .filter(recipe -> recipe.commonDivider() > 0)
                    .filter(recipe -> recipe.components() != null && !recipe.components().isEmpty())
                    .toList();
                unresolvedCount = data.unresolvedCount();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load generated GT6 alloy recipes", exception);
        }
        bootstrapped = true;
        GT6Neo.LOGGER.info(
            "Loaded GT6 crucible alloys: {} resolved recipes, {} unresolved source entries",
            recipes.size(),
            unresolvedCount
        );
    }

    public static int resolvedRecipeCount() {
        bootstrap();
        return recipes.size();
    }

    public static int unresolvedRecipeCount() {
        bootstrap();
        return unresolvedCount;
    }

    public static boolean hasRecipeFor(String material) {
        bootstrap();
        return recipes.stream().anyMatch(recipe -> recipe.outputMaterial().equals(material));
    }

    /**
     * Selects and applies at most one alloy conversion, matching the source
     * rule of maximizing conversions multiplied by the common divider.
     */
    public static Result process(
        Contents contents,
        long temperature,
        long oxygenUnits,
        long freeCapacity
    ) {
        bootstrap();
        if (freeCapacity <= 0) {
            return Result.NONE;
        }
        GTAlloyRecipe selected = null;
        long selectedConversions = 0;
        long selectedWeight = 0;

        for (GTAlloyRecipe recipe : recipes) {
            GTAlloyMaterial output = gameplay(recipe.outputMaterial());
            if (output == null || temperature < output.meltingKelvin()) {
                continue;
            }

            long conversions = Long.MAX_VALUE;
            int nonMolten = 0;
            boolean moltenAnchor = false;
            boolean possible = true;
            for (GTAlloyRecipe.Component component : recipe.components()) {
                if (component.amountUnits() <= 0) {
                    possible = false;
                    break;
                }
                GTAlloyMaterial componentMaterial = gameplay(component.material());
                if (componentMaterial == null) {
                    possible = false;
                    break;
                }
                long available = "air".equals(component.material())
                    ? oxygenUnits
                    : contents.amount(component.material());
                if (available <= 0) {
                    possible = false;
                    break;
                }
                conversions = Math.min(conversions, available / component.amountUnits());
                if (temperature < componentMaterial.meltingKelvin()) {
                    nonMolten++;
                } else if (!"air".equals(component.material()) && available > 0) {
                    moltenAnchor = true;
                }
            }
            if (!possible || !moltenAnchor || nonMolten > 1 || conversions <= 0) {
                continue;
            }

            conversions = Math.min(conversions, freeCapacity / recipe.commonDivider());
            if (conversions <= 0) {
                continue;
            }
            long weight = conversions * recipe.commonDivider();
            if (selected == null || weight > selectedWeight) {
                selected = recipe;
                selectedConversions = conversions;
                selectedWeight = weight;
            }
        }

        if (selected == null) {
            return Result.NONE;
        }

        long oxygenUsed = 0;
        for (GTAlloyRecipe.Component component : selected.components()) {
            long consumed = selectedConversions * component.amountUnits();
            if ("air".equals(component.material())) {
                oxygenUsed += consumed;
            } else if (!contents.consume(component.material(), consumed)) {
                return Result.NONE;
            }
        }
        long outputUnits = selected.commonDivider() * selectedConversions;
        contents.add(selected.outputMaterial(), outputUnits);
        return new Result(true, oxygenUsed, selected.outputMaterial(), outputUnits);
    }

    private static GTAlloyMaterial gameplay(String material) {
        GTMaterialGameplayRegistry.MaterialGameplay row =
            GTMaterialGameplayRegistry.material(material);
        return row == null ? null : new GTAlloyMaterial(row.meltingKelvin());
    }

    private record GTAlloyMaterial(double meltingKelvin) {
    }
}
