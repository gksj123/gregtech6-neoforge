package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.MaterialBlockItem;
import gregtech.neo.material.GTMaterialBlocks;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialStorageBlock extends BaseEntityBlock {
    public static final MapCodec<MaterialStorageBlock> CODEC = simpleCodec(properties -> new MaterialStorageBlock(properties, "blockSolid"));

    private final String prefix;

    public MaterialStorageBlock(Properties properties, String prefix) {
        super(properties);
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
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
        return new MaterialStorageBlockEntity(pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()
            && level.getBlockEntity(pos) instanceof MaterialStorageBlockEntity storage) {
            MaterialPart part = storage.part();
            if (part != null && level instanceof ServerLevel serverLevel) {
                ItemStack drop = MaterialBlockItem.create(
                    GTMaterialBlocks.ITEMS.get(prefix).get(),
                    part.material(),
                    part.prefix(),
                    1
                );
                Block.popResource(serverLevel, pos, drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
