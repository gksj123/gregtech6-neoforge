package gregtech.neo.block;

import gregtech.neo.core.EnergyPacket;
import gregtech.neo.core.MachineEnergyProfile;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class BasicCrusherBlockEntity extends AbstractCrusherBlockEntity {
    public BasicCrusherBlockEntity(BlockPos pos, BlockState state) {
        super(
            GTBlockEntities.BASIC_CRUSHER.get(),
            pos,
            state,
            MachineEnergyProfile.kineticCrusher()
        );
    }

    public void tick() {
        tickProcessing();
    }

    @Override
    protected int parallelism() {
        return 4;
    }

    @Override
    protected boolean isFormedForProcessing() {
        return true;
    }

    @Override
    protected void onEnergySizeExceeded(EnergyPacket packet) {
        if (level != null) {
            level.explode(
                null,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5,
                1.5F,
                Level.ExplosionInteraction.BLOCK
            );
        }
    }

    public void describe(Player player, boolean detailed) {
        if (!detailed) {
            InWorldMessages.send(player, Component.translatable(
                "message.gregtech.crusher.status",
                isActive(),
                true,
                "KU",
                storedEnergy(),
                progress(),
                duration()
            ));
            return;
        }
        InWorldMessages.send(player, Component.translatable(
            "message.gregtech.crusher.detail",
            isActive(),
            true,
            "KU",
            storedEnergy(),
            capacity(),
            progress(),
            duration(),
            lastIncomingSize(),
            energyProfile().minimumSize(),
            energyProfile().maximumSize(),
            energyProfile().maximumAmount(),
            inputStack().getHoverName(),
            outputStack().getHoverName()
        ));
    }
}
