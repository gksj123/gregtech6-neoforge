package gregtech.neo.core;

public enum PortMode {
    DISABLED,
    ENERGY_IN,
    ITEM_IN,
    ITEM_OUT,
    FLUID_IN,
    FLUID_OUT;

    public PortMode next() {
        PortMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public boolean itemInput() {
        return this == ITEM_IN;
    }

    public boolean itemOutput() {
        return this == ITEM_OUT;
    }

    public boolean energyInput() {
        return this == ENERGY_IN;
    }
}
