package gregtech.neo.item;

import gregtech.neo.material.GTMaterial;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialPrefix;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class MaterialPartItem extends Item {
    public MaterialPartItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, String materialSlug, String prefixId, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(GTDataComponents.MATERIAL_PART.get(), new MaterialPart(materialSlug, prefixId));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        if (part == null) {
            return Component.translatable("item.gregtech.unassigned_material_part");
        }
        GTMaterial material = GTMaterialRegistry.material(part.material());
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(part.prefix());
        String materialName = material == null ? part.material() : material.prettyName();
        String formName = prefix == null ? part.prefix() : prefix.prettyCategory();
        return Component.translatable("item.gregtech.material_part.name", materialName, formName);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        if (part == null) {
            return;
        }
        GTMaterial material = GTMaterialRegistry.material(part.material());
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(part.prefix());
        if (material != null) {
            GT6Tooltip.header(tooltip, "MATERIAL FORM");
            GT6Tooltip.line(tooltip, ChatFormatting.YELLOW,
                "MAT: " + material.name()
                    + (material.symbol().isBlank() ? "" : " [" + material.symbol() + "]")
                    + " // ID " + material.id());
        }
        if (prefix != null) {
            GT6Tooltip.line(tooltip, ChatFormatting.AQUA,
                "FORM: " + prefix.id()
                    + " // " + prefix.category()
                    + " // " + prefix.amount() + "U");
        }
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(part.material());
        if (gameplay != null) {
            GT6Tooltip.line(tooltip, ChatFormatting.RED,
                "THERM: MP " + format(gameplay.meltingKelvin())
                    + "K // BP " + format(gameplay.boilingKelvin())
                    + "K // DENS " + format(gameplay.densityGramsPerCubicCentimeter())
                    + "g/cm3");
            if (gameplay.toolTypes() > 0) {
                GT6Tooltip.line(tooltip, ChatFormatting.GREEN,
                    "TOOL: " + gameplay.toolTypes()
                        + " types // SPD " + format(gameplay.toolSpeed())
                        + " // DUR " + gameplay.toolDurability()
                        + " // Q " + gameplay.toolQuality());
            }
            GT6Tooltip.source(tooltip, "MT.java", gameplay.sourceLine());
            GT6Tooltip.nbt(tooltip,
                "gregtech:material_part={material:\"" + part.material()
                    + "\",prefix:\"" + part.prefix() + "\"}"
                    + ", heat:\"" + gameplay.heatConfidence() + "\"");
        }
        GT6Tooltip.line(tooltip, ChatFormatting.GRAY,
            "CTRL: insert into crucibles or machine input slots");
    }

    private static String format(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001
            ? Long.toString(Math.round(value))
            : String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
