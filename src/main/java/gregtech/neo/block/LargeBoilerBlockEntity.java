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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;

/**
 * GT6 1.7.10: gregtech/tileentity/multiblocks/MultiTileEntityLargeBoiler.java.
 */
public final class LargeBoilerBlockEntity extends BlockEntity implements EnergyReceiver {
    private static final long STEAM_PER_EU = 2;
    private static final long EU_PER_WATER = 80;
    private static final long STEAM_PER_WATER = 160;
    private static final int WATER_CAPACITY = 128_000;

    public enum FluidRole {
        NONE,
        WATER_IN,
        STEAM_OUT
    }

    private record OutputTarget(BlockPos pos, Direction side) {
    }

    private MachinePart part;
    private GTMachineDefinition definition;
    private long storedHeat;
    private long heatCapacity = 20_480_000L;
    private long outputRate = 2_048;
    private short efficiency = 10_000;
    private short coolDownResetTimer = 128;
    private byte barometer;
    private boolean formed;
    private boolean destroyed;
    private int structureCooldown;
    private String structureError = "";

    private final FluidTank waterTank = new FluidTank(WATER_CAPACITY, GTFluidUtil::isWater) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank steamTank = new FluidTank(2_048_000, GTFluidUtil::isSteam) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public LargeBoilerBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.LARGE_BOILER.get(), pos, state);
        formed = state.hasProperty(LargeBoilerBlock.FORMED)
            && state.getValue(LargeBoilerBlock.FORMED);
    }

    public void setPart(MachinePart part) {
        this.part = part;
        definition = GTMachineRegistry.find(part.category(), part.sourceLine());
        outputRate = definition != null && definition.outputSu() > 0
            ? definition.outputSu()
            : outputRateForSourceLine(part.sourceLine());
        heatCapacity = outputRate * 10_000L;
        int steamCapacity = (int) Math.min(Integer.MAX_VALUE, heatCapacity);
        steamTank.setCapacity(steamCapacity);
        waterTank.setCapacity(WATER_CAPACITY);
        storedHeat = 0;
        steamTank.setFluid(FluidStack.EMPTY);
        waterTank.setFluid(FluidStack.EMPTY);
        efficiency = 10_000;
        coolDownResetTimer = 128;
        barometer = 0;
        destroyed = false;
        setChanged();
    }

    // GT6 1.7.10: gregtech/loaders/b/Loader_MultiTileEntities.java:1248-1252
    private static long outputRateForSourceLine(int line) {
        return switch (line) {
            case 1248, 1249 -> 4_096L * STEAM_PER_EU;
            case 1250 -> 8_192L * STEAM_PER_EU;
            case 1251 -> 16_384L * STEAM_PER_EU;
            case 1252 -> 131_072L * STEAM_PER_EU;
            default -> 1_024L * STEAM_PER_EU;
        };
    }

    public void tick() {
        if (level == null || level.isClientSide() || destroyed) {
            return;
        }

        if (--structureCooldown <= 0) {
            structureCooldown = 20;
            forceStructureCheck();
        }
        if (!formed) {
            return;
        }

        long conversions = Math.min(
            steamTank.getCapacity() / 2_560L,
            Math.min(storedHeat / EU_PER_WATER, waterTank.getFluidAmount())
        );
        if (conversions > 0) {
            boolean distilled = GTFluidUtil.isDistilledWater(waterTank.getFluid());
            waterTank.drain((int) conversions, IFluidHandler.FluidAction.EXECUTE);
            if (level.random.nextInt(10) == 0
                && efficiency > 5_000
                && waterTank.getFluidAmount() > 0
                && !distilled) {
                efficiency -= (short) Math.min(conversions, efficiency - 5_000);
                if (efficiency < 5_000) {
                    efficiency = 5_000;
                }
            }
            long produced = units(
                conversions,
                10_000,
                (long) efficiency * STEAM_PER_WATER
            );
            steamTank.fill(
                GTFluidUtil.steam((int) Math.min(Integer.MAX_VALUE, produced)),
                IFluidHandler.FluidAction.EXECUTE
            );
            storedHeat -= conversions * EU_PER_WATER;
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

        long excessSteam = steamTank.getFluidAmount() - steamTank.getCapacity() / 2L;
        if (excessSteam > 0) {
            long output = excessSteam > steamTank.getCapacity() / 4L
                ? outputRate * 2
                : outputRate;
            int offered = (int) Math.min(Integer.MAX_VALUE, Math.min(excessSteam, output));
            int accepted = distributeSteam(offered);
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

    public void forceStructureCheck() {
        if (level == null || level.isClientSide() || destroyed) {
            return;
        }
        if (!(getBlockState().getBlock() instanceof LargeBoilerBlock)) {
            return;
        }
        formed = structureIsValid();
        BlockState state = getBlockState();
        if (state.getValue(LargeBoilerBlock.FORMED) != formed) {
            level.setBlock(
                worldPosition,
                state.setValue(LargeBoilerBlock.FORMED, formed),
                Block.UPDATE_ALL
            );
        }
        if (!formed && barometer > 4) {
            explode();
        }
    }

    private boolean structureIsValid() {
        if (definition == null) {
            return structureFailure("controller has no machine definition");
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos bottom = partPos(x, -1, z);
                if (!level.isLoaded(bottom)) {
                    return structureFailure("unloaded " + bottom);
                }
                if (!level.getBlockState(bottom).is(gregtech.neo.registry.GTBlocks.HEAT_TRANSMITTER.get())) {
                    return structureFailure("heat transmitter expected at " + bottom);
                }
            }
        }

        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos target = partPos(x, y, z);
                    if (!level.isLoaded(target)) {
                        return structureFailure("unloaded " + target);
                    }
                    if (x == 0 && z == 0) {
                        if (y < 2 && !level.getBlockState(target).isAir()) {
                            return structureFailure("boiler interior must be hollow at " + target);
                        }
                        if (y == 2 && !isMatchingWall(target)) {
                            return structureFailure("matching top wall expected at " + target);
                        }
                        continue;
                    }
                    if (target.equals(worldPosition)) {
                        continue;
                    }
                    if (!isMatchingWall(target)) {
                        return structureFailure("matching boiler wall expected at " + target);
                    }
                }
            }
        }
        structureError = "";
        return true;
    }

    private boolean isMatchingWall(BlockPos target) {
        if (!(level.getBlockState(target).getBlock() instanceof LargeBoilerWallBlock)) {
            return false;
        }
        if (!(level.getBlockEntity(target) instanceof LargeBoilerWallBlockEntity wall)) {
            return false;
        }
        return definition.material_slug().equals(wall.material());
    }

    private boolean structureFailure(String message) {
        structureError = message;
        return false;
    }

    private int distributeSteam(int amount) {
        List<IFluidHandler> handlers = new ArrayList<>(5);
        List<Integer> capacities = new ArrayList<>(5);
        for (OutputTarget target : outputTargets()) {
            IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK,
                target.pos(),
                target.side()
            );
            if (handler == null) {
                continue;
            }
            int capacity = handler.fill(
                GTFluidUtil.steam(amount),
                IFluidHandler.FluidAction.SIMULATE
            );
            if (capacity > 0) {
                handlers.add(handler);
                capacities.add(capacity);
            }
        }

        if (handlers.isEmpty()) {
            return 0;
        }
        if (handlers.size() == 1) {
            return handlers.getFirst().fill(
                GTFluidUtil.steam(amount),
                IFluidHandler.FluidAction.EXECUTE
            );
        }

        long totalCapacity = 0;
        for (int capacity : capacities) {
            totalCapacity += capacity;
        }
        if (totalCapacity < amount) {
            int accepted = 0;
            for (int index = 0; index < handlers.size(); index++) {
                accepted += fill(handlers.get(index), capacities.get(index));
            }
            return accepted;
        }

        if (amount < handlers.size()) {
            return 0;
        }

        int accepted = 0;
        int remaining = amount;
        int targetCount = handlers.size();
        int originalTargets = targetCount;
        for (int index = 0; index < handlers.size(); index++) {
            if (capacities.get(index) <= amount / originalTargets) {
                int moved = fill(handlers.get(index), amount / originalTargets);
                accepted += moved;
                remaining -= moved;
                handlers.set(index, null);
                targetCount--;
                if (targetCount < 2) {
                    break;
                }
            }
        }

        for (IFluidHandler handler : handlers) {
            if (handler == null) {
                continue;
            }
            int share = targetCount <= 1 ? remaining : remaining / targetCount;
            int moved = fill(handler, Math.min(share, remaining));
            accepted += moved;
            remaining -= moved;
            targetCount--;
        }
        return accepted;
    }

    private static int fill(IFluidHandler handler, int amount) {
        if (handler == null || amount <= 0) {
            return 0;
        }
        return handler.fill(
            GTFluidUtil.steam(amount),
            IFluidHandler.FluidAction.EXECUTE
        );
    }

    private List<OutputTarget> outputTargets() {
        BlockPos top = partPos(0, 2, 0).above();
        Direction right = right();
        Direction outward = facing();
        return List.of(
            new OutputTarget(top, Direction.DOWN),
            new OutputTarget(partPos(2, 1, 0), right.getOpposite()),
            new OutputTarget(partPos(-2, 1, 0), right),
            new OutputTarget(partPos(0, 1, 2), outward.getOpposite()),
            new OutputTarget(partPos(0, 1, -2), outward)
        );
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
        if (side == null || side == Direction.UP) {
            return null;
        }
        return new FluidHandlerView(waterTank, true, false);
    }

    IFluidHandler waterInputHandler() {
        return new FluidHandlerView(waterTank, true, false);
    }

    IFluidHandler steamOutputHandler() {
        return new FluidHandlerView(steamTank, false, true);
    }

    public boolean descale(Player player) {
        if (efficiency >= 10_000) {
            return false;
        }
        if (barometer > 15) {
            explode();
            return false;
        }
        if (player != null && storedHeat + steamTank.getFluidAmount() / STEAM_PER_EU > 2_000) {
            float damage = Math.max(
                1.0F,
                Math.min(10.0F, (storedHeat + steamTank.getFluidAmount() / 2.0F) / 2_000.0F)
            );
            player.hurt(level.damageSources().onFire(), damage);
        }
        steamTank.setFluid(FluidStack.EMPTY);
        efficiency = 10_000;
        storedHeat = 0;
        setChanged();
        return true;
    }

    public void damageTouchingEntity(LivingEntity entity) {
        if (!formed || storedHeat + steamTank.getFluidAmount() / STEAM_PER_EU <= 2_000) {
            return;
        }
        float damage = Math.min(
            10.0F,
            (storedHeat + steamTank.getFluidAmount() / 2.0F) / 2_000.0F
        );
        entity.hurt(level.damageSources().onFire(), damage);
    }

    public void onBreak(Player player) {
        if (!destroyed && barometer > 4 && (player == null || !player.isCreative())) {
            explode();
        }
    }

    private void explode() {
        if (level == null || level.isClientSide() || destroyed) {
            return;
        }
        destroyed = true;
        float power = 2.0F + Math.max(
            1.0F,
            (float) (Math.sqrt(steamTank.getFluidAmount()) / 1_000.0D)
        );
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

    public Component describe() {
        if (definition == null) {
            return Component.translatable("block.gregtech.large_boiler.unassigned");
        }
        return Component.translatable(
            "message.gregtech.large_boiler.status",
            definition.display_name(),
            formed,
            outputRate,
            efficiency,
            storedHeat,
            heatCapacity,
            waterTank.getFluidAmount(),
            steamTank.getFluidAmount(),
            barometer,
            structureError
        );
    }

    public GTMachineDefinition definition() {
        return definition;
    }

    public boolean isFormed() {
        return formed;
    }

    public String structureError() {
        return structureError;
    }

    public long storedHeat() {
        return storedHeat;
    }

    public long heatCapacity() {
        return heatCapacity;
    }

    public long outputRate() {
        return outputRate;
    }

    public long steamAmount() {
        return steamTank.getFluidAmount();
    }

    public long steamCapacity() {
        return steamTank.getCapacity();
    }

    public long waterAmount() {
        return waterTank.getFluidAmount();
    }

    public short efficiency() {
        return efficiency;
    }

    public byte barometer() {
        return barometer;
    }

    public int fillSteamForTesting(int amount) {
        return steamTank.fill(
            GTFluidUtil.steam(amount),
            IFluidHandler.FluidAction.EXECUTE
        );
    }

    public boolean isPart(BlockPos pos) {
        BlockPos delta = pos.subtract(center());
        Direction right = right();
        int x = delta.getX() * right.getStepX() + delta.getZ() * right.getStepZ();
        int z = delta.getX() * facing().getStepX() + delta.getZ() * facing().getStepZ();
        int y = delta.getY();
        if (y == -1) {
            return Math.abs(x) <= 1 && Math.abs(z) <= 1;
        }
        return y >= 0 && y <= 2
            && Math.abs(x) <= 1
            && Math.abs(z) <= 1;
    }

    public FluidRole fluidRoleAt(BlockPos pos) {
        if (!isPart(pos)) {
            return FluidRole.NONE;
        }
        int y = pos.getY() - center().getY();
        if (y == 0) {
            return FluidRole.WATER_IN;
        }
        if (y == 1 || y == 2) {
            return FluidRole.STEAM_OUT;
        }
        return FluidRole.NONE;
    }

    public BlockPos center() {
        return worldPosition.relative(facing().getOpposite());
    }

    public String material() {
        return definition == null ? "" : definition.material_slug();
    }

    private Direction facing() {
        return getBlockState().getValue(LargeBoilerBlock.FACING);
    }

    Direction right() {
        return facing().getCounterClockWise();
    }

    private BlockPos partPos(int x, int y, int z) {
        return center().relative(right(), x).relative(facing(), z).above(y);
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
        storedHeat = tag.getLong("gt.energy");
        heatCapacity = tag.getLong("gt.capacity");
        outputRate = tag.getLong("gt.output");
        efficiency = tag.getShort("gt.efficiency");
        coolDownResetTimer = tag.getShort("cooldown");
        barometer = tag.getByte("barometer");
        formed = tag.getBoolean("formed");
        destroyed = tag.getBoolean("destroyed");
        if (tag.contains("source_line")) {
            part = new MachinePart(tag.getInt("source_line"), tag.getString("category"));
            definition = GTMachineRegistry.find(part.category(), part.sourceLine());
        }
        if (outputRate <= 0) {
            outputRate = definition != null && definition.outputSu() > 0
                ? definition.outputSu()
                : outputRateForSourceLine(part == null ? 1248 : part.sourceLine());
        }
        if (heatCapacity <= 0) {
            heatCapacity = outputRate * 10_000L;
        }
        steamTank.setCapacity((int) Math.min(Integer.MAX_VALUE, heatCapacity));
        waterTank.setCapacity(WATER_CAPACITY);
        if (tag.contains("gt.tank.0")) {
            waterTank.readFromNBT(registries, tag.getCompound("gt.tank.0"));
        }
        if (tag.contains("gt.tank.1")) {
            steamTank.readFromNBT(registries, tag.getCompound("gt.tank.1"));
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
        tag.putBoolean("formed", formed);
        tag.putBoolean("destroyed", destroyed);
        tag.put("gt.tank.0", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("gt.tank.1", steamTank.writeToNBT(registries, new CompoundTag()));
        if (part != null) {
            tag.putInt("source_line", part.sourceLine());
            tag.putString("category", part.category());
        }
    }
}
