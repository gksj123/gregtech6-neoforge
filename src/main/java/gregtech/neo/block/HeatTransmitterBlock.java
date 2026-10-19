package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * GT6 1.7.10: gregtech/loaders/b/Loader_MultiTileEntities.java:1176.
 */
public final class HeatTransmitterBlock extends BaseEntityBlock {
    public static final MapCodec<HeatTransmitterBlock> CODEC = simpleCodec(HeatTransmitterBlock::new);

    public HeatTransmitterBlock(Properties properties) {
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
        return new HeatTransmitterBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hit
    ) {
        if (!level.isClientSide()
            && level.getBlockEntity(pos) instanceof HeatTransmitterBlockEntity transmitter
            && transmitter.controller() != null) {
            InWorldMessages.send(player, transmitter.controller().describe());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && level instanceof ServerLevel serverLevel) {
            Block.popResource(serverLevel, pos, state.getBlock().asItem().getDefaultInstance());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
