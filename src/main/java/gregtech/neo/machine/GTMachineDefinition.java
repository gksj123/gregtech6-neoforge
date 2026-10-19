package gregtech.neo.machine;

public record GTMachineDefinition(
    int line,
    String name_expression,
    String category,
    String registry_id_expression,
    String machine_class,
    String energy_accepted,
    String energy_emitted,
    String recipe_map,
    String fuel_map,
    String material_expression,
    String material_name,
    String material_slug,
    String display_name,
    int input,
    int input_min,
    int input_max,
    int efficiency,
    int parallel,
    int output,
    long inputValue,
    long inputMinValue,
    long inputMaxValue,
    long outputValue,
    long capacity,
    long outputSu,
    long capacitySu,
    boolean parallel_duration,
    boolean cheap_overclocking,
    boolean no_constant_power
) {
}
