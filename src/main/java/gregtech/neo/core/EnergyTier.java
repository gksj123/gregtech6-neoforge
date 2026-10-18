package gregtech.neo.core;

import java.util.OptionalLong;

/**
 * GT6 voltage tiers. Nominal values follow the original 8/32/128 progression.
 * The upper bounds preserve GT6's tolerance for one tier step above nominal.
 */
public enum EnergyTier {
    ULV(8, 16),
    LV(32, 64),
    MV(128, 256),
    HV(512, 1024),
    EV(2048, 4096),
    IV(8192, 16384),
    LUV(32768, 65536),
    ZPM(131072, 262144),
    UV(524288, 1048576),
    MAX(8589934592L, 17179869184L);

    private final long nominalVoltage;
    private final long maximumVoltage;

    EnergyTier(long nominalVoltage, long maximumVoltage) {
        this.nominalVoltage = nominalVoltage;
        this.maximumVoltage = maximumVoltage;
    }

    public long nominalVoltage() {
        return nominalVoltage;
    }

    public long maximumVoltage() {
        return maximumVoltage;
    }

    public static EnergyTier forVoltage(long voltage) {
        long absolute = Math.abs(voltage);
        for (EnergyTier tier : values()) {
            if (absolute <= tier.maximumVoltage) {
                return tier;
            }
        }
        return MAX;
    }

    public static OptionalLong nominalVoltageOf(EnergyTier tier) {
        return tier == null ? OptionalLong.empty() : OptionalLong.of(tier.nominalVoltage);
    }
}
