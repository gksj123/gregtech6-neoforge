package gregtech.neo.block;

import gregtech.neo.core.EnergyPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import gregtech.neo.registry.GTBlockEntities;

public final class MachinePortBlockEntity extends BlockEntity implements EnergyReceiver {
    public MachinePortBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.MACHINE_PORT.get(), pos, state);
    }

    public LargeCrusherBlockEntity controller() {
        return level == null ? null : MachinePortBlock.findController(level, worldPosition);
    }

    public IItemHandler itemHandler() {
        LargeCrusherBlockEntity controller = controller();
        if (controller == null) {
            return null;
        }
        MachinePortMode mode = getBlockState().getValue(MachinePortBlock.MODE);
        if (mode.mode().itemInput()) {
            return controller.inputItemHandler();
        }
        if (mode.mode().itemOutput()) {
            return controller.outputItemHandler();
        }
        return null;
    }

    @Override
    public long receivePacket(EnergyPacket packet, Direction side, boolean simulate) {
        if (!MachinePortBlock.mode(getBlockState()).energyInput()) {
            return 0;
        }
        LargeCrusherBlockEntity controller = controller();
        return controller == null ? 0 : controller.receivePacket(packet, side, simulate);
    }
}
