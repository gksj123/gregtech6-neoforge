package gregtech.neo.block;

import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialStorageBlockEntity extends BlockEntity {
    private String material = "";
    private String prefix = "blockSolid";

    public MaterialStorageBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.MATERIAL_STORAGE.get(), pos, state);
        if (state.getBlock() instanceof MaterialStorageBlock block) {
            prefix = block.prefix();
        }
    }

    public void setPart(MaterialPart part) {
        material = part.material();
        prefix = part.prefix();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public MaterialPart part() {
        return material.isBlank() ? null : new MaterialPart(material, prefix);
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
        material = tag.getString("material");
        prefix = tag.contains("prefix") ? tag.getString("prefix") : prefix;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("material", material);
        tag.putString("prefix", prefix);
    }
}
