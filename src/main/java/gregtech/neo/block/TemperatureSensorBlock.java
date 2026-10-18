package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TemperatureSensorBlock extends BaseEntityBlock {
    public static final MapCodec<TemperatureSensorBlock> CODEC = simpleCodec(TemperatureSensorBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape NORTH_SHAPE = box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape SOUTH_SHAPE = box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape WEST_SHAPE = box(0, 0, 0, 2, 16, 16);
    private static final VoxelShape EAST_SHAPE = box(14, 0, 0, 16, 16, 16);
    private static final VoxelShape DOWN_SHAPE = box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape UP_SHAPE = box(0, 14, 0, 16, 16, 16);

    public TemperatureSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return shape(state);
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return shape(state);
    }

    private static VoxelShape shape(BlockState state) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            case DOWN -> DOWN_SHAPE;
            case UP -> UP_SHAPE;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TemperatureSensorBlockEntity(pos, state);
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
        if (!level.isClientSide()
            && stack.getItem() instanceof GT6ToolItem
            && level.getBlockEntity(pos) instanceof TemperatureSensorBlockEntity sensor) {
            InWorldMessages.send(player, sensor.describe());
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hit
    ) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TemperatureSensorBlockEntity sensor) {
            InWorldMessages.send(player, sensor.describe());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TemperatureSensorBlockEntity sensor
            ? sensor.analogSignal()
            : 0;
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
            if (blockEntity instanceof TemperatureSensorBlockEntity sensor) {
                sensor.tick();
            }
        };
    }
}
