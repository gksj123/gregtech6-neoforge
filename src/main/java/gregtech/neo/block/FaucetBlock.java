package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.casting.CastingPart;
import gregtech.neo.item.FaucetItem;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.registry.GTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FaucetBlock extends BaseEntityBlock {
    public static final MapCodec<FaucetBlock> CODEC = simpleCodec(FaucetBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public FaucetBlock(Properties properties) {
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal()
            ? clicked.getOpposite()
            : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return shapeFor(state.getValue(FACING));
    }

    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case EAST -> box(0, 2, 5, 12, 10, 11);
            case SOUTH -> box(5, 2, 0, 11, 10, 12);
            case WEST -> box(4, 2, 5, 16, 10, 11);
            default -> box(5, 2, 4, 11, 10, 16);
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FaucetBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof FaucetBlockEntity faucet)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof GT6ToolItem tool) {
            if (tool.kind() == GT6ToolItem.ToolKind.MONKEY_WRENCH) {
                faucet.toggleAutomatic();
            } else if (tool.kind() == GT6ToolItem.ToolKind.SOFT_HAMMER) {
                faucet.resetAutomation();
            } else if (tool.kind() == GT6ToolItem.ToolKind.WRENCH
                || tool.kind() == GT6ToolItem.ToolKind.SCREWDRIVER) {
                Direction facing = state.getValue(FACING);
                level.setBlockAndUpdate(pos, state.setValue(FACING, facing.getClockWise()));
            } else if (tool.kind() == GT6ToolItem.ToolKind.THERMOMETER
                || tool.kind() == GT6ToolItem.ToolKind.MAGNIFYING_GLASS
                || tool.kind() == GT6ToolItem.ToolKind.ELECTROMETER) {
                InWorldMessages.send(player, faucet.describe());
                return ItemInteractionResult.SUCCESS;
            } else {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            InWorldMessages.send(player, faucet.describe());
            return ItemInteractionResult.SUCCESS;
        }
        faucet.tryPour();
        InWorldMessages.send(player, faucet.describe());
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FaucetBlockEntity faucet) {
            faucet.tryPour();
            InWorldMessages.send(player, faucet.describe());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()
            && level.getBlockEntity(pos) instanceof FaucetBlockEntity faucet
            && level instanceof ServerLevel serverLevel) {
            Block.popResource(serverLevel, pos, FaucetItem.create(
                GTItems.FAUCET.get(),
                new CastingPart(faucet.wallMaterial(), faucet.sourceLine(), 0, faucet.acidProof())
            ));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (!level.isClientSide()
            && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof FaucetBlockEntity faucet) {
            Block.popResource(serverLevel, pos, FaucetItem.create(
                GTItems.FAUCET.get(),
                new CastingPart(faucet.wallMaterial(), faucet.sourceLine(), 0, faucet.acidProof())
            ));
        }
        super.wasExploded(level, pos, explosion);
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
            if (blockEntity instanceof FaucetBlockEntity faucet) {
                faucet.tick();
            }
        };
    }
}
