package gregtech.neo.block;

import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import gregtech.neo.machine.GTMachineDefinition;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class BurningBoxBlockEntity extends BlockEntity {
    private static final long EU_PER_FURNACE_TICK = 25L;
    private static final long MAX_STORED_HEAT = 1_000_000_000_000L;

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && fuelTicks(stack) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler automation = MachineItemHandler.combined(
        inventory,
        0,
        1,
        1,
        2
    );
    private MachinePart part;
    private GTMachineDefinition definition;
    private long storedHeat;
    private long burnRate;
    private long efficiency = 10_000;
    private boolean enabled = true;
    private boolean burning;

    public BurningBoxBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.BURNING_BOX.get(), pos, state);
    }

    public void setPart(MachinePart part) {
        this.part = part;
        definition = GTMachineRegistry.find(part.category(), part.sourceLine());
        if (definition != null) {
            burnRate = Math.max(0, definition.output());
            efficiency = definition.efficiency() > 0 ? definition.efficiency() : 10_000;
        }
        storedHeat = 0;
        setBurning(false);
        setChanged();
    }

    public void tick() {
        if (level == null || level.isClientSide() || !enabled || definition == null) {
            setBurning(false);
            return;
        }
        if (!"Furnace".equals(definition.fuel_map())
            || !"HU".equals(definition.energy_emitted())
            || burnRate <= 0) {
            setBurning(false);
            return;
        }
        if (burning) {
            if (!canIgnite()) {
                setBurning(false);
                return;
            }
            if (storedHeat < burnRate * 2) {
                tryConsumeFuel();
            }
            if (storedHeat >= burnRate) {
                EnergyPacket packet = new EnergyPacket(EnergyKind.HEAT, burnRate, 1);
                if (level.getBlockEntity(worldPosition.above()) instanceof EnergyReceiver receiver
                    && receiver.receivePacket(packet, Direction.DOWN, true) > 0) {
                    receiver.receivePacket(packet, Direction.DOWN, false);
                }
                storedHeat -= burnRate;
            }
            if (storedHeat < burnRate) {
                setBurning(false);
            }
            if (level.getGameTime() % 20 == 0) {
                setChanged();
            }
            if (level.random.nextInt(80) == 0) {
                spreadFire();
            }
        } else if (level.getGameTime() % 200 == 0 && hasIgnitionSource()) {
            setBurning(true);
        }
    }

    public boolean ignite() {
        if (!enabled || !canIgnite()) {
            return false;
        }
        setBurning(true);
        return true;
    }

    public void extinguish() {
        setBurning(false);
    }

    public boolean isBurning() {
        return burning;
    }

    private boolean canIgnite() {
        if (level == null) {
            return false;
        }
        BlockPos front = frontPos();
        BlockState state = level.getBlockState(front);
        return state.getCollisionShape(level, front).isEmpty()
            && state.getFluidState().isEmpty()
            && !level.getBlockState(front.above()).getFluidState().isSource();
    }

    private boolean hasIgnitionSource() {
        if (level == null) {
            return false;
        }
        return level.getBlockState(frontPos()).is(Blocks.FIRE)
            || level.getBlockState(frontPos()).is(Blocks.SOUL_FIRE);
    }

    private void spreadFire() {
        if (level == null) {
            return;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            BlockPos candidate = worldPosition.offset(
                level.random.nextInt(7) - 3,
                level.random.nextInt(4) - 1,
                level.random.nextInt(7) - 3
            );
            if (level.getBlockState(candidate).isAir()
                && level.getBlockState(candidate.below()).isSolid()) {
                level.setBlockAndUpdate(candidate, Blocks.FIRE.defaultBlockState());
                return;
            }
        }
    }

    private BlockPos frontPos() {
        return worldPosition.relative(getBlockState()
            .getValue(BurningBoxBlock.FACING));
    }

    private void setBurning(boolean value) {
        boolean changed = burning != value;
        burning = value;
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(BurningBoxBlock.LIT)
                && state.getValue(BurningBoxBlock.LIT) != value) {
                level.setBlock(
                    worldPosition,
                    state.setValue(BurningBoxBlock.LIT, value),
                    net.minecraft.world.level.block.Block.UPDATE_ALL
                );
            }
        }
        if (changed) {
            setChanged();
        }
    }

    private void tryConsumeFuel() {
        ItemStack fuel = inventory.getStackInSlot(0);
        int ticks = fuelTicks(fuel);
        if (ticks <= 0) {
            return;
        }
        long totalHeat;
        try {
            totalHeat = Math.multiplyExact((long) ticks, EU_PER_FURNACE_TICK);
            totalHeat = Math.multiplyExact(totalHeat, efficiency) / 10_000L;
        } catch (ArithmeticException ignored) {
            totalHeat = MAX_STORED_HEAT;
        }
        fuel.shrink(1);
        inventory.setStackInSlot(0, fuel.isEmpty() ? ItemStack.EMPTY : fuel);
        storedHeat = Math.min(MAX_STORED_HEAT, storedHeat + totalHeat);
    }

    public ItemStack insertFuel(ItemStack stack) {
        return inventory.insertItem(0, stack, false);
    }

    public ItemStack extractOutput() {
        ItemStack stack = inventory.getStackInSlot(1);
        return stack.isEmpty() ? ItemStack.EMPTY : inventory.extractItem(1, stack.getCount(), false);
    }

    public IItemHandler itemHandler() {
        return automation;
    }

    public GTMachineDefinition definition() {
        return definition;
    }

    public long storedHeat() {
        return storedHeat;
    }

    public long burnRate() {
        return burnRate;
    }

    public long efficiency() {
        return efficiency;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggleEnabled() {
        enabled = !enabled;
        if (!enabled) {
            setBurning(false);
        }
        setChanged();
    }

    public Component describe() {
        if (definition == null) {
            return Component.translatable("block.gregtech.burning_box.unassigned");
        }
        return Component.translatable(
            "message.gregtech.burning_box.status",
            definition.display_name(),
            burnRate,
            efficiency,
            storedHeat,
            burning,
            inventory.getStackInSlot(0).getHoverName()
        );
    }

    public static int fuelTicks(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            return 1600;
        }
        if (stack.is(Items.COAL_BLOCK)) {
            return 16000;
        }
        if (stack.is(Items.BLAZE_ROD)) {
            return 2400;
        }
        if (stack.is(Items.OAK_PLANKS) || stack.is(Items.SPRUCE_PLANKS)
            || stack.is(Items.BIRCH_PLANKS) || stack.is(Items.JUNGLE_PLANKS)
            || stack.is(Items.ACACIA_PLANKS) || stack.is(Items.DARK_OAK_PLANKS)
            || stack.is(Items.MANGROVE_PLANKS) || stack.is(Items.CHERRY_PLANKS)
            || stack.is(Items.BAMBOO_PLANKS) || stack.is(Items.CRIMSON_PLANKS)
            || stack.is(Items.WARPED_PLANKS)) {
            return 300;
        }
        if (stack.is(Items.STICK) || stack.is(Items.BAMBOO)) {
            return 100;
        }
        return 0;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        storedHeat = tag.getLong("stored_heat");
        burnRate = tag.getLong("burn_rate");
        efficiency = tag.contains("efficiency") ? tag.getLong("efficiency") : 10_000;
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        burning = tag.getBoolean("burning");
        if (tag.contains("source_line")) {
            part = new MachinePart(tag.getInt("source_line"), tag.getString("category"));
            definition = GTMachineRegistry.find(part.category(), part.sourceLine());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putLong("stored_heat", storedHeat);
        tag.putLong("burn_rate", burnRate);
        tag.putLong("efficiency", efficiency);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("burning", burning);
        if (part != null) {
            tag.putInt("source_line", part.sourceLine());
            tag.putString("category", part.category());
        }
    }
}
