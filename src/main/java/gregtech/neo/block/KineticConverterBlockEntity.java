package gregtech.neo.block;

import gregtech.neo.core.EnergyBuffer;
import gregtech.neo.core.EnergyPacket;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class KineticConverterBlockEntity extends BlockEntity implements EnergyReceiver {
    private final KineticConverterKind kind;
    private final EnergyBuffer inputBuffer;
    private long lastAccepted;

    public KineticConverterBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.KINETIC_CONVERTER.get(), pos, state);
        this.kind = kindFor(state);
        this.inputBuffer = new EnergyBuffer(kind.inputProfile().kind(), kind.inputProfile().capacity());
    }

    private static KineticConverterKind kindFor(BlockState state) {
        if (state.is(GTBlocks.ELECTRIC_ENGINE.get())) {
            return KineticConverterKind.ELECTRIC_ENGINE;
        }
        if (state.is(GTBlocks.ROTATION_ENGINE.get())) {
            return KineticConverterKind.ROTATION_ENGINE;
        }
        return KineticConverterKind.ELECTRIC_MOTOR;
    }

    public void tick() {
        if (level == null || level.isClientSide() || inputBuffer.stored() < kind.inputProfile().recommendedSize()) {
            return;
        }
        Direction output = getBlockState().getValue(KineticConverterBlock.FACING);
        EnergyPacket outputPacket = new EnergyPacket(kind.outputKind(), kind.outputSize(), 1);
        MechanicalEnergyNetwork.Result simulation = MechanicalEnergyNetwork.distribute(
            level,
            worldPosition,
            output,
            outputPacket,
            true
        );
        if (simulation.acceptedAmperes() <= 0) {
            return;
        }
        inputBuffer.consume(kind.inputProfile().recommendedSize());
        MechanicalEnergyNetwork.Result result = MechanicalEnergyNetwork.distribute(
            level,
            worldPosition,
            output,
            outputPacket,
            false
        );
        if (result.acceptedAmperes() > 0) {
            lastAccepted = result.deliveredEnergy();
            setChanged();
        }
    }

    @Override
    public long receivePacket(EnergyPacket packet, Direction side, boolean simulate) {
        if (packet.kind() != kind.inputProfile().kind()) {
            return 0;
        }
        if (packet.size() > kind.inputProfile().maximumSize()) {
            if (!simulate && level != null) {
                level.explode(
                    null,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    1.5F,
                    Level.ExplosionInteraction.BLOCK
                );
            }
            return 0;
        }
        long acceptedAmperes = Math.min(packet.amount(), kind.inputProfile().maximumAmount());
        acceptedAmperes = Math.min(acceptedAmperes, inputBuffer.room() / packet.size());
        if (acceptedAmperes <= 0) {
            return 0;
        }
        if (simulate) {
            return acceptedAmperes;
        }
        long acceptedEnergy = inputBuffer.accept(packet.withAmount(acceptedAmperes));
        setChanged();
        return acceptedEnergy / packet.size();
    }

    public KineticConverterKind kind() {
        return kind;
    }

    public long storedEnergy() {
        return inputBuffer.stored();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inputBuffer.restore(tag.getLong("energy"));
        lastAccepted = tag.getLong("last_accepted");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("energy", inputBuffer.stored());
        tag.putLong("last_accepted", lastAccepted);
    }
}
