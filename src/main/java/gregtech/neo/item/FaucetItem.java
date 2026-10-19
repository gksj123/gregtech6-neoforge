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

/**
 * GT6 1.7.10: gregtech/loaders/b/Loader_MultiTileEntities.java:299-341 and
 * gregtech/tileentity/tools/MultiTileEntityFaucet.java:72-78.
 */
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
        String wallMaterial = part == null || part.wallMaterial() == null
            ? ""
            : part.wallMaterial();
        if (wallMaterial.isBlank()) {
            return Component.literal("Crucible Faucet (Unassigned)");
        }
        GTMaterial material = GTMaterialRegistry.material(wallMaterial);
        String wall = material == null ? wallMaterial : material.prettyName();
        return Component.literal("Crucible Faucet (" + wall + ")");
    }

    /**
     * GT6 1.7.10: MultiTileEntityFaucet.java:72-78. Keep the source order:
     * no-GUI hint, acid-proof hint, meltdown limit, monkey-wrench hint.
     */
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
        String wallMaterial = part.wallMaterial() == null ? "" : part.wallMaterial();
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(wallMaterial);
        long limit = gameplay == null ? 1250L : (long) (gameplay.meltingKelvin() * 1.25D);

        tooltip.add(Component.literal(
            "No GUI, click to interact"
        ).withStyle(ChatFormatting.GOLD));
        if (part.acidProof()) {
            tooltip.add(Component.literal("Can handle Acids").withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.literal(
            "Melts down when stored Heat is too much! (" + limit + " K)"
        ).withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.literal(
            "Use Monkey Wrench to toggle Auto-Inputs"
        ).withStyle(ChatFormatting.DARK_GRAY));
    }
}
