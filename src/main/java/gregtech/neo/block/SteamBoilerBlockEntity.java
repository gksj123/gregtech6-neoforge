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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * GT6 1.7.10: gregtech/tileentity/energy/converters/MultiTileEntityBoilerTank.java.
 */
public final class SteamBoilerBlockEntity extends BlockEntity implements EnergyReceiver {
    private static final long STEAM_PER_EU = 2;
    private static final long EU_PER_WATER = 80;
    private static final long STEAM_PER_WATER = 160;
    private static final int WATER_CAPACITY = 4000;

    private MachinePart part;
    private GTMachineDefinition definition;
    private long storedHeat;
    private long heatCapacity = 640000;
    private long outputRate = 64;
    private short efficiency = 10000;
    private short coolDownResetTimer = 128;
    private byte barometer;
    private final FluidTank waterTank = new FluidTank(
        WATER_CAPACITY,
        GTFluidUtil::isWater
    ) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank steamTank = new FluidTank(
        64000,
        GTFluidUtil::isSteam
    ) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public SteamBoilerBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.STEAM_BOILER.get(), pos, state);
    }

    public void setPart(MachinePart part) {
        this.part = part;
        definition = GTMachineRegistry.find(part.category(), part.sourceLine());
        outputRate = definition != null && definition.outputSu() > 0
            ? definition.outputSu()
            : outputRateForSourceLine(part.sourceLine());
        steamTank.setCapacity((int) Math.min(Integer.MAX_VALUE, outputRate * 10000L));
        heatCapacity = outputRate * 10000L;
        storedHeat = 0;
        steamTank.setFluid(FluidStack.EMPTY);
        waterTank.setFluid(FluidStack.EMPTY);
        efficiency = 10000;
        barometer = 0;
        setChanged();
    }

    // GT6 1.7.10: gregtech/loaders/b/Loader_MultiTileEntities.java:553-579
    private static long outputRateForSourceLine(int line) {
        return switch (line) {
            case 553 -> 16 * STEAM_PER_EU;
            case 554 -> 20 * STEAM_PER_EU;
            case 555 -> 24 * STEAM_PER_EU;
            case 556 -> 24 * STEAM_PER_EU;
            case 557 -> 28 * STEAM_PER_EU;
            case 558 -> 16 * STEAM_PER_EU;
            case 559 -> 32 * STEAM_PER_EU;
            case 560 -> 96 * STEAM_PER_EU;
            case 561 -> 112 * STEAM_PER_EU;
            case 562 -> 112 * STEAM_PER_EU;
            case 563 -> 128 * STEAM_PER_EU;
            case 564 -> 128 * STEAM_PER_EU;
            case 565 -> 256 * STEAM_PER_EU;
            case 567 -> 64 * STEAM_PER_EU;
            case 568 -> 80 * STEAM_PER_EU;
            case 569 -> 96 * STEAM_PER_EU;
            case 570 -> 96 * STEAM_PER_EU;
            case 571 -> 112 * STEAM_PER_EU;
            case 572 -> 64 * STEAM_PER_EU;
            case 573 -> 128 * STEAM_PER_EU;
            case 574 -> 384 * STEAM_PER_EU;
            case 575 -> 448 * STEAM_PER_EU;
            case 576 -> 448 * STEAM_PER_EU;
            case 577 -> 512 * STEAM_PER_EU;
            case 578 -> 512 * STEAM_PER_EU;
            case 579 -> 1024 * STEAM_PER_EU;
            default -> 64;
        };
    }

    public void tick() {
        if (level == null || level.isClientSide() || definition == null) {
            return;
        }

        long tConversions = Math.min(
            steamTank.getCapacity() / 2560L,
            Math.min(storedHeat / EU_PER_WATER, waterTank.getFluidAmount())
        );
        if (tConversions > 0) {
            boolean distilled = GTFluidUtil.isDistilledWater(waterTank.getFluid());
            waterTank.drain((int) tConversions, IFluidHandler.FluidAction.EXECUTE);
            if (level.random.nextInt(10) == 0
                && efficiency > 5000
                && waterTank.getFluidAmount() > 0
                && !distilled) {
                efficiency -= (short) Math.min(tConversions, efficiency - 5000);
                if (efficiency < 5000) {
                    efficiency = 5000;
                }
            }
            long produced = units(
                tConversions,
                10000,
                (long) efficiency * STEAM_PER_WATER
            );
            steamTank.fill(
                GTFluidUtil.steam((int) Math.min(Integer.MAX_VALUE, produced)),
                IFluidHandler.FluidAction.EXECUTE
            );
            storedHeat -= tConversions * EU_PER_WATER;
            coolDownResetTimer = 128;
        }

        if (coolDownResetTimer-- <= 0) {
            coolDownResetTimer = 0;
            storedHeat -= (outputRate * 64) / STEAM_PER_EU;
            steamTank.drain(
                (int) Math.min(Integer.MAX_VALUE, outputRate * 64),
                IFluidHandler.FluidAction.EXECUTE
            );
            if (storedHeat <= 0) {
                storedHeat = 0;
                coolDownResetTimer = 128;
            }
        }

        long tAmount = steamTank.getFluidAmount() - steamTank.getCapacity() / 2L;
        if (tAmount > 0) {
            long emitRate = tAmount > steamTank.getCapacity() / 4L
                ? outputRate * 2
                : outputRate;
            int emitAmount = (int) Math.min(emitRate, tAmount);
            int accepted = SteamNetwork.distribute(
                level,
                worldPosition,
                Direction.UP,
                GTFluidUtil.steam(emitAmount),
                false
            );
            if (accepted > 0) {
                steamTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
            }
        }

        barometer = (byte) scale(steamTank.getFluidAmount(), steamTank.getCapacity(), 31);
        if (storedHeat > heatCapacity || steamTank.getFluidAmount() >= steamTank.getCapacity()) {
            explode();
        }

        if (level.getGameTime() % 20 == 0) {
            setChanged();
        }
    }

    @Override
    public long receivePacket(EnergyPacket packet, Direction side, boolean simulate) {
        if (packet.kind() != EnergyKind.HEAT) {
            return 0;
        }
        if (simulate) {
            return packet.amount();
        }
        storedHeat = EnergyPacket.saturatedAdd(storedHeat, packet.totalEnergy());
        coolDownResetTimer = (short) Math.max(coolDownResetTimer, 32);
        setChanged();
        return packet.amount();
    }

    public boolean addWater(long amount) {
        return addWaterFluid(new FluidStack(Fluids.WATER, (int) amount));
    }

    public boolean addDistilledWater(long amount) {
        return addWaterFluid(GTFluidUtil.distilledWater((int) amount));
    }

    private boolean addWaterFluid(FluidStack stack) {
        if (waterTank.fill(stack.copy(), IFluidHandler.FluidAction.SIMULATE) != stack.getAmount()) {
            return false;
        }
        waterTank.fill(stack.copy(), IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        return true;
    }

    public IFluidHandler fluidHandler(Direction side) {
        if (side == Direction.UP) {
            return new FluidHandlerView(steamTank, false, true);
        }
        return new FluidHandlerView(waterTank, true, false);
    }

    public boolean descale(Player player) {
        if (efficiency >= 10000) {
            return false;
        }
        if (barometer > 15 && level != null) {
            explode();
            return false;
        }
        if (player != null && storedHeat + steamTank.getFluidAmount() / 2L > 2000) {
            float damage = Math.max(
                1.0F,
                Math.min(10.0F, (storedHeat + steamTank.getFluidAmount() / 2.0F) / 2000.0F)
            );
            player.hurt(level.damageSources().onFire(), damage);
        }
        steamTank.setFluid(FluidStack.EMPTY);
        efficiency = 10000;
        storedHeat = 0;
        setChanged();
        return true;
    }

    public void damageTouchingEntity(net.minecraft.world.entity.LivingEntity entity) {
        if (storedHeat + steamTank.getFluidAmount() / STEAM_PER_EU <= 2000) {
            return;
        }
        float damage = Math.min(
            10.0F,
            (storedHeat + steamTank.getFluidAmount() / 2.0F) / 2000.0F
        );
        entity.hurt(level.damageSources().onFire(), damage);
    }

    private void explode() {
        if (level == null || level.isClientSide()) {
            return;
        }
        float power = Math.max(1.0F, (float) Math.sqrt(steamTank.getFluidAmount()) / 100.0F);
        steamTank.setFluid(FluidStack.EMPTY);
        storedHeat = 0;
        level.explode(
            null,
            worldPosition.getX() + 0.5,
            worldPosition.getY() + 0.5,
            worldPosition.getZ() + 0.5,
            power,
            Level.ExplosionInteraction.BLOCK
        );
    }

    public void onBreak() {
        if (barometer > 4 && level != null && !level.isClientSide()) {
            explode();
        }
    }

    public Component describe() {
        if (definition == null) {
            return Component.translatable("block.gregtech.steam_boiler.unassigned");
        }
        return Component.translatable(
            "message.gregtech.steam_boiler.status",
            definition.display_name(),
            outputRate,
            efficiency,
            storedHeat,
            heatCapacity,
            waterTank.getFluidAmount(),
            steamTank.getFluidAmount(),
            barometer
        );
    }

    public GTMachineDefinition definition() {
        return definition;
    }

    public long storedHeat() {
        return storedHeat;
    }

    public long steamAmount() {
        return steamTank.getFluidAmount();
    }

    public long waterAmount() {
        return waterTank.getFluidAmount();
    }

    public FluidStack waterFluid() {
        return waterTank.getFluid().copy();
    }

    public FluidStack steamFluid() {
        return steamTank.getFluid().copy();
    }

    public short efficiency() {
        return efficiency;
    }

    public byte barometer() {
        return barometer;
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
        storedHeat = tag.contains("gt.energy")
            ? tag.getLong("gt.energy")
            : tag.getLong("stored_heat");
        heatCapacity = tag.contains("gt.capacity")
            ? tag.getLong("gt.capacity")
            : tag.getLong("heat_capacity");
        outputRate = tag.contains("gt.output")
            ? tag.getLong("gt.output")
            : tag.getLong("output_rate");
        efficiency = tag.contains("gt.efficiency")
            ? tag.getShort("gt.efficiency")
            : tag.getShort("efficiency");
        coolDownResetTimer = tag.getShort("cooldown");
        barometer = tag.getByte("barometer");
        if (tag.contains("gt.tank.0")) {
            waterTank.readFromNBT(registries, tag.getCompound("gt.tank.0"));
        } else if (tag.getLong("water") > 0) {
            waterTank.setFluid(new FluidStack(Fluids.WATER, (int) tag.getLong("water")));
        }
        if (tag.contains("gt.tank.1")) {
            steamTank.readFromNBT(registries, tag.getCompound("gt.tank.1"));
        } else if (tag.getLong("steam") > 0) {
            steamTank.setFluid(GTFluidUtil.steam((int) tag.getLong("steam")));
        }
        if (tag.contains("source_line")) {
            part = new MachinePart(tag.getInt("source_line"), tag.getString("category"));
            definition = GTMachineRegistry.find(part.category(), part.sourceLine());
            if (outputRate == 0) {
                outputRate = definition != null && definition.outputSu() > 0
                    ? definition.outputSu()
                    : outputRateForSourceLine(part.sourceLine());
            }
            if (heatCapacity == 0) {
                heatCapacity = outputRate * 10000L;
            }
            if (steamTank.getCapacity() == 64000 && outputRate > 0) {
                steamTank.setCapacity((int) Math.min(Integer.MAX_VALUE, outputRate * 10000L));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("gt.energy", storedHeat);
        tag.putLong("gt.capacity", heatCapacity);
        tag.putLong("gt.output", outputRate);
        tag.putShort("gt.efficiency", efficiency);
        tag.putShort("cooldown", coolDownResetTimer);
        tag.putByte("barometer", barometer);
        tag.put("gt.tank.0", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("gt.tank.1", steamTank.writeToNBT(registries, new CompoundTag()));
        if (part != null) {
            tag.putInt("source_line", part.sourceLine());
            tag.putString("category", part.category());
        }
    }
}
