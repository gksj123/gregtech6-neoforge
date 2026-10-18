package gregtech.neo.machine;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import gregtech.neo.GT6Neo;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.stream.Collectors;

public final class GTMachineRegistry {
    private static volatile boolean bootstrapped;
    private static List<GTMachineDefinition> machines = List.of();
    private static Map<String, GTMachineDefinition> machinesByKey = Map.of();

    private GTMachineRegistry() {
    }

    private record SourceRecord(
        int line,
        @SerializedName("name_expression") String nameExpression,
        String category,
        @SerializedName("registry_id_expression") String registryIdExpression,
        @SerializedName("class") String machineClass,
        @SerializedName("energy_accepted") String energyAccepted,
        @SerializedName("energy_emitted") String energyEmitted,
        @SerializedName("recipe_map") String recipeMap,
        @SerializedName("fuel_map") String fuelMap,
        @SerializedName("material_expression") String materialExpression,
        @SerializedName("display_name") String displayName,
        int input,
        @SerializedName("input_min") int inputMin,
        @SerializedName("input_max") int inputMax,
        int efficiency,
        int parallel,
        int output,
        @SerializedName("parallel_duration") boolean parallelDuration,
        @SerializedName("cheap_overclocking") boolean cheapOverclocking,
        @SerializedName("no_constant_power") boolean noConstantPower
    ) {
        GTMachineDefinition toDefinition() {
            return new GTMachineDefinition(
                line,
                nameExpression,
                category,
                registryIdExpression,
                machineClass,
                energyAccepted,
                energyEmitted,
                recipeMap,
                fuelMap,
                materialExpression,
                displayName,
                input,
                inputMin,
                inputMax,
                efficiency,
                parallel,
                output,
                parallelDuration,
                cheapOverclocking,
                noConstantPower
            );
        }
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        try (var stream = GTMachineRegistry.class.getResourceAsStream("/data/gregtech/gt6/machines.json")) {
            Objects.requireNonNull(stream, "Missing generated GT6 machine registry");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                List<SourceRecord> records = new Gson().fromJson(
                    reader,
                    new TypeToken<List<SourceRecord>>() {
                    }.getType()
                );
                machines = records.stream().map(SourceRecord::toDefinition).toList();
                indexMachines();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load generated GT6 machine registry", exception);
        }
        bootstrapped = true;
        GT6Neo.LOGGER.info(
            "Loaded GT6 machine registry: {} registrations in {} categories",
            machines.size(),
            categories().size()
        );
    }

    public static Collection<GTMachineDefinition> machines() {
        bootstrap();
        return machines;
    }

    public static GTMachineDefinition find(String category, int sourceLine) {
        bootstrap();
        return machinesByKey.get(key(category, sourceLine));
    }

    public static Map<String, List<GTMachineDefinition>> categories() {
        bootstrapWithoutLogging();
        return machines.stream().collect(Collectors.groupingBy(GTMachineDefinition::category));
    }

    private static synchronized void bootstrapWithoutLogging() {
        if (!bootstrapped) {
            // categories() is called from bootstrap for logging, so load the raw list first.
            try (var stream = GTMachineRegistry.class.getResourceAsStream("/data/gregtech/gt6/machines.json")) {
                Objects.requireNonNull(stream, "Missing generated GT6 machine registry");
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    List<SourceRecord> records = new Gson().fromJson(
                        reader,
                        new TypeToken<List<SourceRecord>>() {
                        }.getType()
                    );
                    machines = records.stream().map(SourceRecord::toDefinition).toList();
                    indexMachines();
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to load generated GT6 machine registry", exception);
            }
        }
    }

    private static void indexMachines() {
        Map<String, GTMachineDefinition> index = new LinkedHashMap<>();
        for (GTMachineDefinition machine : machines) {
            index.put(key(machine.category(), machine.line()), machine);
        }
        machinesByKey = Map.copyOf(index);
    }

    private static String key(String category, int sourceLine) {
        return (category == null ? "" : category.toLowerCase(Locale.ROOT)) + ":" + sourceLine;
    }
}
