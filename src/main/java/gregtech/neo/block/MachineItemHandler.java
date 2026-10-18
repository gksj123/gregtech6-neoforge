package gregtech.neo.block;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Exposes GT machine input and output ranges without granting automation
 * permission to pull ingredients back out or push finished products back in.
 */
public final class MachineItemHandler implements IItemHandler {
    private final IItemHandler backing;
    private final int inputStart;
    private final int inputEnd;
    private final int outputStart;
    private final int outputEnd;
    private final boolean exposeInputs;
    private final boolean exposeOutputs;

    private MachineItemHandler(
        IItemHandler backing,
        int inputStart,
        int inputEnd,
        int outputStart,
        int outputEnd,
        boolean exposeInputs,
        boolean exposeOutputs
    ) {
        this.backing = backing;
        this.inputStart = inputStart;
        this.inputEnd = inputEnd;
        this.outputStart = outputStart;
        this.outputEnd = outputEnd;
        this.exposeInputs = exposeInputs;
        this.exposeOutputs = exposeOutputs;
    }

    public static MachineItemHandler combined(
        IItemHandler backing,
        int inputStart,
        int inputEnd,
        int outputStart,
        int outputEnd
    ) {
        return new MachineItemHandler(backing, inputStart, inputEnd, outputStart, outputEnd, true, true);
    }

    public static MachineItemHandler inputs(IItemHandler backing, int start, int end) {
        return new MachineItemHandler(backing, start, end, start, start, true, false);
    }

    public static MachineItemHandler outputs(IItemHandler backing, int start, int end) {
        return new MachineItemHandler(backing, start, start, start, end, false, true);
    }

    @Override
    public int getSlots() {
        return (exposeInputs ? inputEnd - inputStart : 0)
            + (exposeOutputs ? outputEnd - outputStart : 0);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        int mapped = mapSlot(slot);
        return mapped < 0 ? ItemStack.EMPTY : backing.getStackInSlot(mapped);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isInputSlot(slot)) {
            return stack;
        }
        int mapped = mapSlot(slot);
        return mapped < 0 ? stack : backing.insertItem(mapped, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!isOutputSlot(slot)) {
            return ItemStack.EMPTY;
        }
        int mapped = mapSlot(slot);
        return mapped < 0 ? ItemStack.EMPTY : backing.extractItem(mapped, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int mapped = mapSlot(slot);
        return mapped < 0 ? 0 : backing.getSlotLimit(mapped);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!isInputSlot(slot)) {
            return false;
        }
        int mapped = mapSlot(slot);
        return mapped >= 0 && backing.isItemValid(mapped, stack);
    }

    private boolean isInputSlot(int slot) {
        return exposeInputs && slot >= 0 && slot < inputEnd - inputStart;
    }

    private boolean isOutputSlot(int slot) {
        int inputSlots = exposeInputs ? inputEnd - inputStart : 0;
        return exposeOutputs
            && slot >= inputSlots
            && slot < inputSlots + outputEnd - outputStart;
    }

    private int mapSlot(int slot) {
        if (isInputSlot(slot)) {
            return inputStart + slot;
        }
        int inputSlots = exposeInputs ? inputEnd - inputStart : 0;
        if (isOutputSlot(slot)) {
            return outputStart + slot - inputSlots;
        }
        return -1;
    }
}
