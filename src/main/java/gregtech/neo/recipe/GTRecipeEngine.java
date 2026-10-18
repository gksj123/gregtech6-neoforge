package gregtech.neo.recipe;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import gregtech.neo.GT6Neo;
import gregtech.neo.material.GTMaterialItems;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialPrefix;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Matches GT6 recipe-map data against modern item slots.
 *
 * <p>The current data-driven phase intentionally accepts only handlers whose
 * source condition is {@code ANTIMATTER.NOT}, whose fluids/additional items
 * are absent, and whose output remains in the same material. Unsupported
 * source handlers are retained in the generated registry for later phases but
 * are never silently treated as complete recipes.</p>
 */
public final class GTRecipeEngine {
    private static final Gson GSON = new Gson();
    private static final long MATERIAL_UNIT = 648_648_000L;
    private static volatile boolean bootstrapped;
    private static Map<String, List<Handler>> handlersByMap = Map.of();
    private static Map<String, List<ExplicitRecipe>> recipesByMap = Map.of();

    private GTRecipeEngine() {
    }

    public record InputUse(int slot, int count) {
    }

    public record Plan(
        String id,
        List<InputUse> inputs,
        List<ItemStack> outputs,
        long powerPerTick,
        long durationTicks,
        int runs,
        String source,
        int line
    ) {
        public Plan {
            inputs = List.copyOf(inputs);
            outputs = outputs.stream().map(ItemStack::copy).toList();
        }
    }

    private record StackRecord(
        String item,
        String prefix,
        int count
    ) {
    }

    private record HandlerRecord(
        String map,
        @SerializedName("handler_type") String handlerType,
        List<StackRecord> inputs,
        List<StackRecord> outputs,
        long eut,
        long duration,
        long multiplier,
        @SerializedName("pulverized_remains") boolean pulverizedRemains,
        @SerializedName("flat_fluid") boolean flatFluid,
        String condition,
        boolean supported,
        String source,
        int line
    ) {
        Handler toHandler() {
            return new Handler(
                map,
                handlerType,
                nullToEmpty(inputs),
                nullToEmpty(outputs),
                eut,
                duration,
                multiplier,
                pulverizedRemains,
                flatFluid,
                condition,
                supported,
                source,
                line
            );
        }
    }

    private record ExplicitRecipe(
        String id,
        String map,
        List<StackRecord> inputs,
        List<StackRecord> outputs,
        long eut,
        long duration,
        String source,
        int line
    ) {
        ExplicitRecipe normalized() {
            return new ExplicitRecipe(
                id,
                map,
                nullToEmpty(inputs),
                nullToEmpty(outputs),
                eut,
                duration,
                source,
                line
            );
        }
    }

    private record Handler(
        String map,
        String handlerType,
        List<StackRecord> inputs,
        List<StackRecord> outputs,
        long eut,
        long duration,
        long multiplier,
        boolean pulverizedRemains,
        boolean flatFluid,
        String condition,
        boolean supported,
        String source,
        int line
    ) {
    }

    private record MaterialCount(String material, String prefix, int count) {
    }

    private record Timing(long power, long duration) {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        List<HandlerRecord> handlerRecords = readList(
            "/data/gregtech/gt6/recipe_handlers.json",
            new TypeToken<List<HandlerRecord>>() {
            }.getType()
        );
        List<ExplicitRecipe> recipeRecords = readList(
            "/data/gregtech/gt6/recipes.json",
            new TypeToken<List<ExplicitRecipe>>() {
            }.getType()
        );

        Map<String, List<Handler>> handlers = new LinkedHashMap<>();
        for (HandlerRecord record : handlerRecords) {
            Handler handler = record.toHandler();
            if (!handler.supported || handler.eut <= 0 || handler.inputs.isEmpty() || handler.outputs.isEmpty()) {
                continue;
            }
            handlers.computeIfAbsent(handler.map, ignored -> new ArrayList<>()).add(handler);
        }
        Map<String, List<ExplicitRecipe>> recipes = new LinkedHashMap<>();
        for (ExplicitRecipe record : recipeRecords) {
            ExplicitRecipe recipe = record.normalized();
            if (recipe.eut <= 0 || recipe.inputs.isEmpty() || recipe.outputs.isEmpty()) {
                continue;
            }
            recipes.computeIfAbsent(recipe.map, ignored -> new ArrayList<>()).add(recipe);
        }
        handlers.replaceAll((ignored, list) -> list.stream()
            .sorted(Comparator.comparingInt(Handler::line))
            .toList());
        recipes.replaceAll((ignored, list) -> list.stream()
            .sorted(Comparator.comparingInt(ExplicitRecipe::line))
            .toList());
        handlersByMap = Map.copyOf(handlers);
        recipesByMap = Map.copyOf(recipes);
        bootstrapped = true;
        GT6Neo.LOGGER.info(
            "Loaded GT6 recipe engine: {} supported material handlers, {} explicit item recipes",
            handlers.values().stream().mapToInt(List::size).sum(),
            recipes.values().stream().mapToInt(List::size).sum()
        );
    }

