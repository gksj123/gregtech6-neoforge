package gregtech.neo.core;

import java.util.List;

public record CrusherRecipe(
    String inputId,
    int inputCount,
    String outputId,
    int outputCount,
    long durationTicks,
    long euPerTick,
    long minimumVoltage
) {
    public static final List<CrusherRecipe> BUILTIN = List.of(
        new CrusherRecipe("minecraft:cobblestone", 1, "minecraft:gravel", 1, 80, 8, 32),
        new CrusherRecipe("minecraft:gravel", 1, "minecraft:sand", 1, 100, 8, 32),
        new CrusherRecipe("minecraft:stone", 1, "minecraft:cobblestone", 1, 100, 8, 32),
        new CrusherRecipe("minecraft:deepslate", 1, "minecraft:cobbled_deepslate", 1, 120, 16, 64)
    );

    public long totalEnergy() {
        return EnergyPacket.saturatedMultiply(durationTicks, euPerTick);
    }

    public static CrusherRecipe find(String itemId) {
        for (CrusherRecipe recipe : BUILTIN) {
            if (recipe.inputId.equals(itemId)) {
                return recipe;
            }
        }
        return null;
    }
}
