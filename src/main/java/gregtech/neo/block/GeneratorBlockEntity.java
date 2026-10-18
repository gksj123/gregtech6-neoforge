package gregtech.neo.block;

import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import gregtech.neo.registry.GTBlockEntities;

public final class GeneratorBlockEntity extends BlockEntity {
    private static final EnergyPacket PACKET = new EnergyPacket(EnergyKind.ELECTRICITY, 32, 2);

    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return fuelTicks(stack) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private boolean enabled = true;
    private int burnRemaining;
    private long generatedLastTick;
    private long deliveredLastTick;
    private int receiversLastTick;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.LV_GENERATOR.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide() || !enabled) {
            return;
        }
        if (burnRemaining <= 0) {
            ItemStack fuel = inventory.getStackInSlot(0);
            int ticks = fuelTicks(fuel);
            if (ticks <= 0) {
                generatedLastTick = 0;
                deliveredLastTick = 0;
                receiversLastTick = 0;
                return;
            }
            fuel.shrink(1);
            if (fuel.isEmpty()) {
                inventory.setStackInSlot(0, ItemStack.EMPTY);
            }
            burnRemaining = ticks;
            setChanged();
        }

        EnergyNetwork.Result result = EnergyNetwork.distribute(level, worldPosition, PACKET);
        generatedLastTick = PACKET.totalEnergy();
        deliveredLastTick = result.deliveredEnergy();
        receiversLastTick = result.receivers();
        if (result.acceptedAmperes() > 0) {
            burnRemaining--;
            if (level.getGameTime() % 20 == 0) {
                setChanged();
            }
        }
    }

    public ItemStack insertFuel(ItemStack stack) {
        return inventory.insertItem(0, stack, false);
    }

    public boolean isFuel(ItemStack stack) {
        return fuelTicks(stack) > 0;
    }

    public void toggleEnabled() {
        enabled = !enabled;
        setChanged();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void resetReadings() {
        generatedLastTick = 0;
        deliveredLastTick = 0;
        receiversLastTick = 0;
    }

    public void describe(Player player) {
        InWorldMessages.send(player, Component.translatable(
            "message.gregtech.generator.status",
            enabled,
            burnRemaining,
            PACKET.size(),
            PACKET.amount(),
            generatedLastTick,
            deliveredLastTick,
            receiversLastTick
        ));
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
        if (stack.is(Items.STICK) || stack.is(Items.BAMBOO)) {
            return 100;
        }
        return 0;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        burnRemaining = tag.getInt("burn");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putBoolean("enabled", enabled);
        tag.putInt("burn", burnRemaining);
    }
}
