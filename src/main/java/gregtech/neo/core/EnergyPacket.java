package gregtech.neo.core;

import java.util.Objects;

/**
 * A packet has a size (voltage for EU) and a count of packets (amperage for EU).
 * Energy cannot be represented correctly by a single "EU" number alone.
 */
public record EnergyPacket(EnergyKind kind, long size, long amount) {
    public EnergyPacket {
        Objects.requireNonNull(kind, "kind");
        if (size <= 0) {
            throw new IllegalArgumentException("Packet size must be positive");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Packet amount must be positive");
        }
    }

    public long totalEnergy() {
        return saturatedMultiply(size, amount);
    }

    public EnergyTier tier() {
        return EnergyTier.forVoltage(size);
    }

    public EnergyPacket withSize(long newSize) {
        return new EnergyPacket(kind, newSize, amount);
    }

    public EnergyPacket withAmount(long newAmount) {
        return new EnergyPacket(kind, size, newAmount);
    }

    public static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(Math.abs(left), Math.abs(right));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    public static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0 && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }
}
