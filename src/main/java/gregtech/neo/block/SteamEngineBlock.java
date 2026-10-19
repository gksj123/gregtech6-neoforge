package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.item.SteamEngineItem;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class SteamEngineBlock extends BaseEntityBlock {
    public static final MapCodec<SteamEngineBlock> CODEC = simpleCodec(SteamEngineBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public SteamEngineBlock(Properties properties) {
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
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamEngineBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof SteamEngineBlockEntity engine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof GT6ToolItem tool) {
            switch (tool.kind()) {
                case SOFT_HAMMER -> {
                    engine.toggleStopped();
                    InWorldMessages.send(player, engine.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case MAGNIFYING_GLASS, ELECTROMETER -> {
                    InWorldMessages.send(player, engine.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                default -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }
        InWorldMessages.send(player, engine.describe());
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()
            && level.getBlockEntity(pos) instanceof SteamEngineBlockEntity engine
            && engine.definition() != null && level instanceof ServerLevel serverLevel) {
            Block.popResource(serverLevel, pos, SteamEngineItem.create(
                GTItems.STEAM_ENGINE.get(),
                new MachinePart(engine.definition().line(), engine.definition().category())
            ));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (!level.isClientSide()
            && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof SteamEngineBlockEntity engine
            && engine.definition() != null) {
            Block.popResource(serverLevel, pos, SteamEngineItem.create(
                GTItems.STEAM_ENGINE.get(),
                new MachinePart(engine.definition().line(), engine.definition().category())
            ));
        }
        super.wasExploded(level, pos, explosion);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof SteamEngineBlockEntity engine) {
                engine.tick();
            }
        };
    }
}
