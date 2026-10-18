package gregtech.neo.block;

import gregtech.neo.core.EnergyPacket;
import net.minecraft.core.Direction;

public interface EnergyReceiver {
    long receivePacket(EnergyPacket packet, Direction side, boolean simulate);
}
