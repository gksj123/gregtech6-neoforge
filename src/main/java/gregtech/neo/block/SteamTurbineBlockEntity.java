package gregtech.neo.block;

import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import gregtech.neo.fluid.FluidHandlerView;
import gregtech.neo.fluid.GTFluidUtil;
import gregtech.neo.machine.GTMachineDefinition;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;

/**
 * GT6 1.7.10: gregtech/tileentity/energy/converters/MultiTileEntityTurbineSteam.java.
 */
public final class SteamTurbineBlockEntity extends BlockEntity {
    private static final long STEAM_PER_EU = 2;
    private static final int STEAM_PER_WATER = 200;

    private MachinePart part;
    private GTMachineDefinition definition;
    private long inputSize = 48;
    private long outputSize = 16;
    private long energyStored;
    private long energyProducedNextTick;
    private long steamCounter;
    private int overloadTicks;
    private boolean active;
    private boolean stopped;
    private final FluidTank steamTank = new FluidTank(
        384,
        GTFluidUtil::isSteam
    ) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.STEAM_TURBINE.get(), pos, state);
    }

    public void setPart(MachinePart part) {
        this.part = part;
        definition = GTMachineRegistry.find(part.category(), part.sourceLine());
        if (definition != null) {
            inputSize = definition.inputValue() > 0
                ? definition.inputValue()
                : Math.max(1, definition.input() * 2L);
            outputSize = definition.outputValue() > 0
                ? definition.outputValue()
                : Math.max(1, definition.output());
        }
        steamTank.setCapacity((int) Math.min(
            Integer.MAX_VALUE,
            Math.max(1, inputSize) * 8L
        ));
        energyStored = 0;
        energyProducedNextTick = 0;
        steamCounter = 0;
        overloadTicks = 0;
        active = false;
        stopped = false;
        setChanged();
    }

    public void tick() {
        if (level == null || level.isClientSide() || definition == null) {
            return;
        }

        if (energyProducedNextTick > 0) {
            energyStored = EnergyPacket.saturatedAdd(energyStored, energyProducedNextTick);
            energyProducedNextTick = 0;
        } else if (!stopped && steamTank.getFluidAmount() >= Math.max(1, inputSize)) {
            int tSteam = steamTank.getFluidAmount();
            steamTank.setFluid(FluidStack.EMPTY);
            long converted = tSteam / STEAM_PER_EU;
            energyStored = EnergyPacket.saturatedAdd(energyStored, converted);
            energyProducedNextTick = converted;
            steamCounter += tSteam;
            outputDistilledWater();
        }

        long tOutput = units(energyStored, inputSize, outputSize);
        boolean canEmit = tOutput >= Math.max(1, outputSize / 2);
        active = canEmit;
        long outputMax = outputSize * 2;
        if (tOutput > outputMax) {
            overloadTicks++;
            energyStored = 0;
            if (overloadTicks > 100 && level != null) {
                level.explode(
                    null,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    1.5F,
                    Level.ExplosionInteraction.BLOCK
                );
            }
            return;
        }
        overloadTicks = 0;

        if (canEmit) {
            Direction output = getBlockState().getValue(SteamTurbineBlock.FACING);
            EnergyPacket packet = new EnergyPacket(EnergyKind.ROTATION, tOutput, 1);
            MechanicalEnergyNetwork.Result result = MechanicalEnergyNetwork.distribute(
                level,
                worldPosition,
                output,
                packet,
                false
            );
            if (result.acceptedAmperes() > 0) {
                long consumed = units(tOutput * tOutput, outputSize, inputSize);
                energyStored = Math.max(0, energyStored - consumed);
            }
        }

        if (level.getGameTime() % 20 == 0) {
            setChanged();
        }
    }

    private void outputDistilledWater() {
        int distilled = (int) Math.min(
            Integer.MAX_VALUE,
            steamCounter / STEAM_PER_WATER
        );
        if (distilled <= 0) {
            return;
        }
        steamCounter %= STEAM_PER_WATER;
        GTFluidUtil.fillAll(
            level,
            worldPosition,
            GTFluidUtil.distilledWater(distilled),
            outputSides(),
            false
        );
    }

    private List<Direction> outputSides() {
        Direction facing = getBlockState().getValue(SteamTurbineBlock.FACING);
        List<Direction> sides = new ArrayList<>(4);
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != facing.getAxis()) {
                sides.add(direction);
            }
        }
        return sides;
    }

    public IFluidHandler fluidHandler(Direction side) {
        if (stopped) {
            return null;
        }
        Direction facing = getBlockState().getValue(SteamTurbineBlock.FACING);
        return side == facing.getOpposite()
            ? new FluidHandlerView(steamTank, true, false)
            : null;
    }

    public void toggleStopped() {
        stopped = !stopped;
        setChanged();
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean isActive() {
        return active;
    }

    public long steamAmount() {
        return steamTank.getFluidAmount();
    }

    public long steamCapacity() {
        return steamTank.getCapacity();
    }

    public long energyStored() {
        return energyStored;
    }

    public long inputSize() {
        return inputSize;
    }

    public long outputSize() {
        return outputSize;
    }

    public long steamCounter() {
        return steamCounter;
    }

    public int fillSteamForTesting(int amount) {
        return steamTank.fill(
            GTFluidUtil.steam(amount),
            IFluidHandler.FluidAction.EXECUTE
        );
    }

    public Component describe() {
        if (definition == null) {
            return Component.translatable("block.gregtech.steam_turbine.unassigned");
        }
        return Component.translatable(
            "message.gregtech.steam_turbine.status",
            definition.display_name(),
            outputSize,
            steamTank.getFluidAmount(),
            steamTank.getCapacity(),
            active
        );
    }

    public GTMachineDefinition definition() {
        return definition;
    }

    private static long units(long value, long denominator, long multiplier) {
        if (denominator <= 0) {
            return 0;
        }
        return (value * multiplier) / denominator;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        steamCounter = tag.contains("gt.energy.su")
            ? tag.getLong("gt.energy.su")
            : tag.getLong("steam_counter");
        energyProducedNextTick = tag.contains("gt.output.su")
            ? tag.getLong("gt.output.su")
            : tag.getLong("energy_next");
        energyStored = tag.getLong("energy");
        inputSize = tag.getLong("input_size");
        outputSize = tag.getLong("output_size");
        active = tag.getBoolean("active");
        stopped = tag.contains("gt.stopped")
            ? tag.getBoolean("gt.stopped")
            : tag.getBoolean("stopped");
        overloadTicks = tag.getInt("overload_ticks");
        if (tag.contains("gt.tank.0")) {
            steamTank.readFromNBT(registries, tag.getCompound("gt.tank.0"));
        } else if (tag.getLong("steam") > 0) {
            steamTank.setFluid(GTFluidUtil.steam((int) tag.getLong("steam")));
        }
        if (tag.contains("source_line")) {
            part = new MachinePart(tag.getInt("source_line"), tag.getString("category"));
            definition = GTMachineRegistry.find(part.category(), part.sourceLine());
            if (definition != null) {
                if (inputSize == 0) {
                    inputSize = definition.inputValue();
                }
                if (outputSize == 0) {
                    outputSize = definition.outputValue();
                }
            }
        }
        steamTank.setCapacity((int) Math.min(
            Integer.MAX_VALUE,
            Math.max(1, inputSize) * 8L
        ));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("energy", energyStored);
        tag.putLong("input_size", inputSize);
        tag.putLong("output_size", outputSize);
        tag.putLong("gt.energy.su", steamCounter);
        tag.putLong("gt.output.su", energyProducedNextTick);
        tag.putBoolean("active", active);
        tag.putBoolean("gt.stopped", stopped);
        tag.putInt("overload_ticks", overloadTicks);
        tag.put("gt.tank.0", steamTank.writeToNBT(registries, new CompoundTag()));
        if (part != null) {
            tag.putInt("source_line", part.sourceLine());
            tag.putString("category", part.category());
        }
    }
}
