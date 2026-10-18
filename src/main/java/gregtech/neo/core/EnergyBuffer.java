package gregtech.neo.core;

public final class EnergyBuffer {
    private final EnergyKind kind;
    private final long capacity;
    private long stored;

    public EnergyBuffer(EnergyKind kind, long capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must not be negative");
        }
        this.kind = kind;
        this.capacity = capacity;
    }

    public long stored() {
        return stored;
    }

    public long capacity() {
        return capacity;
    }

    public long room() {
        return capacity - stored;
    }

    public long accept(EnergyPacket packet) {
        if (packet.kind() != kind) {
            return 0;
        }
        long accepted = Math.min(room(), packet.totalEnergy());
        stored += accepted;
        return accepted;
    }

    public long consume(long amount) {
        long consumed = Math.min(stored, Math.max(0, amount));
        stored -= consumed;
        return consumed;
    }

    public void restore(long amount) {
        stored = Math.max(0, Math.min(capacity, amount));
    }

    public EnergyBuffer copy() {
        EnergyBuffer copy = new EnergyBuffer(kind, capacity);
        copy.stored = stored;
        return copy;
    }
}
