package gregtech.neo.block;

import gregtech.neo.core.EnergyBuffer;
import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import gregtech.neo.machine.GTMachineDefinition;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialPrefix;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class SmallCrucibleBlockEntity extends BlockEntity implements EnergyReceiver, TemperatureProvider {
    public static final long MAX_AMOUNT = 16L * GTMaterialGameplayRegistry.MATERIAL_UNIT;
    private static final long ENVIRONMENT_TEMPERATURE = 293L;
    private static final long HEAT_CAPACITY = 100_000_000_000L;
    private static final long KG_PER_HEAT_UNIT = 100L;
    private static final double HEAT_RESISTANCE_BONUS = 1.25D;

    private final EnergyBuffer heatBuffer = new EnergyBuffer(EnergyKind.HEAT, HEAT_CAPACITY);
    private final List<Content> contents = new ArrayList<>();
    private MachinePart part;
    private GTMachineDefinition definition;
    private String wallMaterial = "stone";
    private long temperature = ENVIRONMENT_TEMPERATURE;
    private boolean enabled = true;

    public SmallCrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.SMALL_CRUCIBLE.get(), pos, state);
    }

    public void setPart(MachinePart part) {
        this.part = part;
        definition = GTMachineRegistry.find(part.category(), part.sourceLine());
        wallMaterial = definition == null || definition.material_slug().isBlank()
            ? "stone"
            : definition.material_slug();
        contents.clear();
        heatBuffer.restore(0);
        temperature = ENVIRONMENT_TEMPERATURE;
        setChanged();
    }

    public void tick() {
        if (level == null || level.isClientSide() || !enabled) {
            return;
        }
        if (level.getGameTime() % 20 == 0) {
            coolTowardEnvironment();
            processPhaseChanges();
        }
        applyHeat();
        if (temperature > maximumTemperature()) {
            meltDown();
        } else if (level.getGameTime() % 20 == 0) {
            setChanged();
        }
    }

    private void applyHeat() {
        long required = requiredHeatPerKelvin();
        long possible = heatBuffer.stored() / required;
        if (possible <= 0) {
            return;
        }
        long increase = Math.min(possible, Math.max(0, maximumTemperature() + 1 - temperature));
        if (increase > 0) {
            heatBuffer.consume(increase * required);
            temperature += increase;
        }
    }

    private void coolTowardEnvironment() {
        if (temperature > ENVIRONMENT_TEMPERATURE) {
            temperature--;
        } else if (temperature < ENVIRONMENT_TEMPERATURE) {
            temperature++;
        }
        temperature = Math.max(ENVIRONMENT_TEMPERATURE, temperature);
    }

    private void processPhaseChanges() {
        for (int index = contents.size() - 1; index >= 0; index--) {
            Content content = contents.get(index);
            GTMaterialGameplayRegistry.MaterialGameplay gameplay =
                GTMaterialGameplayRegistry.material(content.material);
            if (gameplay == null || content.units <= 0 || temperature >= gameplay.boilingKelvin()) {
                contents.remove(index);
            }
        }
    }

    public boolean insertItem(ItemStack stack) {
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        return part != null && addMaterial(part.material(), part.prefix(), 1);
    }

    public boolean addMaterial(String material, String prefixId, int itemCount) {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(material);
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(prefixId);
        if (gameplay == null || prefix == null || prefix.amount() <= 0 || itemCount <= 0) {
            return false;
        }
        long units = prefix.amount() * itemCount;
        if (units <= 0 || totalUnits() > MAX_AMOUNT - units) {
            return false;
        }
        double oldMass = thermalMassKilograms();
        double incomingMass = gameplay.weightKilograms(units);
        if (oldMass + incomingMass > 0) {
            double difference = temperature - ENVIRONMENT_TEMPERATURE;
            long mixed = ENVIRONMENT_TEMPERATURE + signRound(
                difference * oldMass / (oldMass + incomingMass)
            );
            temperature = mixed;
        }
        addContent(material, units);
        setChanged();
        return true;
    }

    private static long signRound(double value) {
        return value < 0 ? -(long) Math.floor(-value) : (long) Math.floor(value);
    }

    private void addContent(String material, long units) {
        for (Content content : contents) {
            if (content.material.equals(material)) {
                content.units += units;
                return;
            }
        }
        contents.add(new Content(material, units));
    }

    public long requiredHeatPerKelvin() {
        return 1L + (long) (thermalMassKilograms() / KG_PER_HEAT_UNIT);
    }

    public double thermalMassKilograms() {
        double total = 0;
        GTMaterialGameplayRegistry.MaterialGameplay wall =
            GTMaterialGameplayRegistry.material(wallMaterial);
        if (wall != null) {
            total += wall.weightKilograms(GTMaterialGameplayRegistry.MATERIAL_UNIT * 7L);
        }
        for (Content content : contents) {
            GTMaterialGameplayRegistry.MaterialGameplay gameplay =
                GTMaterialGameplayRegistry.material(content.material);
            if (gameplay != null) {
                total += gameplay.weightKilograms(content.units);
            }
        }
        return total;
    }

    public long maximumTemperature() {
        GTMaterialGameplayRegistry.MaterialGameplay wall =
            GTMaterialGameplayRegistry.material(wallMaterial);
        double melting = wall == null ? 1000 : wall.meltingKelvin();
        return (long) (melting * HEAT_RESISTANCE_BONUS);
    }

    public long totalUnits() {
        long total = 0;
        for (Content content : contents) {
            total += content.units;
        }
        return total;
    }

    public long moltenUnits() {
        long total = 0;
        for (Content content : contents) {
            GTMaterialGameplayRegistry.MaterialGameplay gameplay =
                GTMaterialGameplayRegistry.material(content.material);
            if (gameplay != null && temperature >= gameplay.meltingKelvin()) {
                total += content.units;
            }
        }
        return total;
    }

    @Override
    public long receivePacket(EnergyPacket packet, Direction side, boolean simulate) {
        if (!enabled || packet.kind() != EnergyKind.HEAT) {
            return 0;
        }
        long accepted = Math.min(packet.amount(), heatBuffer.room() / packet.size());
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            heatBuffer.accept(packet.withAmount(accepted));
            setChanged();
        }
        return accepted;
    }

    private void meltDown() {
        contents.clear();
        heatBuffer.restore(0);
        if (level != null) {
            level.setBlockAndUpdate(worldPosition, Blocks.LAVA.defaultBlockState());
        }
    }

    public void toggle() {
        enabled = !enabled;
        setChanged();
    }

    public GTMachineDefinition definition() {
        return definition;
    }

    public long temperature() {
        return temperature;
    }

    public long storedHeat() {
        return heatBuffer.stored();
    }

    @Override
    public long temperatureKelvin() {
        return temperature;
    }

    @Override
    public long maximumTemperatureKelvin() {
        return maximumTemperature();
    }

    @Override
    public String temperatureSourceName() {
        return definition == null ? "Smelting Crucible" : definition.display_name();
    }

    public Component describe() {
        return Component.translatable(
            "message.gregtech.small_crucible.status",
            definition == null ? "Unassigned" : definition.display_name(),
            enabled,
            temperature,
            maximumTemperature(),
            heatBuffer.stored(),
            requiredHeatPerKelvin(),
            thermalMassKilograms(),
            totalUnits(),
            MAX_AMOUNT,
            moltenUnits()
        );
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        temperature = tag.contains("temperature") ? tag.getLong("temperature") : ENVIRONMENT_TEMPERATURE;
        heatBuffer.restore(tag.getLong("heat"));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        if (tag.contains("source_line")) {
            part = new MachinePart(tag.getInt("source_line"), tag.getString("category"));
            setPart(part);
            temperature = tag.getLong("temperature");
            heatBuffer.restore(tag.getLong("heat"));
        }
        contents.clear();
        ListTag list = tag.getList("contents", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            contents.add(new Content(entry.getString("material"), entry.getLong("units")));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("temperature", temperature);
        tag.putLong("heat", heatBuffer.stored());
        tag.putBoolean("enabled", enabled);
        if (part != null) {
            tag.putInt("source_line", part.sourceLine());
            tag.putString("category", part.category());
        }
        ListTag list = new ListTag();
        for (Content content : contents) {
            CompoundTag entry = new CompoundTag();
            entry.putString("material", content.material);
            entry.putLong("units", content.units);
            list.add(entry);
        }
        tag.put("contents", list);
    }

    public static final class Content {
        private final String material;
        private long units;

        private Content(String material, long units) {
            this.material = material;
            this.units = units;
        }

        public String material() {
            return material;
        }

        public long units() {
            return units;
        }
    }
}
