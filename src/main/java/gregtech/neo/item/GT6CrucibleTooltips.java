package gregtech.neo.item;

import gregtech.neo.machine.GTMachineDefinition;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Set;

/**
 * Reproduces the 1.7.10 GT6 crucible item tooltips line for line.
 *
 * <p>The strings, their order, and their colour codes are taken from
 * {@code MultiTileEntitySmeltery.addToolTips}, {@code MultiTileEntityCrucible.addToolTips},
 * and the common {@code MultiTileEntityItemInternal.addInformation} tail.</p>
 */
public final class GT6CrucibleTooltips {
    private static final long KG_PER_HEAT_UNIT = 100L;
    private static final long SMALL_WALL_UNITS = 7L;
    private static final long LARGE_WALL_UNITS = 100L;
    private static final double SMALL_HEAT_RESISTANCE_BONUS = 1.25D;
    private static final double LARGE_HEAT_RESISTANCE_BONUS = 1.10D;

    /** Materials whose source registration passes {@code NBT_ACIDPROOF, T}. */
    private static final Set<String> ACID_PROOF_MATERIALS = Set.of(
        "stainlesssteel",
        "netherite",
        "thaumium",
        "chromium",
        "iridium",
        "tungsten",
        "voidmetal",
        "adamantium"
    );

    private GT6CrucibleTooltips() {
    }

    public static void appendSmallCrucible(List<Component> tooltip, GTMachineDefinition definition) {
        String materialSlug = definition.material_slug() == null ? "" : definition.material_slug();
        GTMaterialGameplayRegistry.MaterialGameplay material =
            materialSlug.isBlank() ? null : GTMaterialGameplayRegistry.material(materialSlug);

        line(tooltip, ChatFormatting.AQUA, conversionLine());
        line(tooltip, ChatFormatting.YELLOW,
            "Thermal Mass: " + weight(material, SMALL_WALL_UNITS) + " kg");
        line(tooltip, ChatFormatting.DARK_RED,
            "Melts down when stored Heat is too much! ("
                + maximumTemperature(material, SMALL_HEAT_RESISTANCE_BONUS) + " K)");
        line(tooltip, ChatFormatting.WHITE, "KU Input will turn into Air for Steelmaking");
        if (ACID_PROOF_MATERIALS.contains(materialSlug)) {
            line(tooltip, ChatFormatting.GOLD, "Can handle Acids");
        }
        line(tooltip, ChatFormatting.DARK_RED, "Can put Blocks around it on Fire! (4m)");
        line(tooltip, ChatFormatting.DARK_RED, "Causes Damage when touched while active!");
        line(tooltip, ChatFormatting.DARK_GRAY, "Use Thermometer to Measure");
        line(tooltip, ChatFormatting.DARK_GRAY, "Use Shovel to empty this");
        harvest(tooltip, material == null ? 0 : material.toolQuality());
    }

    public static void appendLargeCrucible(List<Component> tooltip) {
        // The port currently exposes the source's Large Steel Crucible registration.
        GTMaterialGameplayRegistry.MaterialGameplay material =
            GTMaterialGameplayRegistry.material("steel");

        line(tooltip, ChatFormatting.AQUA, "Structure:");
        line(tooltip, ChatFormatting.WHITE, "3x3x3 Hollow of Walls with opening on Top.");
        line(tooltip, ChatFormatting.WHITE, "Main at Bottom-Center.");
        line(tooltip, ChatFormatting.WHITE,
            "Energy IN from Bottom Layer, Stuff IN from Top Layer.");
        line(tooltip, ChatFormatting.WHITE, "Molds usable at second Layer of Walls");
        line(tooltip, ChatFormatting.AQUA, conversionLine());
        line(tooltip, ChatFormatting.YELLOW,
            "Thermal Mass: " + weight(material, LARGE_WALL_UNITS) + " kg");
        line(tooltip, ChatFormatting.DARK_RED,
            "Melts down when stored Heat is too much! ("
                + maximumTemperature(material, LARGE_HEAT_RESISTANCE_BONUS) + " K)");
        line(tooltip, ChatFormatting.WHITE,
            "KU at Bottom Layer will turn into Air for Steelmaking");
        // Large Steel Crucible does not set NBT_ACIDPROOF in the source.
        line(tooltip, ChatFormatting.DARK_RED, "Can put Blocks around it on Fire! (6m)");
        line(tooltip, ChatFormatting.DARK_RED, "Causes Damage when touched while active!");
        line(tooltip, ChatFormatting.DARK_GRAY, "Use Shovel to empty this");
        harvest(tooltip, material == null ? 2 : material.toolQuality());
    }

    private static String conversionLine() {
        return "Converts 1 HU into +1 K per " + KG_PER_HEAT_UNIT
            + "kg (at least 1 Units per Tick required!)";
    }

    private static String weight(
        GTMaterialGameplayRegistry.MaterialGameplay material,
        long wallUnits
    ) {
        if (material == null) {
            return "0.0";
        }
        return Double.toString(material.weightKilograms(
            wallUnits * GTMaterialGameplayRegistry.MATERIAL_UNIT
        ));
    }

    private static long maximumTemperature(
        GTMaterialGameplayRegistry.MaterialGameplay material,
        double heatResistanceBonus
    ) {
        double meltingPoint = material == null ? 2046.0D : material.meltingKelvin();
        return (long) (meltingPoint * heatResistanceBonus);
    }

    private static void harvest(List<Component> tooltip, int harvestLevel) {
        MutableComponent line = Component.literal("Tool to Harvest: ")
            .withStyle(ChatFormatting.DARK_GRAY);
        String suffix = switch (harvestLevel) {
            case 2 -> " (2, Iron)";
            case 3 -> " (3, Diamond)";
            case 4 -> " (4, Netherite)";
            case 5 -> " (5, Adamantium)";
            default -> harvestLevel > 5 ? " (" + harvestLevel + ", Infinity)" : "";
        };
        line.append(Component.literal("Pickaxe" + suffix).withStyle(ChatFormatting.WHITE));
        tooltip.add(line);
    }

    private static void line(List<Component> tooltip, ChatFormatting color, String text) {
        tooltip.add(Component.literal(text).withStyle(color));
    }
}
