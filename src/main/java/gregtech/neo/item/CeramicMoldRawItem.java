package gregtech.neo.item;

import gregtech.neo.casting.CastingPart;
import gregtech.neo.casting.GTMoldRegistry;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * GT6 1.7.10: gregtech/loaders/b/Loader_MultiTileEntities.java:391-420.
 */
public final class CeramicMoldRawItem extends Item {
    public CeramicMoldRawItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, int mask, int sourceLine) {
        ItemStack stack = new ItemStack(item);
        stack.set(
            GTDataComponents.CASTING_PART.get(),
            new CastingPart("ceramic", sourceLine, mask, false)
        );
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        CastingPart part = stack.get(GTDataComponents.CASTING_PART.get());
        if (part == null) {
            return Component.literal("Raw Ceramic Mold");
        }
        GTMoldRegistry.MaskLookup shape = GTMoldRegistry.lookup(part.shapeMask());
        return Component.literal("Raw Ceramic " + prettyShape(shape.sourceShape()) + " Mold");
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        CastingPart part = stack.get(GTDataComponents.CASTING_PART.get());
        if (part == null) {
            return;
        }
        GTMoldRegistry.MaskLookup shape = GTMoldRegistry.lookup(part.shapeMask());
        GT6Tooltip.header(tooltip, "RAW CERAMIC MOLD");
        GT6Tooltip.line(tooltip, ChatFormatting.AQUA, shape.sourceShape());
        GT6Tooltip.line(tooltip, ChatFormatting.GRAY, "Furnace smelting preserves the carved shape");
        GT6Tooltip.nbt(
            tooltip,
            "gregtech:casting_part={mask:" + part.shapeMask() + ",wall_material:\"ceramic\"}"
        );
    }

    private static String prettyShape(String sourceShape) {
        String[] words = sourceShape.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }
        return result.isEmpty() ? "Ceramic" : result.toString();
    }
}
