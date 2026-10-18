package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.BasicMachineItem;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTDataComponents;
import gregtech.neo.registry.GTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BasicMachineBlock extends BaseEntityBlock {
    public static final MapCodec<BasicMachineBlock> CODEC = simpleCodec(BasicMachineBlock::new);

    public BasicMachineBlock(Properties properties) {
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
        return new BasicMachineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof BasicMachineBlockEntity machine) {
                machine.tick();
            }
        };
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
        if (!(level.getBlockEntity(pos) instanceof BasicMachineBlockEntity machine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof gregtech.neo.item.GT6ToolItem tool) {
            switch (tool.kind()) {
                case SCREWDRIVER -> {
                    machine.toggle();
                    InWorldMessages.send(player, machine.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case MAGNIFYING_GLASS, ELECTROMETER -> {
                    InWorldMessages.send(player, machine.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case SOFT_HAMMER -> {
                    machine.reset();
                    InWorldMessages.send(player, InWorldMessages.status("reset"));
                    return ItemInteractionResult.SUCCESS;
                }
                default -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }
        ItemStack remainder = machine.insertItem(stack);
        if (remainder.getCount() != stack.getCount()) {
            player.setItemInHand(hand, remainder);
            InWorldMessages.send(player, InWorldMessages.status("item_inserted"));
        } else {
            InWorldMessages.send(player, machine.describe());
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BasicMachineBlockEntity machine) {
            ItemStack output = machine.extractOutput();
            if (!output.isEmpty()) {
                player.getInventory().placeItemBackInInventory(output);
                InWorldMessages.send(player, InWorldMessages.status("item_extracted"));
            } else {
                InWorldMessages.send(player, machine.describe());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()
            && level.getBlockEntity(pos) instanceof BasicMachineBlockEntity machine
            && machine.part() != null && level instanceof ServerLevel serverLevel) {
            Block.popResource(serverLevel, pos, BasicMachineItem.create(
                GTItems.BASIC_MACHINE.get(),
                machine.part()
            ));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
