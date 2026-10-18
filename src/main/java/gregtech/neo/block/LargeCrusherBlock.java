package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class LargeCrusherBlock extends BaseEntityBlock {
    public static final MapCodec<LargeCrusherBlock> CODEC = simpleCodec(LargeCrusherBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public LargeCrusherBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(FORMED, false);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LargeCrusherBlockEntity(pos, state);
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
        if (!(stack.getItem() instanceof GT6ToolItem tool)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof LargeCrusherBlockEntity crusher)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        switch (tool.kind()) {
            case WRENCH -> {
                Direction rotated = state.getValue(FACING).getClockWise();
                level.setBlock(pos, state.setValue(FACING, rotated), Block.UPDATE_ALL);
                crusher.forceStructureCheck();
                InWorldMessages.send(player, InWorldMessages.status("rotated"));
                return ItemInteractionResult.SUCCESS;
            }
            case SCREWDRIVER -> {
                crusher.toggleActive();
                InWorldMessages.send(player, Component.translatable(
                    crusher.isActive() ? "message.gregtech.machine.enabled" : "message.gregtech.machine.disabled"
                ));
                return ItemInteractionResult.SUCCESS;
            }
            case SOFT_HAMMER -> {
                crusher.forceStructureCheck();
                InWorldMessages.send(player, crusher.isFormed()
                    ? InWorldMessages.status("structure_formed")
                    : InWorldMessages.status("structure_missing"));
                return ItemInteractionResult.SUCCESS;
            }
            case MAGNIFYING_GLASS, ELECTROMETER -> {
                crusher.describe(player, true);
                return ItemInteractionResult.SUCCESS;
            }
            default -> {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hit
    ) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LargeCrusherBlockEntity crusher) {
            crusher.describe(player, false);
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
            if (blockEntity instanceof LargeCrusherBlockEntity crusher) {
                crusher.tick();
            }
        };
    }
}
