package gregtech.neo.core;

import java.util.Objects;

public record WireSpec(String id, long maximumVoltage, long maximumAmperage, long voltageLossPerBlock) {
    public WireSpec {
        Objects.requireNonNull(id, "id");
        if (maximumVoltage <= 0 || maximumAmperage <= 0 || voltageLossPerBlock < 0) {
            throw new IllegalArgumentException("Invalid wire specification");
        }
    }

    public Transmission transfer(EnergyPacket packet) {
        if (packet.size() > maximumVoltage || packet.amount() > maximumAmperage) {
            return Transmission.overloaded(this, packet);
        }
        long deliveredSize = packet.size() - voltageLossPerBlock;
        if (deliveredSize <= 0) {
            return Transmission.fullyLost(this, packet);
        }
        EnergyPacket delivered = packet.withSize(deliveredSize);
        long loss = EnergyPacket.saturatedMultiply(packet.size() - deliveredSize, packet.amount());
        return new Transmission(this, packet, delivered, loss, false, false);
    }

    public record Transmission(
        WireSpec wire,
        EnergyPacket input,
        EnergyPacket output,
        long energyLoss,
        boolean overloaded,
        boolean fullyLost
    ) {
        private static Transmission overloaded(WireSpec wire, EnergyPacket packet) {
            return new Transmission(wire, packet, new EnergyPacket(packet.kind(), 1, 1), packet.totalEnergy(), true, true);
        }

        private static Transmission fullyLost(WireSpec wire, EnergyPacket packet) {
            return new Transmission(wire, packet, null, packet.totalEnergy(), false, true);
        }
    }
}
