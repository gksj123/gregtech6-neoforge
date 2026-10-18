package gregtech.neo.block;

import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.MachineEnergyProfile;

public enum KineticConverterKind {
    ELECTRIC_MOTOR(
        new MachineEnergyProfile(EnergyKind.ELECTRICITY, 16, 32, 64, 4, 128),
        EnergyKind.ROTATION,
        16
    ),
    ELECTRIC_ENGINE(
        new MachineEnergyProfile(EnergyKind.ELECTRICITY, 16, 32, 64, 4, 128),
        EnergyKind.KINETIC,
        16
    ),
    ROTATION_ENGINE(
        new MachineEnergyProfile(EnergyKind.ROTATION, 16, 32, 64, 4, 128),
        EnergyKind.KINETIC,
        16
    );

    private final MachineEnergyProfile inputProfile;
    private final EnergyKind outputKind;
    private final long outputSize;

    KineticConverterKind(MachineEnergyProfile inputProfile, EnergyKind outputKind, long outputSize) {
        this.inputProfile = inputProfile;
        this.outputKind = outputKind;
        this.outputSize = outputSize;
    }

    public MachineEnergyProfile inputProfile() {
        return inputProfile;
    }

    public EnergyKind outputKind() {
        return outputKind;
    }

    public long outputSize() {
        return outputSize;
    }
}
