package gregtech.neo.block;

import gregtech.neo.core.EnergyBuffer;
import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import gregtech.neo.core.MachineEnergyProfile;
import gregtech.neo.machine.GTMachineDefinition;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.recipe.GTRecipeEngine;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public final class BasicMachineBlockEntity extends BlockEntity implements EnergyReceiver {
    private static final int INPUT_SLOTS = 9;
    private static final int OUTPUT_START = 9;

    private final ItemStackHandler inventory = new ItemStackHandler(18) {
        @Override
        protected void onContentsChanged(int slot) {
            plan = null;
            setChanged();
        }
    };
    private final IItemHandler automation = MachineItemHandler.combined(
        inventory,
        0,
        INPUT_SLOTS,
        OUTPUT_START,
        inventory.getSlots()
    );
    private final IItemHandler automationInputs = MachineItemHandler.inputs(inventory, 0, INPUT_SLOTS);
    private final IItemHandler automationOutputs = MachineItemHandler.outputs(
        inventory,
        OUTPUT_START,
        inventory.getSlots()
    );
    private MachinePart part;
    private GTMachineDefinition definition;
    private MachineEnergyProfile energyProfile;
    private EnergyBuffer buffer;
    private boolean enabled = true;
    private GTRecipeEngine.Plan plan;
    private long progress;
    private long lastIncomingSize;

    public BasicMachineBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.BASIC_MACHINE.get(), pos, state);
    }

    public void setPart(MachinePart part) {
        this.part = part;
        resolveDefinition();
        plan = null;
        progress = 0;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void resolveDefinition() {
        definition = part == null ? null : GTMachineRegistry.find(part.category(), part.sourceLine());
        energyProfile = definition == null ? null : energyProfile(definition);
        buffer = energyProfile == null ? null : new EnergyBuffer(energyProfile.kind(), energyProfile.capacity());
    }

    private static MachineEnergyProfile energyProfile(GTMachineDefinition definition) {
        EnergyKind kind = energyKind(definition.energy_accepted());
        if (kind == null || definition.input() <= 0) {
            return null;
        }
        long recommended = definition.input();
        long minimum = definition.input_min() > 0 ? definition.input_min() : Math.max(1, recommended / 2);
        long maximum = definition.input_max() > 0 ? definition.input_max() : Math.max(recommended * 2, recommended);
        if (minimum > recommended) {
            minimum = recommended;
        }
        if (maximum < recommended) {
            maximum = recommended;
        }
        return new MachineEnergyProfile(kind, minimum, recommended, maximum, 64, maximum);
    }

    private static EnergyKind energyKind(String sourceName) {
        return switch (sourceName) {
            case "EU" -> EnergyKind.ELECTRICITY;
            case "RU" -> EnergyKind.ROTATION;
            case "KU" -> EnergyKind.KINETIC;
            case "HU" -> EnergyKind.HEAT;
            case "CU" -> EnergyKind.CRYO;
            case "LU" -> EnergyKind.LIGHT;
            case "MU" -> EnergyKind.MAGNETIC;
            case "NU" -> EnergyKind.NEUTRON;
            case "QU" -> EnergyKind.QUANTUM;
            case "TU" -> EnergyKind.TIME;
            case "STEAM" -> EnergyKind.STEAM;
            default -> null;
        };
    }

    @Override
    public long receivePacket(EnergyPacket packet, Direction side, boolean simulate) {
        if (!enabled || energyProfile == null || !energyProfile.acceptsKind(packet)) {
            return 0;
        }
        long accepted = Math.min(packet.amount(), energyProfile.maximumAmount());
        accepted = Math.min(accepted, buffer.room() / packet.size());
        if (accepted <= 0) {
            return 0;
        }
        if (simulate) {
            return accepted;
        }
        long energy = buffer.accept(packet.withAmount(accepted));
        lastIncomingSize = packet.size();
        setChanged();
        return energy / packet.size();
    }

    public void tick() {
        if (level == null || level.isClientSide() || !enabled || definition == null || buffer == null) {
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
            definition.recipe_map(),
            slots,
            0,
            INPUT_SLOTS,
            OUTPUT_START,
            inventory.getSlots(),
            Math.max(1, definition.parallel()),
            (int) energyProfile.minimumSize(),
            (int) energyProfile.maximumSize(),
            definition.parallel_duration(),
            definition.cheap_overclocking(),
            definition.efficiency()
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
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
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

    public void toggle() {
        enabled = !enabled;
        setChanged();
    }

    public void reset() {
        enabled = true;
        if (buffer != null) {
            buffer.restore(0);
        }
        plan = null;
        progress = 0;
        setChanged();
    }

    public ItemStack insertItem(ItemStack stack) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            stack = inventory.insertItem(slot, stack, false);
            if (stack.isEmpty()) {
                break;
            }
        }
        return stack;
    }

    public ItemStack extractOutput() {
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                return inventory.extractItem(slot, stack.getCount(), false);
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack peekOutputForTesting() {
        for (int slot = OUTPUT_START; slot < inventory.getSlots(); slot++) {
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

    public MachinePart part() {
        return part;
    }

    public GTMachineDefinition definition() {
        return definition;
    }

    public long progress() {
        return progress;
    }

    public long duration() {
        return plan == null ? 0 : plan.durationTicks();
    }

    public String activeRecipeSource() {
        return plan == null ? "" : plan.source() + ":" + plan.line();
    }

    public Component describe() {
        if (definition == null) {
            return Component.translatable("message.gregtech.machine.unresolved");
        }
        return Component.translatable(
            "message.gregtech.machine.generic_status",
            definition.display_name(),
            definition.energy_accepted(),
            definition.input(),
            definition.recipe_map(),
            enabled,
            buffer == null ? 0 : buffer.stored(),
            buffer == null ? 0 : buffer.capacity(),
            progress,
            duration(),
            lastIncomingSize,
            enabled ? activeRecipeSource() : ""
        );
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        progress = tag.getLong("progress");
        lastIncomingSize = tag.getLong("last_incoming_size");
        if (tag.contains("source_line")) {
            part = new MachinePart(tag.getInt("source_line"), tag.getString("category"));
            resolveDefinition();
            if (buffer != null) {
                buffer.restore(tag.getLong("energy"));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putBoolean("enabled", enabled);
        tag.putLong("progress", progress);
        tag.putLong("last_incoming_size", lastIncomingSize);
        if (part != null) {
            tag.putInt("source_line", part.sourceLine());
            tag.putString("category", part.category());
        }
        if (buffer != null) {
            tag.putLong("energy", buffer.stored());
        }
    }
}
