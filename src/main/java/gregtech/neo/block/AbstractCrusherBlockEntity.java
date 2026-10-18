package gregtech.neo.block;

import gregtech.neo.core.EnergyBuffer;
import gregtech.neo.core.EnergyPacket;
import gregtech.neo.core.MachineEnergyProfile;
import gregtech.neo.recipe.GTRecipeEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractCrusherBlockEntity extends BlockEntity implements EnergyReceiver {
    private static final int INPUT_END = 1;
    private static final int OUTPUT_START = 1;
    private static final int OUTPUT_END = 13;

    protected final MachineEnergyProfile energyProfile;
    protected final EnergyBuffer buffer;
    protected final ItemStackHandler inventory;
    private final IItemHandler automation;
    private final IItemHandler automationInputs;
    private final IItemHandler automationOutputs;

    private boolean active = true;
    private long progress;
    private GTRecipeEngine.Plan plan;
    private long lastIncomingSize;

    protected AbstractCrusherBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState state,
        MachineEnergyProfile energyProfile
    ) {
        super(type, pos, state);
        this.energyProfile = energyProfile;
        this.buffer = new EnergyBuffer(energyProfile.kind(), energyProfile.capacity());
        this.inventory = new ItemStackHandler(OUTPUT_END) {
            @Override
            protected void onContentsChanged(int slot) {
                plan = null;
                setChanged();
            }
        };
        this.automation = MachineItemHandler.combined(inventory, 0, INPUT_END, OUTPUT_START, OUTPUT_END);
        this.automationInputs = MachineItemHandler.inputs(inventory, 0, INPUT_END);
        this.automationOutputs = MachineItemHandler.outputs(inventory, OUTPUT_START, OUTPUT_END);
    }

    protected final void tickProcessing() {
        if (level == null || level.isClientSide() || !active || !isFormedForProcessing()) {
            return;
        }
        if (plan == null) {
            plan = findPlan();
            if (plan == null) {
                progress = 0;
                return;
            }
        }
        if (buffer.stored() < plan.powerPerTick()) {
            return;
        }
        buffer.consume(plan.powerPerTick());
        progress++;
        if (progress >= plan.durationTicks()) {
            completePlan();
        }
        if (progress % 20 == 0) {
            setChanged();
        }
    }

    private GTRecipeEngine.Plan findPlan() {
        List<ItemStack> slots = new ArrayList<>(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            slots.add(inventory.getStackInSlot(slot));
        }
        return GTRecipeEngine.findPlan(
            "Crusher",
            slots,
            0,
            INPUT_END,
            OUTPUT_START,
            OUTPUT_END,
            parallelism(),
            (int) energyProfile.minimumSize(),
            (int) energyProfile.maximumSize(),
            parallelDuration(),
            cheapOverclocking(),
            efficiency()
        );
    }

    private void completePlan() {
        GTRecipeEngine.Plan completed = plan;
        plan = null;
        if (!insertOutputs(completed.outputs())) {
            progress = Math.max(0, completed.durationTicks() - 1);
            plan = completed;
            return;
        }
        for (GTRecipeEngine.InputUse input : completed.inputs()) {
            ItemStack stack = inventory.getStackInSlot(input.slot());
            stack.shrink(Math.min(input.count(), stack.getCount()));
            inventory.setStackInSlot(input.slot(), stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        progress = 0;
        setChanged();
    }

    private boolean insertOutputs(List<ItemStack> outputs) {
        List<ItemStack> simulated = new ArrayList<>();
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) {
            simulated.add(inventory.getStackInSlot(slot).copy());
        }
        for (ItemStack output : outputs) {
            if (!simulateInsert(simulated, output.copy())) {
                return false;
            }
        }
        for (int index = 0; index < simulated.size(); index++) {
            inventory.setStackInSlot(OUTPUT_START + index, simulated.get(index));
        }
        return true;
    }

    private static boolean simulateInsert(List<ItemStack> slots, ItemStack remaining) {
        for (int index = 0; index < slots.size() && !remaining.isEmpty(); index++) {
            ItemStack existing = slots.get(index);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }
            int insert = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (insert > 0) {
                ItemStack grown = existing.copy();
                grown.grow(insert);
                slots.set(index, grown);
                remaining.shrink(insert);
            }
        }
        for (int index = 0; index < slots.size() && !remaining.isEmpty(); index++) {
            if (!slots.get(index).isEmpty()) {
                continue;
            }
            int insert = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.copy();
            placed.setCount(insert);
            slots.set(index, placed);
            remaining.shrink(insert);
        }
        return remaining.isEmpty();
    }

    @Override
    public long receivePacket(EnergyPacket packet, Direction side, boolean simulate) {
        if (!energyProfile.acceptsKind(packet)) {
            if (!simulate && packet.kind() == energyProfile.kind() && packet.size() > energyProfile.maximumSize()) {
                onEnergySizeExceeded(packet);
            }
            return 0;
        }
        long acceptedAmperes = Math.min(packet.amount(), energyProfile.maximumAmount());
        acceptedAmperes = Math.min(acceptedAmperes, buffer.room() / packet.size());
        if (acceptedAmperes <= 0) {
            return 0;
        }
        if (simulate) {
            return acceptedAmperes;
        }
        long acceptedEnergy = buffer.accept(packet.withAmount(acceptedAmperes));
        lastIncomingSize = packet.size();
        setChanged();
        return acceptedEnergy / packet.size();
    }

    protected int parallelism() {
        return 1;
    }

    protected boolean parallelDuration() {
        return true;
    }

    protected boolean cheapOverclocking() {
        return false;
    }

    protected int efficiency() {
        return 10_000;
    }

    protected abstract boolean isFormedForProcessing();

    protected abstract void onEnergySizeExceeded(EnergyPacket packet);

    public ItemStack insertItem(ItemStack stack) {
        return inventory.insertItem(0, stack, false);
    }

    public ItemStack extractOutput() {
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                return inventory.extractItem(slot, stack.getCount(), false);
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack peekOutputForTesting() {
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    public IItemHandler itemHandler() {
        return automation;
    }

    public IItemHandler inputItemHandler() {
        return automationInputs;
    }

    public IItemHandler outputItemHandler() {
        return automationOutputs;
    }

    public void toggleActive() {
        active = !active;
        setChanged();
    }

    public boolean isActive() {
        return active;
    }

    public MachineEnergyProfile energyProfile() {
        return energyProfile;
    }

    public long storedEnergy() {
        return buffer.stored();
    }

    public long capacity() {
        return buffer.capacity();
    }

    public long progress() {
        return progress;
    }

    public long duration() {
        return plan == null ? 0 : plan.durationTicks();
    }

    public long lastIncomingSize() {
        return lastIncomingSize;
    }

    public ItemStack inputStack() {
        return inventory.getStackInSlot(0);
    }

    public ItemStack outputStack() {
        return peekOutputForTesting();
    }

    public String activeRecipeSource() {
        return plan == null ? "" : plan.source() + ":" + plan.line();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        active = !tag.contains("active") || tag.getBoolean("active");
        progress = tag.getLong("progress");
        lastIncomingSize = tag.getLong("last_size");
        buffer.restore(tag.getLong("energy"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putBoolean("active", active);
        tag.putLong("progress", progress);
        tag.putLong("last_size", lastIncomingSize);
        tag.putLong("energy", buffer.stored());
    }
}
