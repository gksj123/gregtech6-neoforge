package gregtech.neo.block;

import gregtech.neo.core.EnergyPacket;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * GT6 1.7.10: gregtech/loaders/b/Loader_MultiTileEntities.java:1176.
 */
public final class HeatTransmitterBlockEntity extends BlockEntity implements EnergyReceiver {
    public HeatTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.HEAT_TRANSMITTER.get(), pos, state);
    }

    public LargeBoilerBlockEntity controller() {
        if (level == null) {
            return null;
        }
        for (BlockPos candidate : BlockPos.betweenClosed(
            worldPosition.offset(-3, -3, -3),
            worldPosition.offset(3, 3, 3)
        )) {
            if (level.getBlockEntity(candidate) instanceof LargeBoilerBlockEntity boiler
                && boiler.isPart(worldPosition)) {
                return boiler;
            }
        }
        return null;
    }

    @Override
    public long receivePacket(EnergyPacket packet, Direction side, boolean simulate) {
        LargeBoilerBlockEntity boiler = controller();
        if (boiler == null || !boiler.isFormed()) {
            return 0;
        }
        return boiler.receivePacket(packet, side, simulate);
    }
}
