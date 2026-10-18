package gregtech.neo.core;

import java.util.Objects;

/**
 * Physical input limits for one energy family. These values deliberately keep
 * GT6's distinction between size (voltage/speed) and packet amount.
 */
public record MachineEnergyProfile(
    EnergyKind kind,
    long minimumSize,
    long recommendedSize,
    long maximumSize,
    long maximumAmount,
    long capacity
) {
    public MachineEnergyProfile {
        Objects.requireNonNull(kind, "kind");
        if (minimumSize <= 0
            || recommendedSize < minimumSize
            || maximumSize < recommendedSize
            || maximumAmount <= 0
            || capacity < minimumSize) {
            throw new IllegalArgumentException("Invalid machine energy profile");
        }
    }

    public boolean acceptsKind(EnergyPacket packet) {
        return packet.kind() == kind && packet.size() <= maximumSize;
    }

    public static MachineEnergyProfile kineticCrusher() {
        return new MachineEnergyProfile(EnergyKind.KINETIC, 16, 32, 64, 4, 64);
    }

    public static MachineEnergyProfile largeCrusher() {
        return new MachineEnergyProfile(EnergyKind.ROTATION, 512, 512, 4096, 64, 4096);
    }
}
