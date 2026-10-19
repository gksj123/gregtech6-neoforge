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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;

/**
 * GT6 1.7.10: gregtech/tileentity/energy/converters/MultiTileEntityEngineSteam.java.
 */
public final class SteamEngineBlockEntity extends BlockEntity {
    private static final long STEAM_PER_EU = 2;
    private static final int STEAM_PER_WATER = 200;

    private MachinePart part;
    private GTMachineDefinition definition;
    private long energyStored;
    private long energyCapacity = 640000;
    private long outputBase = 8;
    private short efficiency = 10000;
    private boolean stopped;
    private boolean active;
    private boolean emitsEnergy;
    private byte state;
    private byte piston;
    private final FluidTank steamTank = new FluidTank(
        6400,
        GTFluidUtil::isSteam
    ) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public SteamEngineBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.STEAM_ENGINE.get(), pos, state);
    }

    public void setPart(MachinePart part) {
        this.part = part;
        definition = GTMachineRegistry.find(part.category(), part.sourceLine());
        if (definition != null) {
            outputBase = definition.outputValue() > 0
                ? definition.outputValue()
                : Math.max(1, definition.output() / STEAM_PER_EU);
            efficiency = (short) (definition.efficiency() > 0
                ? definition.efficiency()
                : 10000);
            energyCapacity = definition.capacity() > 0
                ? definition.capacity()
                : outputBase * 2000L;
        }
        steamTank.setCapacity((int) Math.min(
            Integer.MAX_VALUE,
            (long) STEAM_PER_WATER * outputBase * 2L
        ));
        energyStored = 0;
        steamTank.setFluid(FluidStack.EMPTY);
        stopped = false;
        active = false;
        emitsEnergy = false;
        state = 0;
        piston = 0;
        setChanged();
    }

    public void tick() {
        if (level == null || level.isClientSide() || definition == null) {
            return;
        }

        if (active && level.getGameTime() % Math.max(1, 32 - state) == 0) {
            piston = (byte) ((piston + 1) & 3);
        }

        if (!stopped) {
            int tConversions = steamTank.getFluidAmount() / STEAM_PER_WATER;
            if (tConversions > 0) {
                steamTank.drain(
                    tConversions * STEAM_PER_WATER,
                    IFluidHandler.FluidAction.EXECUTE
                );
                long converted = units(
                    (long) tConversions * STEAM_PER_WATER / STEAM_PER_EU,
                    10000,
                    efficiency
                );
                energyStored = Math.min(energyCapacity - 1, energyStored + converted);
                outputDistilledWater(tConversions);
            }
        }

        if (level.getGameTime() % 20 == 0) {
            state = (byte) Math.min(31, scale(energyStored, energyCapacity, 32));
        }

        long tOutput = (outputBase * (state + 1)) / 16;
        active = !stopped && energyStored > tOutput && tOutput * 2 > outputBase;
        emitsEnergy = false;

        if (active) {
            Direction output = getBlockState().getValue(SteamEngineBlock.FACING);
            EnergyPacket packet = new EnergyPacket(
                EnergyKind.KINETIC,
                tOutput,
                1
            ).withDirection(piston > 1);
            MechanicalEnergyNetwork.Result result = MechanicalEnergyNetwork.distribute(
                level,
                worldPosition,
                output,
                packet,
                false
            );
            emitsEnergy = result.acceptedAmperes() > 0;
            // GT6 consumes the converted output even when no network accepts it.
            energyStored = Math.max(0, energyStored - tOutput);
        }

        if (energyStored >= energyCapacity) {
            energyStored = energyCapacity - 1;
            if (state > 30) {
                stopped = true;
                steamTank.setFluid(FluidStack.EMPTY);
            } else {
                state = 31;
            }
        }

        if (stopped && energyStored > 0) {
            energyStored = Math.max(0, energyStored - Math.max(1, energyCapacity / 64));
        }

        if (level.getGameTime() % 20 == 0) {
            setChanged();
        }
    }

    private void outputDistilledWater(int amount) {
        if (level == null || amount <= 0) {
            return;
        }
        GTFluidUtil.fillAll(
            level,
            worldPosition,
            GTFluidUtil.distilledWater(amount),
            outputSides(),
            false
        );
    }

    private List<Direction> outputSides() {
        Direction facing = getBlockState().getValue(SteamEngineBlock.FACING);
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
        Direction facing = getBlockState().getValue(SteamEngineBlock.FACING);
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

    public boolean emitsEnergy() {
        return emitsEnergy;
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

    public long energyCapacity() {
        return energyCapacity;
    }

    public long outputBase() {
        return outputBase;
    }

    public int fillSteamForTesting(int amount) {
        return steamTank.fill(
            GTFluidUtil.steam(amount),
            IFluidHandler.FluidAction.EXECUTE
        );
    }

    public Component describe() {
        if (definition == null) {
            return Component.translatable("block.gregtech.steam_engine.unassigned");
        }
        return Component.translatable(
            "message.gregtech.steam_engine.status",
            definition.display_name(),
            outputBase,
            efficiency,
            steamTank.getFluidAmount(),
            energyStored,
            stopped,
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

    private static long scale(long value, long max, int target) {
        if (max <= 0) {
            return 0;
        }
        return Math.min(target, (value * target) / max);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energyStored = tag.contains("gt.energy")
            ? tag.getLong("gt.energy")
            : tag.getLong("energy");
        energyCapacity = tag.contains("gt.capacity")
            ? tag.getLong("gt.capacity")
            : tag.getLong("energy_capacity");
        outputBase = tag.contains("gt.output")
            ? tag.getLong("gt.output")
            : tag.getLong("output_base");
        efficiency = tag.contains("gt.efficiency")
            ? tag.getShort("gt.efficiency")
            : tag.getShort("efficiency");
        stopped = tag.contains("gt.stopped")
            ? tag.getBoolean("gt.stopped")
            : tag.getBoolean("stopped");
        active = tag.getBoolean("active");
        state = tag.getByte("state");
        piston = tag.getByte("piston");
        if (tag.contains("gt.tank.0")) {
            steamTank.readFromNBT(registries, tag.getCompound("gt.tank.0"));
        } else if (tag.getLong("steam") > 0) {
            steamTank.setFluid(GTFluidUtil.steam((int) tag.getLong("steam")));
        }
        steamTank.setCapacity((int) Math.min(
            Integer.MAX_VALUE,
            (long) STEAM_PER_WATER * Math.max(1, outputBase) * 2L
        ));
        if (tag.contains("source_line")) {
            part = new MachinePart(tag.getInt("source_line"), tag.getString("category"));
            definition = GTMachineRegistry.find(part.category(), part.sourceLine());
            if (definition != null) {
                if (outputBase == 0) {
                    outputBase = definition.outputValue();
                }
                if (energyCapacity == 0) {
                    energyCapacity = definition.capacity();
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("gt.energy", energyStored);
        tag.putLong("gt.capacity", energyCapacity);
        tag.putLong("gt.output", outputBase);
        tag.putShort("gt.efficiency", efficiency);
        tag.putBoolean("gt.stopped", stopped);
        tag.putBoolean("active", active);
        tag.putByte("state", state);
        tag.putByte("piston", piston);
        tag.put("gt.tank.0", steamTank.writeToNBT(registries, new CompoundTag()));
        if (part != null) {
            tag.putInt("source_line", part.sourceLine());
            tag.putString("category", part.category());
        }
    }
}
