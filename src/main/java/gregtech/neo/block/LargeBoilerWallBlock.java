package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.LargeBoilerWallItem;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * GT6 1.7.10: gregtech/loaders/b/Loader_MultiTileEntities.java:1159-1165.
 */
public final class LargeBoilerWallBlock extends BaseEntityBlock {
    public static final MapCodec<LargeBoilerWallBlock> CODEC = simpleCodec(LargeBoilerWallBlock::new);

    public LargeBoilerWallBlock(Properties properties) {
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
        return new LargeBoilerWallBlockEntity(pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()
            && level.getBlockEntity(pos) instanceof LargeBoilerWallBlockEntity wall
            && !wall.material().isBlank()
            && level instanceof ServerLevel serverLevel) {
            Block.popResource(serverLevel, pos, LargeBoilerWallItem.create(
                GTItems.LARGE_BOILER_WALL.get(),
                wall.material()
            ));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
