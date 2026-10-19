package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.item.SteamTurbineItem;
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

public final class SteamTurbineBlock extends BaseEntityBlock {
    public static final MapCodec<SteamTurbineBlock> CODEC = simpleCodec(SteamTurbineBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public SteamTurbineBlock(Properties properties) {
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
        return new SteamTurbineBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof SteamTurbineBlockEntity turbine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof GT6ToolItem tool
            && tool.kind() == GT6ToolItem.ToolKind.SOFT_HAMMER) {
            turbine.toggleStopped();
            InWorldMessages.send(player, turbine.describe());
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof GT6ToolItem tool
            && (tool.kind() == GT6ToolItem.ToolKind.MAGNIFYING_GLASS
                || tool.kind() == GT6ToolItem.ToolKind.ELECTROMETER)) {
            InWorldMessages.send(player, turbine.describe());
            return ItemInteractionResult.SUCCESS;
        }
        InWorldMessages.send(player, turbine.describe());
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()
            && level.getBlockEntity(pos) instanceof SteamTurbineBlockEntity turbine
            && turbine.definition() != null && level instanceof ServerLevel serverLevel) {
            Block.popResource(serverLevel, pos, SteamTurbineItem.create(
                GTItems.STEAM_TURBINE.get(),
                new MachinePart(turbine.definition().line(), turbine.definition().category())
            ));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (!level.isClientSide()
            && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof SteamTurbineBlockEntity turbine
            && turbine.definition() != null) {
            Block.popResource(serverLevel, pos, SteamTurbineItem.create(
                GTItems.STEAM_TURBINE.get(),
                new MachinePart(turbine.definition().line(), turbine.definition().category())
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
            if (blockEntity instanceof SteamTurbineBlockEntity turbine) {
                turbine.tick();
            }
        };
    }
}
