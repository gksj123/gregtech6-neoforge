package gregtech.neo.block;

import gregtech.neo.core.EnergyPacket;
import gregtech.neo.core.WireSpec;
import gregtech.neo.registry.GTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import gregtech.neo.registry.GTBlockEntities;

public final class CableBlockEntity extends BlockEntity {
    private long lastVoltage;
    private long lastAmperage;
    private int overloadCount;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(state.is(GTBlocks.TIN_CABLE.get())
            ? GTBlockEntities.TIN_CABLE.get()
            : GTBlockEntities.COPPER_CABLE.get(), pos, state);
    }

    public WireSpec spec() {
        Block block = getBlockState().getBlock();
        if (block == GTBlocks.COPPER_CABLE.get()) {
            return new WireSpec("copper", 64, 4, 1);
        }
        return new WireSpec("tin", 32, 2, 1);
    }

    public void recordTransfer(EnergyPacket packet, WireSpec.Transmission transmission) {
        lastVoltage = packet.size();
        lastAmperage = packet.amount();
        if (transmission.overloaded()) {
            overloadCount++;
        }
    }

    public void burnOut(Level levelToUse) {
        if (levelToUse.isClientSide()) {
            return;
        }
        Block block = getBlockState().getBlock();
        levelToUse.setBlockAndUpdate(worldPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        Block.popResource(levelToUse, worldPosition, new ItemStack(block));
    }

    public long lastVoltage() {
        return lastVoltage;
    }

    public long lastAmperage() {
        return lastAmperage;
    }

    public int overloadCount() {
        return overloadCount;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        overloadCount = tag.getInt("overloads");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("overloads", overloadCount);
    }
}
