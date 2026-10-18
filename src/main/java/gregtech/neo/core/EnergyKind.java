package gregtech.neo.core;

/**
 * GT6 keeps the physical meaning of an energy packet separate from its amount.
 * Size is not universally "voltage"; each kind gives the size a different meaning.
 */
public enum EnergyKind {
    ELECTRICITY("EU", "Electric Energy", "Voltage", "Amperage", true),
    ROTATION("RU", "Rotational Energy", "Speed", "Power", true),
    KINETIC("KU", "Kinetic Energy", "Push/Pull", "Power", true),
    HEAT("HU", "Heat Energy", "Direction", "Temperature", true),
    CRYO("CU", "Cryogenic Energy", "Direction", "Temperature", true),
    LIGHT("LU", "Light Energy", "Beam Size", "Frequency", true),
    MAGNETIC("MU", "Magnetic Energy", "Polarity", "Field Strength", true),
    NEUTRON("NU", "Neutron Energy", "Unused", "Temperature", true),
    QUANTUM("QU", "Quantum Energy", "Unknown", "Unknown", true),
    TIME("TU", "Time", "Unused", "Ticks", false),
    STEAM("SU", "Steam", "Unused", "Steam per Tick", true);

    private final String symbol;
    private final String displayName;
    private final String sizeName;
    private final String amountName;
    private final boolean exploding;

    EnergyKind(String symbol, String displayName, String sizeName, String amountName, boolean exploding) {
        this.symbol = symbol;
        this.displayName = displayName;
        this.sizeName = sizeName;
        this.amountName = amountName;
        this.exploding = exploding;
    }

    public String symbol() {
        return symbol;
    }

    public String displayName() {
        return displayName;
    }

    public String sizeName() {
        return sizeName;
    }

    public String amountName() {
        return amountName;
    }

    public boolean exploding() {
        return exploding;
    }
}
