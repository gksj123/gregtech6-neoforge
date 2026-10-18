package gregtech.neo.block;

import gregtech.neo.core.PortMode;
import net.minecraft.util.StringRepresentable;

public enum MachinePortMode implements StringRepresentable {
    DISABLED(PortMode.DISABLED),
    ENERGY_IN(PortMode.ENERGY_IN),
    ITEM_IN(PortMode.ITEM_IN),
    ITEM_OUT(PortMode.ITEM_OUT),
    FLUID_IN(PortMode.FLUID_IN),
    FLUID_OUT(PortMode.FLUID_OUT);

    private final PortMode mode;

    MachinePortMode(PortMode mode) {
        this.mode = mode;
    }

    public PortMode mode() {
        return mode;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
