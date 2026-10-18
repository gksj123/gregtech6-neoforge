package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BasicCrusherBlock extends BaseEntityBlock {
    public static final MapCodec<BasicCrusherBlock> CODEC = simpleCodec(BasicCrusherBlock::new);

    public BasicCrusherBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BasicCrusherBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof BasicCrusherBlockEntity crusher)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof GT6ToolItem tool) {
            switch (tool.kind()) {
                case SCREWDRIVER -> {
                    crusher.toggleActive();
                    InWorldMessages.send(player, Component.translatable(
                        crusher.isActive() ? "message.gregtech.machine.enabled" : "message.gregtech.machine.disabled"
                    ));
                    return ItemInteractionResult.SUCCESS;
                }
                case MAGNIFYING_GLASS, ELECTROMETER -> {
                    crusher.describe(player, true);
                    return ItemInteractionResult.SUCCESS;
                }
                case SOFT_HAMMER -> {
                    InWorldMessages.send(player, Component.translatable(
                        "message.gregtech.energy.accepts",
                        "KU",
                        16,
                        64
                    ));
                    return ItemInteractionResult.SUCCESS;
                }
                default -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }
        ItemStack remainder = crusher.insertItem(stack);
        if (remainder.getCount() != stack.getCount()) {
            player.setItemInHand(hand, remainder);
            InWorldMessages.send(player, InWorldMessages.status("item_inserted"));
        } else {
            crusher.describe(player, false);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hit
    ) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BasicCrusherBlockEntity crusher) {
            ItemStack output = crusher.extractOutput();
            if (!output.isEmpty()) {
                player.getInventory().placeItemBackInInventory(output);
                InWorldMessages.send(player, InWorldMessages.status("item_extracted"));
            } else {
                crusher.describe(player, false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        return (levelToTick, pos, stateToTick, blockEntity) -> {
            if (blockEntity instanceof BasicCrusherBlockEntity crusher) {
                crusher.tick();
            }
        };
    }
}
