package gregtech.neo.item;

import gregtech.neo.block.FaucetBlockEntity;
import gregtech.neo.casting.CastingPart;
import gregtech.neo.material.GTMaterial;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class FaucetItem extends BlockItem {
    public FaucetItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(Item item, CastingPart part) {
        ItemStack stack = new ItemStack(item);
        stack.set(GTDataComponents.CASTING_PART.get(), part);
        return stack;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        if (stack.get(GTDataComponents.CASTING_PART.get()) == null) {
            stack.set(
                GTDataComponents.CASTING_PART.get(),
                new CastingPart("stone", 1700, 0, false)
            );
        }
        return stack;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(
        BlockPos pos,
        Level level,
        Player player,
        ItemStack stack,
        BlockState state
    ) {
        CastingPart part = stack.get(GTDataComponents.CASTING_PART.get());
        if (part == null || !(level.getBlockEntity(pos) instanceof FaucetBlockEntity faucet)) {
            return false;
        }
        faucet.configure(part);
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        CastingPart part = stack.get(GTDataComponents.CASTING_PART.get());
        GTMaterial material = part == null ? null : GTMaterialRegistry.material(part.wallMaterial());
        String wall = material == null
            ? part == null ? "Unassigned" : part.wallMaterial()
            : material.prettyName();
        return Component.literal("Crucible Faucet (" + wall + ")");
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
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(part.wallMaterial());
        long limit = gameplay == null ? 1250 : (long) (gameplay.meltingKelvin() * 1.25D);
        GT6Tooltip.header(tooltip, "CRUCIBLE FAUCET");
        GT6Tooltip.source(tooltip, "Crucibles Faucets", part.sourceLine());
        GT6Tooltip.line(tooltip, ChatFormatting.AQUA,
            "WALL: " + part.wallMaterial() + " // LIMIT: " + limit + "K");
        if (part.acidProof()) {
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD, "ACID: PROOF");
        }
        GT6Tooltip.line(tooltip, ChatFormatting.GREEN,
            "CTRL: empty hand pour // redstone pulse pour");
        GT6Tooltip.line(tooltip, ChatFormatting.GRAY,
            "TOOL: monkey wrench automatic // soft hammer redstone");
        GT6Tooltip.nbt(tooltip,
            "gregtech:casting_part={wall:\"" + part.wallMaterial()
                + "\",line:" + part.sourceLine()
                + ",acid:" + part.acidProof() + "}, mode:\"REDSTONE\"");
    }
}
