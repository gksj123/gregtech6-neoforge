package gregtech.neo.block;

import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * GT6 1.7.10: gregtech/loaders/b/Loader_MultiTileEntities.java:1159-1165, 1248-1252.
 *
 * The 1.7.10 dense wall was a metadata variant of the shared multitile. The
 * modern registration gives it a dedicated block while preserving the wall
 * material and lower-water/upper-steam port roles.
 */
public final class LargeBoilerWallBlockEntity extends BlockEntity {
    private String material = "";

    public LargeBoilerWallBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.LARGE_BOILER_WALL.get(), pos, state);
    }

    public void setPart(MaterialPart part) {
        material = part.material();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public String material() {
        return material;
    }

    public MaterialPart part() {
        return material.isBlank() ? null : new MaterialPart(material, "casingMachineDense");
    }

    public LargeBoilerBlockEntity controller() {
        if (level == null) {
            return null;
        }
        for (BlockPos candidate : BlockPos.betweenClosed(
            worldPosition.offset(-3, -3, -3),
            worldPosition.offset(3, 3, 3)
        )) {
            if (level.getBlockEntity(candidate) instanceof LargeBoilerBlockEntity boiler
                && boiler.isPart(worldPosition)) {
                return boiler;
            }
        }
        return null;
    }

    public IFluidHandler fluidHandler(Direction side) {
        LargeBoilerBlockEntity boiler = controller();
        if (boiler == null || !boiler.isFormed()) {
            return null;
        }
        return switch (boiler.fluidRoleAt(worldPosition)) {
            case WATER_IN -> boiler.waterInputHandler();
            case STEAM_OUT -> boiler.steamOutputHandler();
            case NONE -> null;
        };
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
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("material", material);
    }
}
