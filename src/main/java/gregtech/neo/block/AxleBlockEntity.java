package gregtech.neo.block;

import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AxleBlockEntity extends BlockEntity {
    public AxleBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.AXLE.get(), pos, state);
    }
}
