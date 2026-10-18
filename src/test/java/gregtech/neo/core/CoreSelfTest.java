package gregtech.neo.core;

public final class CoreSelfTest {
    private CoreSelfTest() {
    }

    public static void main(String[] args) {
        energyPacketKeepsSizeAndAmount();
        wireLosesVoltageBeforeDeliveringCurrent();
        wireOverloadIsDetected();
        bufferNeverOverfills();
        portModeCyclesWithoutGuiState();
        crusherRecipesHavePositiveCosts();
        machineProfilesDoNotMixEnergyFamilies();
        System.out.println("GT6 Neo core self-test passed");
    }

    private static void energyPacketKeepsSizeAndAmount() {
        EnergyPacket packet = new EnergyPacket(EnergyKind.ELECTRICITY, 32, 4);
        check(packet.totalEnergy() == 128, "packet energy");
        check(packet.tier() == EnergyTier.LV, "LV tier");
    }

    private static void wireLosesVoltageBeforeDeliveringCurrent() {
        WireSpec wire = new WireSpec("tin", 32, 2, 1);
        WireSpec.Transmission result = wire.transfer(new EnergyPacket(EnergyKind.ELECTRICITY, 32, 2));
        check(!result.overloaded(), "wire should not overload");
        check(result.output().size() == 31, "voltage drop");
        check(result.output().amount() == 2, "amperage preserved");
        check(result.energyLoss() == 2, "loss equals voltage drop times amperage");
    }

    private static void wireOverloadIsDetected() {
        WireSpec wire = new WireSpec("tin", 32, 1, 1);
        check(wire.transfer(new EnergyPacket(EnergyKind.ELECTRICITY, 64, 1)).overloaded(), "overvoltage");
        check(wire.transfer(new EnergyPacket(EnergyKind.ELECTRICITY, 32, 2)).overloaded(), "overcurrent");
    }

    private static void bufferNeverOverfills() {
        EnergyBuffer buffer = new EnergyBuffer(EnergyKind.ELECTRICITY, 100);
        check(buffer.accept(new EnergyPacket(EnergyKind.ELECTRICITY, 32, 2)) == 64, "first packet");
        check(buffer.accept(new EnergyPacket(EnergyKind.ELECTRICITY, 32, 2)) == 36, "partial packet");
        check(buffer.stored() == 100, "buffer capacity");
        check(buffer.accept(new EnergyPacket(EnergyKind.HEAT, 32, 1)) == 0, "energy type mismatch");
        buffer.restore(25);
        check(buffer.stored() == 25, "buffer restore");
        buffer.restore(-1);
        check(buffer.stored() == 0, "buffer restore clamp");
    }

    private static void portModeCyclesWithoutGuiState() {
        PortMode mode = PortMode.DISABLED;
        for (int i = 0; i < PortMode.values().length; i++) {
            mode = mode.next();
        }
        check(mode == PortMode.DISABLED, "port mode must cycle back to disabled");
    }

    private static void crusherRecipesHavePositiveCosts() {
        check(!CrusherRecipe.BUILTIN.isEmpty(), "built-in recipes");
        for (CrusherRecipe recipe : CrusherRecipe.BUILTIN) {
            check(recipe.totalEnergy() > 0, "recipe energy");
            check(recipe.minimumVoltage() >= EnergyTier.LV.nominalVoltage(), "recipe tier");
        }
    }

    private static void machineProfilesDoNotMixEnergyFamilies() {
        MachineEnergyProfile kinetic = MachineEnergyProfile.kineticCrusher();
        MachineEnergyProfile rotation = MachineEnergyProfile.largeCrusher();
        check(kinetic.acceptsKind(new EnergyPacket(EnergyKind.KINETIC, 32, 1)), "KU crusher accepts KU");
        check(!kinetic.acceptsKind(new EnergyPacket(EnergyKind.ROTATION, 32, 1)), "KU crusher rejects RU");
        check(!kinetic.acceptsKind(new EnergyPacket(EnergyKind.LIGHT, 32, 1)), "KU crusher rejects LU");
        check(rotation.acceptsKind(new EnergyPacket(EnergyKind.ROTATION, 512, 1)), "RU crusher accepts RU");
        check(!rotation.acceptsKind(new EnergyPacket(EnergyKind.KINETIC, 512, 1)), "RU crusher rejects KU");
        check(!rotation.acceptsKind(new EnergyPacket(EnergyKind.ELECTRICITY, 512, 1)), "RU crusher rejects EU");
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Self-test failed: " + label);
        }
    }
}