    private static <T> List<T> readList(String path, java.lang.reflect.Type type) {
        try (var stream = GTRecipeEngine.class.getResourceAsStream(path)) {
            Objects.requireNonNull(stream, "Missing generated GT6 recipe resource " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                List<T> result = GSON.fromJson(reader, type);
                return result == null ? List.of() : result;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load generated GT6 recipe resource " + path, exception);
        }
    }

    public static Plan findPlan(
        String recipeMap,
        List<ItemStack> slots,
        int inputStart,
        int inputEnd,
        int outputStart,
        int outputEnd,
        int parallel,
        int inputMin,
        int inputMax,
        boolean parallelDuration,
        boolean cheapOverclocking,
        int efficiency
    ) {
        bootstrap();
        if (recipeMap == null || recipeMap.isBlank()) {
            return null;
        }
        for (ExplicitRecipe recipe : recipesByMap.getOrDefault(recipeMap, List.of())) {
            Plan plan = planExplicitRecipe(
                recipe,
                slots,
                inputStart,
                inputEnd,
                outputStart,
                outputEnd,
                parallel,
                inputMin,
                inputMax,
                parallelDuration,
                cheapOverclocking,
                efficiency
            );
            if (plan != null) {
                return plan;
            }
        }
        for (Handler handler : handlersByMap.getOrDefault(recipeMap, List.of())) {
            Plan plan = planHandlerRecipe(
                handler,
                slots,
                inputStart,
                inputEnd,
                outputStart,
                outputEnd,
                parallel,
                inputMin,
                inputMax,
                parallelDuration,
                cheapOverclocking,
                efficiency
            );
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    private static Plan planExplicitRecipe(
        ExplicitRecipe recipe,
        List<ItemStack> slots,
        int inputStart,
        int inputEnd,
        int outputStart,
        int outputEnd,
        int parallel,
        int inputMin,
        int inputMax,
        boolean parallelDuration,
        boolean cheapOverclocking,
        int efficiency
    ) {
        List<InputUse> inputs = new ArrayList<>();
        int runs = Math.max(1, parallel);
        for (StackRecord input : recipe.inputs) {
            int available = 0;
            for (int slot = inputStart; slot < inputEnd; slot++) {
                ItemStack stack = slots.get(slot);
                if (input.item.equals(itemId(stack))) {
                    available += stack.getCount();
                }
            }
            if (input.count <= 0 || available < input.count) {
                return null;
            }
            runs = Math.min(runs, available / input.count);
        }
        if (runs <= 0) {
            return null;
        }
        for (StackRecord input : recipe.inputs) {
            int needed = input.count * runs;
            for (int slot = inputStart; slot < inputEnd && needed > 0; slot++) {
                ItemStack stack = slots.get(slot);
                if (!input.item.equals(itemId(stack))) {
                    continue;
                }
                int consumed = Math.min(needed, stack.getCount());
                if (consumed > 0) {
                    inputs.add(new InputUse(slot, consumed));
                    needed -= consumed;
                }
            }
            if (needed > 0) {
                return null;
            }
        }

        List<ItemStack> outputs = scaledItemOutputs(recipe.outputs, runs);
        if (!canFitAll(slots, outputStart, outputEnd, outputs)) {
            return null;
        }
        Timing timing = timing(
            recipe.eut,
            recipe.duration,
            runs,
            parallelDuration,
            cheapOverclocking,
            inputMin,
            inputMax,
            efficiency
        );
        return new Plan(
            "recipe:" + recipe.id,
            inputs,
            outputs,
            timing.power,
            timing.duration,
            runs,
            recipe.source,
            recipe.line
        );
    }

    private static Plan planHandlerRecipe(
        Handler handler,
        List<ItemStack> slots,
        int inputStart,
        int inputEnd,
        int outputStart,
        int outputEnd,
        int parallel,
        int inputMin,
        int inputMax,
        boolean parallelDuration,
        boolean cheapOverclocking,
        int efficiency
    ) {
        List<MaterialCount> available = materialCounts(slots, inputStart, inputEnd);
        String material = null;
        int runs = Math.max(1, parallel);
        for (StackRecord input : handler.inputs) {
            MaterialCount selected = null;
            for (MaterialCount candidate : available) {
                if (!candidate.prefix.equals(input.prefix)) {
                    continue;
                }
                if (material != null && !material.equals(candidate.material)) {
                    continue;
                }
                if (selected == null || candidate.count > selected.count) {
                    selected = candidate;
                }
            }
            if (selected == null || input.count <= 0) {
                return null;
            }
            material = selected.material;
            runs = Math.min(runs, selected.count / input.count);
        }
        if (material == null || runs <= 0) {
            return null;
        }

        List<InputUse> inputUses = materialInputUses(slots, inputStart, inputEnd, handler.inputs, material, runs);
        if (inputUses == null) {
            return null;
        }

        long inputUnits = units(handler.inputs);
        long outputUnits = units(handler.outputs);
        while (runs > 0) {
            List<ItemStack> outputs = materialOutputs(
                handler.outputs,
                material,
                runs,
                handler.pulverizedRemains,
                Math.max(0, inputUnits - outputUnits) * runs
            );
            if (!outputs.isEmpty() && canFitAll(slots, outputStart, outputEnd, outputs)) {
                Timing timing = timing(
                    handler.eut,
                    recipeDuration(handler, material),
                    runs,
                    parallelDuration,
                    cheapOverclocking,
                    inputMin,
                    inputMax,
                    efficiency
                );
                return new Plan(
                    "handler:" + handler.map + ":" + handler.line + ":" + material + ":" + runs,
                    inputUses,
                    outputs,
                    timing.power,
                    timing.duration,
                    runs,
                    handler.source,
                    handler.line
                );
            }
            runs--;
            if (runs > 0) {
                inputUses = materialInputUses(
                    slots,
                    inputStart,
                    inputEnd,
                    handler.inputs,
                    material,
                    runs
                );
                if (inputUses == null) {
                    return null;
                }
            }
        }
        return null;
    }

    private static List<MaterialCount> materialCounts(List<ItemStack> slots, int inputStart, int inputEnd) {
        Map<String, MaterialCount> counts = new LinkedHashMap<>();
        for (int slot = inputStart; slot < inputEnd; slot++) {
            ItemStack stack = slots.get(slot);
            MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
            if (part == null) {
                continue;
            }
            String key = part.material() + "|" + part.prefix();
            MaterialCount existing = counts.get(key);
            counts.put(
                key,
                new MaterialCount(
                    part.material(),
                    part.prefix(),
                    (existing == null ? 0 : existing.count) + stack.getCount()
                )
            );
        }
        return List.copyOf(counts.values());
    }

    private static List<InputUse> materialInputUses(
        List<ItemStack> slots,
        int inputStart,
        int inputEnd,
        List<StackRecord> required,
        String material,
        int runs
    ) {
        List<InputUse> uses = new ArrayList<>();
        for (StackRecord input : required) {
            int needed = input.count * runs;
            for (int slot = inputStart; slot < inputEnd && needed > 0; slot++) {
                ItemStack stack = slots.get(slot);
                MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
                if (part == null || !material.equals(part.material()) || !input.prefix.equals(part.prefix())) {
                    continue;
                }
                int consumed = Math.min(needed, stack.getCount());
                if (consumed > 0) {
                    uses.add(new InputUse(slot, consumed));
                    needed -= consumed;
                }
            }
            if (needed > 0) {
                return null;
            }
        }
        return uses;
    }

    private static List<ItemStack> materialOutputs(
        List<StackRecord> records,
        String material,
        int runs,
        boolean pulverizedRemains,
        long remainingUnits
    ) {
        List<ItemStack> outputs = new ArrayList<>();
        for (StackRecord record : records) {
            if (record.prefix == null || record.prefix.isBlank() || record.count <= 0) {
                continue;
            }
            int count = saturatedInt(record.count, runs);
            addSplitMaterialStack(outputs, material, record.prefix, count);
        }
        if (pulverizedRemains && remainingUnits > 0) {
            addPulverizedRemains(outputs, material, remainingUnits);
        }
        return outputs;
    }

    private static List<ItemStack> scaledItemOutputs(List<StackRecord> records, int runs) {
        List<ItemStack> outputs = new ArrayList<>();
        for (StackRecord record : records) {
            ResourceLocation id = ResourceLocation.tryParse(record.item);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            int count = saturatedInt(record.count, runs);
            while (count > 0) {
                int stackCount = Math.min(64, count);
                outputs.add(new ItemStack(BuiltInRegistries.ITEM.get(id), stackCount));
                count -= stackCount;
            }
        }
        return outputs;
    }

    private static void addPulverizedRemains(List<ItemStack> outputs, String material, long units) {
        GTMaterialPrefix dust = GTMaterialRegistry.prefix("dust");
        GTMaterialPrefix dustSmall = GTMaterialRegistry.prefix("dustSmall");
        GTMaterialPrefix dustTiny = GTMaterialRegistry.prefix("dustTiny");
        GTMaterialPrefix dustDiv72 = GTMaterialRegistry.prefix("dustDiv72");
        if (dust == null || dustSmall == null || dustTiny == null || dustDiv72 == null) {
            return;
        }
        String prefix;
        long count;
        if (units >= MATERIAL_UNIT && (units % MATERIAL_UNIT == 0 || units >= MATERIAL_UNIT * 16)) {
            prefix = dust.id();
            count = units / MATERIAL_UNIT;
        } else if (units * 4 >= MATERIAL_UNIT) {
            prefix = dustSmall.id();
            count = units * 4 / MATERIAL_UNIT;
        } else if (units * 9 >= MATERIAL_UNIT) {
            prefix = dustTiny.id();
            count = units * 9 / MATERIAL_UNIT;
        } else if (units >= dustDiv72.amount()) {
            prefix = dustDiv72.id();
            count = units * 72 / MATERIAL_UNIT;
        } else {
            return;
        }
        addSplitMaterialStack(outputs, material, prefix, (int) Math.min(Integer.MAX_VALUE, Math.max(1, count)));
    }

    private static void addSplitMaterialStack(
        List<ItemStack> outputs,
        String material,
        String prefix,
        int count
    ) {
        while (count > 0) {
            int stackCount = Math.min(64, count);
            ItemStack stack = GTMaterialItems.createPart(material, prefix, stackCount);
            if (stack.isEmpty()) {
                return;
            }
            outputs.add(stack);
            count -= stackCount;
        }
    }

    private static boolean canFitAll(
        List<ItemStack> slots,
        int outputStart,
        int outputEnd,
        List<ItemStack> outputs
    ) {
        List<ItemStack> simulated = new ArrayList<>(outputEnd - outputStart);
        for (int slot = outputStart; slot < outputEnd; slot++) {
            simulated.add(slots.get(slot).copy());
        }
        for (ItemStack output : outputs) {
            if (!simulateInsert(simulated, output.copy())) {
                return false;
            }
        }
        return true;
    }

    private static boolean simulateInsert(List<ItemStack> slots, ItemStack remaining) {
        for (int index = 0; index < slots.size() && !remaining.isEmpty(); index++) {
            ItemStack existing = slots.get(index);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }
            int inserted = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (inserted > 0) {
                ItemStack grown = existing.copy();
                grown.grow(inserted);
                slots.set(index, grown);
                remaining.shrink(inserted);
            }
        }
        for (int index = 0; index < slots.size() && !remaining.isEmpty(); index++) {
            if (!slots.get(index).isEmpty()) {
                continue;
            }
            int inserted = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.copy();
            placed.setCount(inserted);
            slots.set(index, placed);
            remaining.shrink(inserted);
        }
        return remaining.isEmpty();
    }

    private static long recipeDuration(Handler handler, String material) {
        if (handler.duration > 0) {
            return handler.duration;
        }
        long units = Math.max(units(handler.inputs), units(handler.outputs));
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(material);
        long toolQuality = gameplay == null ? 0 : Math.max(0, gameplay.toolQuality());
        long factor = saturatedMultiply(
            Math.max(0, handler.multiplier),
            saturatedAdd(1, toolQuality)
        );
        if (units <= 0 || factor <= 0) {
            return 1;
        }
        return Math.max(1, ceilDivide(saturatedMultiply(units, factor), MATERIAL_UNIT));
    }

    private static long units(List<StackRecord> records) {
        long total = 0;
        for (StackRecord record : records) {
            if (record.prefix == null) {
                continue;
            }
            GTMaterialPrefix prefix = GTMaterialRegistry.prefix(record.prefix);
            if (prefix == null || prefix.amount() <= 0 || record.count <= 0) {
                continue;
            }
            total = saturatedAdd(total, saturatedMultiply(prefix.amount(), record.count));
        }
        return total;
    }

    private static Timing timing(
        long eut,
        long baseDuration,
        int runs,
        boolean parallelDuration,
        boolean cheapOverclocking,
        int inputMin,
        int inputMax,
        int efficiency
    ) {
        long power = parallelDuration ? eut : saturatedMultiply(eut, runs);
        long duration = parallelDuration ? saturatedMultiply(baseDuration, runs) : baseDuration;
        long efficiencyValue = efficiency > 0 ? efficiency : 10_000;
        if (efficiencyValue < 10_000) {
            duration = ceilDivide(saturatedMultiply(duration, 10_000), efficiencyValue);
        }
        if (!cheapOverclocking) {
            while (power < inputMin && power > 0 && power <= inputMax / 4) {
                power *= 4;
                duration = ceilDivide(duration, 2);
            }
        }
        return new Timing(Math.max(1, power), Math.max(1, duration));
    }

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static int saturatedInt(int left, int right) {
        long value = saturatedMultiply(left, right);
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(Math.abs(left), Math.abs(right));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long ceilDivide(long value, long divisor) {
        return value <= 0 ? 0 : 1 + (value - 1) / divisor;
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
