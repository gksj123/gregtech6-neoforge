package gregtech.neo.block;

import gregtech.neo.fluid.GTFluidUtil;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class SteamPipeBlockEntity extends BlockEntity {
    private static final int CAPACITY = 16_000;
    private final FluidTank tank = new FluidTank(CAPACITY, GTFluidUtil::isSteam) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public SteamPipeBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.STEAM_PIPE.get(), pos, state);
    }

    public IFluidHandler fluidHandler(Direction side) {
        return tank;
    }

    public void tick() {
        if (level == null || level.isClientSide() || tank.isEmpty()) {
            return;
        }
        FluidStack offered = tank.getFluid().copy();
        int accepted = SteamNetwork.pushFromPipe(level, worldPosition, offered, false);
        if (accepted > 0) {
            tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    public int fluidAmount() {
        return tank.getFluidAmount();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("gt.tank.0"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("gt.tank.0", tank.writeToNBT(registries, new CompoundTag()));
    }
}
