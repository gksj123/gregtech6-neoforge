package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.item.SteamBoilerItem;
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
import net.minecraft.world.item.Items;
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

public final class SteamBoilerBlock extends BaseEntityBlock {
    public static final MapCodec<SteamBoilerBlock> CODEC = simpleCodec(SteamBoilerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public SteamBoilerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(LIT, false);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamBoilerBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof SteamBoilerBlockEntity boiler)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(Items.WATER_BUCKET)) {
            if (boiler.addWater(1000)) {
                if (!player.isCreative()) {
                    player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                }
            }
            InWorldMessages.send(player, boiler.describe());
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof GT6ToolItem tool) {
            switch (tool.kind()) {
                case CHISEL -> {
                    boiler.descale(player);
                    InWorldMessages.send(player, boiler.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case THERMOMETER, MAGNIFYING_GLASS, ELECTROMETER -> {
                    InWorldMessages.send(player, boiler.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                default -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }
        InWorldMessages.send(player, boiler.describe());
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected void entityInside(
        BlockState state,
        Level level,
        BlockPos pos,
        net.minecraft.world.entity.Entity entity
    ) {
        if (!level.isClientSide()
            && entity instanceof net.minecraft.world.entity.LivingEntity living
            && level.getBlockEntity(pos) instanceof SteamBoilerBlockEntity boiler) {
            boiler.damageTouchingEntity(living);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()
            && level.getBlockEntity(pos) instanceof SteamBoilerBlockEntity boiler
            && boiler.definition() != null && level instanceof ServerLevel serverLevel) {
            boiler.onBreak();
            Block.popResource(serverLevel, pos, SteamBoilerItem.create(
                GTItems.STEAM_BOILER.get(),
                new MachinePart(boiler.definition().line(), boiler.definition().category())
            ));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (!level.isClientSide()
            && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof SteamBoilerBlockEntity boiler
            && boiler.definition() != null) {
            Block.popResource(serverLevel, pos, SteamBoilerItem.create(
                GTItems.STEAM_BOILER.get(),
                new MachinePart(boiler.definition().line(), boiler.definition().category())
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
            if (blockEntity instanceof SteamBoilerBlockEntity boiler) {
                boiler.tick();
            }
        };
    }
}
