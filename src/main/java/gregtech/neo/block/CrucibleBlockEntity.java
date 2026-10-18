package gregtech.neo.block;

import gregtech.neo.core.EnergyBuffer;
import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import gregtech.neo.material.GTMaterial;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialItems;
import gregtech.neo.material.GTMaterialPrefix;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTBlocks;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CrucibleBlockEntity extends BlockEntity implements EnergyReceiver, TemperatureProvider {
    public static final long ENVIRONMENT_TEMPERATURE = 293L;
    public static final long MAX_AMOUNT = 16L * 3L * 3L * 3L
        * GTMaterialGameplayRegistry.MATERIAL_UNIT;
    private static final long HEAT_CAPACITY = 1_000_000_000_000L;
    private static final long KG_PER_HEAT_UNIT = 100L;
    private static final double HEAT_RESISTANCE_BONUS = 1.10D;

    private final EnergyBuffer heatBuffer = new EnergyBuffer(EnergyKind.HEAT, HEAT_CAPACITY);
    private final List<Content> contents = new ArrayList<>();
    private String wallMaterial = "steel";
    private long temperature = ENVIRONMENT_TEMPERATURE;
    private long oxygenUnits;
    private long lastIncomingSize;
    private int structureCooldown;
    private boolean formed;
    private boolean enabled = true;
    private String lastVisualKey = "";

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.CRUCIBLE.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (--structureCooldown <= 0) {
            structureCooldown = 20;
            formed = structureIsValid();
        }
        if (!formed) {
            return;
        }
        if (level.getGameTime() % 20 == 0) {
            coolTowardEnvironment();
            processPhaseChanges();
            damageEntitiesNearContent();
        }
        absorbNearbyItems();
        processOxygenSteelmaking();
        collectRainwater();
        syncVisualState();
        applyHeat();
        if (temperature > maximumTemperature()) {
            meltDown();
        }
        if (level.getGameTime() % 20 == 0) {
            setChanged();
        }
    }

    private void applyHeat() {
        long requiredPerKelvin = requiredHeatPerKelvin();
        long possible = heatBuffer.stored() / requiredPerKelvin;
        if (possible <= 0) {
            return;
        }
        long maximumKelvin = Math.max(0, maximumTemperature() + 1 - temperature);
        long increase = Math.min(possible, maximumKelvin);
        if (increase <= 0) {
            return;
        }
        heatBuffer.consume(increase * requiredPerKelvin);
        temperature += increase;
    }

    private void coolTowardEnvironment() {
        if (temperature > ENVIRONMENT_TEMPERATURE) {
            temperature--;
        } else if (temperature < ENVIRONMENT_TEMPERATURE) {
            temperature++;
        }
        temperature = Math.max(temperature, Math.max(200L, ENVIRONMENT_TEMPERATURE));
    }

    private void processPhaseChanges() {
        for (int index = contents.size() - 1; index >= 0; index--) {
            Content content = contents.get(index);
            GTMaterialGameplayRegistry.MaterialGameplay gameplay =
                GTMaterialGameplayRegistry.material(content.material);
            if (gameplay == null) {
                continue;
            }
            if (temperature >= gameplay.boilingKelvin()) {
                boilingEffect(gameplay, content.units);
                contents.remove(index);
            } else if (content.units <= 0) {
                contents.remove(index);
            }
        }
    }

    private void boilingEffect(GTMaterialGameplayRegistry.MaterialGameplay gameplay, long units) {
        if (level == null) {
            return;
        }
        level.playSound(
            null,
            worldPosition,
            SoundEvents.LAVA_EXTINGUISH,
            SoundSource.BLOCKS,
            0.7F,
            1.2F
        );
        if (gameplay.boilingKelvin() >= 320) {
            for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(worldPosition).inflate(5.0)
            )) {
                entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 40));
                entity.hurt(level.damageSources().onFire(), 1.0F);
            }
        }
        if (gameplay.boilingKelvin() >= 2000) {
            int attempts = (int) Math.min(9,
                Math.max(1, units / GTMaterialGameplayRegistry.MATERIAL_UNIT));
            for (int attempt = 0; attempt < attempts; attempt++) {
                BlockPos candidate = worldPosition.offset(
                    level.random.nextInt(11) - 5,
                    level.random.nextInt(4) - 1,
                    level.random.nextInt(11) - 5
                );
                if (level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.below()).isSolid()) {
                    level.setBlockAndUpdate(
                        candidate,
                        net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState()
                    );
                }
            }
        }
    }

    public boolean insertItem(ItemStack stack) {
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        if (part == null || !addMaterial(part.material(), part.prefix(), 1)) {
            return false;
        }
        return true;
    }

    private void absorbNearbyItems() {
        if (level == null) {
            return;
        }
        AABB intake = new AABB(
            worldPosition.getX() - 0.5,
            worldPosition.getY() + 0.125,
            worldPosition.getZ() - 0.5,
            worldPosition.getX() + 1.5,
            worldPosition.getY() + 3.125,
            worldPosition.getZ() + 1.5
        );
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, intake)) {
            ItemStack stack = item.getItem();
            if (!insertItem(stack)) {
                continue;
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(1);
            if (remainder.isEmpty()) {
                item.discard();
            } else {
                item.setItem(remainder);
            }
            setChanged();
            return;
        }
    }

    public boolean addMaterial(String material, String prefixId, int itemCount) {
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(prefixId);
        if (prefix == null || prefix.amount() <= 0 || itemCount <= 0) {
            return false;
        }
        return addRawMaterial(material, prefix.amount() * itemCount);
    }

    private boolean addRawMaterial(String material, long units) {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(material);
        if (units <= 0 || totalUnits() > MAX_AMOUNT - units) {
            return false;
        }
        if (gameplay == null) {
            return false;
        }

        double oldMass = thermalMassKilograms();
        double incomingMass = gameplay.weightKilograms(units);
        if (oldMass + incomingMass > 0) {
            temperature = ENVIRONMENT_TEMPERATURE
                + directionalRound(temperature - ENVIRONMENT_TEMPERATURE, oldMass, oldMass + incomingMass);
        }
        addContent(material, units);
        setChanged();
        return true;
    }

    private void collectRainwater() {
        if (level == null || level.getGameTime() % 600 != 10
            || !level.isRainingAt(worldPosition.above(2))) {
            return;
        }
        long units = Math.max(1, GTMaterialGameplayRegistry.MATERIAL_UNIT / 1000);
        if (level.isThundering()) {
            units *= 2;
        }
        addRawMaterial("water", units);
    }

    private static long directionalRound(double difference, double numerator, double denominator) {
        if (denominator <= 0) {
            return 0;
        }
        double scaled = Math.abs(difference) * numerator / denominator;
        long rounded = (long) Math.floor(scaled);
        return difference < 0 ? -rounded : rounded;
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
            total += wall.weightKilograms(GTMaterialGameplayRegistry.MATERIAL_UNIT * 100L);
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
        double melting = wall == null ? 2046 : wall.meltingKelvin();
        return (long) (melting * HEAT_RESISTANCE_BONUS);
    }

    public long temperature() {
        return temperature;
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
        return "Large Crucible";
    }

    public long storedHeat() {
        return heatBuffer.stored();
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

    private void processOxygenSteelmaking() {
        if (oxygenUnits <= 0 || temperature < 2046) {
            return;
        }
        for (Content content : contents) {
            String target = switch (content.material) {
                case "iron", "wroughtiron" -> "steel";
                case "meteoriciron" -> "meteoricsteel";
                default -> "";
            };
            if (target.isBlank() || content.units <= 0) {
                continue;
            }
            long converted = Math.min(content.units, oxygenUnits);
            if (converted <= 0) {
                continue;
            }
            content.units -= converted;
            oxygenUnits -= converted;
            addContent(target, converted);
            setChanged();
            break;
        }
    }

    public int fillLevel() {
        if (totalUnits() <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(8,
            (int) Math.ceil(totalUnits() * 8.0 / MAX_AMOUNT)));
    }

    public float fillFraction() {
        return fillLevel() / 8.0F;
    }

    public String contentMaterial() {
        return contents.stream()
            .filter(content -> GTMaterialGameplayRegistry.material(content.material) != null)
            .min(Comparator.comparingDouble(content ->
                GTMaterialGameplayRegistry.material(content.material)
                    .densityGramsPerCubicCentimeter()))
            .map(Content::material)
            .orElse("");
    }

    public int contentColor() {
        GTMaterial material = GTMaterialRegistry.material(contentMaterial());
        return 0xFF000000 | (material == null ? 0x808080 : material.provisionalColor());
    }

    public boolean contentMolten() {
        return contents.stream().anyMatch(content -> {
            GTMaterialGameplayRegistry.MaterialGameplay gameplay =
                GTMaterialGameplayRegistry.material(content.material);
            return gameplay != null && temperature >= gameplay.meltingKelvin();
        });
    }

    public long oxygenUnits() {
        return oxygenUnits;
    }

    private void syncVisualState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        int fill = fillLevel();
        String visualKey = fill + "|" + contentMaterial() + "|" + contentMolten();
        if (visualKey.equals(lastVisualKey)) {
            return;
        }
        lastVisualKey = visualKey;
        BlockState oldState = getBlockState();
        if (!oldState.hasProperty(CrucibleBlock.FILL)) {
            return;
        }
        BlockState newState = oldState.setValue(CrucibleBlock.FILL, fill);
        if (newState != oldState) {
            level.setBlock(worldPosition, newState, Block.UPDATE_ALL);
        }
        level.sendBlockUpdated(worldPosition, oldState, newState, Block.UPDATE_ALL);
    }

    public List<Content> contents() {
        return List.copyOf(contents);
    }

    public ItemStack shovelOutSolidContent() {
        GTMaterialPrefix scrap = GTMaterialRegistry.prefix("scrapGt");
        if (scrap == null || scrap.amount() <= 0) {
            return ItemStack.EMPTY;
        }
        Content selected = contents.stream()
            .filter(content -> {
                GTMaterialGameplayRegistry.MaterialGameplay gameplay =
                    GTMaterialGameplayRegistry.material(content.material);
                return gameplay != null && temperature < gameplay.meltingKelvin()
                    && content.units > 0;
            })
            .min(Comparator.comparingDouble(content ->
                GTMaterialGameplayRegistry.material(content.material)
                    .densityGramsPerCubicCentimeter()))
            .orElse(null);
        if (selected == null) {
            return ItemStack.EMPTY;
        }
        int count = (int) Math.min(64, selected.units / scrap.amount());
        if (count <= 0) {
            selected.units = 0;
        } else {
            selected.units -= count * scrap.amount();
        }
        contents.removeIf(content -> content.units <= 0);
        setChanged();
        return count <= 0 ? ItemStack.EMPTY : GTMaterialItems.createPart(
            selected.material,
            "scrapGt",
            count
        );
    }

    public boolean isFormed() {
        return formed;
    }

    public void forceStructureCheck() {
        if (level != null && !level.isClientSide()) {
            formed = structureIsValid();
        }
    }

    private boolean structureIsValid() {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    BlockPos target = worldPosition.offset(x, y, z);
                    if (!level.isLoaded(target)) {
                        return false;
                    }
                    BlockState state = level.getBlockState(target);
                    if (x == 0 && z == 0) {
                        if (y == 0) {
                            if (!state.is(GTBlocks.CRUCIBLE.get())
                                || level.getBlockEntity(target) != this) {
                                return false;
                            }
                        } else if (!state.isAir()) {
                            return false;
                        }
                    } else if (!state.is(GTBlocks.CRUCIBLE_WALL.get())
                        && !state.is(GTBlocks.MACHINE_PORT.get())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean isPart(BlockPos pos) {
        BlockPos delta = pos.subtract(worldPosition);
        return delta.getY() >= 0 && delta.getY() <= 2
            && Math.abs(delta.getX()) <= 1
            && Math.abs(delta.getZ()) <= 1
            && (delta.getX() != 0 || delta.getZ() != 0);
    }

    private void meltDown() {
        contents.clear();
        heatBuffer.restore(0);
        temperature = maximumTemperature();
        if (level != null) {
            level.playSound(
                null,
                worldPosition,
                SoundEvents.LAVA_EXTINGUISH,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
            );
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    for (int y = 0; y <= 2; y++) {
                        BlockPos target = worldPosition.offset(x, y, z);
                        if (!level.getBlockState(target).isAir()) {
                            level.setBlockAndUpdate(target, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    private void damageEntitiesNearContent() {
        if (level == null || temperature <= 320) {
            return;
        }
        AABB interior = new AABB(
            worldPosition.getX() - 0.5,
            worldPosition.getY() + 0.1,
            worldPosition.getZ() - 0.5,
            worldPosition.getX() + 1.5,
            worldPosition.getY() + 3.0,
            worldPosition.getZ() + 1.5
        );
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, interior)) {
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 40));
            entity.hurt(level.damageSources().onFire(), 1.0F);
        }
    }

    @Override
    public long receivePacket(EnergyPacket packet, Direction side, boolean simulate) {
        if (!enabled || !formed) {
            return 0;
        }
        if (packet.kind() == EnergyKind.KINETIC) {
            if (!canAcceptOxygen()) {
                return 0;
            }
            if (!simulate) {
                long airUnits = EnergyPacket.saturatedMultiply(packet.totalEnergy(), 1000L);
                oxygenUnits = EnergyPacket.saturatedAdd(oxygenUnits, airUnits);
                setChanged();
            }
            return packet.amount();
        }
        if (packet.kind() == EnergyKind.CRYO) {
            if (!simulate) {
                long cooling = Math.max(1,
                    packet.totalEnergy() / requiredHeatPerKelvin());
                temperature = Math.max(ENVIRONMENT_TEMPERATURE, temperature - cooling);
                setChanged();
            }
            return packet.amount();
        }
        if (packet.kind() != EnergyKind.HEAT) {
            return 0;
        }
        long accepted = Math.min(packet.amount(), heatBuffer.room() / packet.size());
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            heatBuffer.accept(packet.withAmount(accepted));
            lastIncomingSize = packet.size();
            setChanged();
        }
        return accepted;
    }

    private boolean canAcceptOxygen() {
        if (level == null) {
            return false;
        }
        BlockState above = level.getBlockState(worldPosition.above());
        return above.isAir() && above.getFluidState().isEmpty();
    }

    public void toggle() {
        enabled = !enabled;
        setChanged();
    }

    public Component describe() {
        return Component.translatable(
            "message.gregtech.crucible.status",
            formed,
            enabled,
            temperature,
            maximumTemperature(),
            heatBuffer.stored(),
            requiredHeatPerKelvin(),
            thermalMassKilograms(),
            totalUnits(),
            MAX_AMOUNT,
            moltenUnits(),
            oxygenUnits,
            fillLevel(),
            contentSummary()
        );
    }

    private String contentSummary() {
        if (contents.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (Content content : contents) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(content.material).append(':').append(content.units);
        }
        return builder.toString();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wallMaterial = tag.contains("wall_material") ? tag.getString("wall_material") : "steel";
        temperature = tag.contains("temperature") ? tag.getLong("temperature") : ENVIRONMENT_TEMPERATURE;
        oxygenUnits = tag.getLong("oxygen");
        heatBuffer.restore(tag.getLong("heat"));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        lastIncomingSize = tag.getLong("last_incoming_size");
        formed = tag.getBoolean("formed");
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
        tag.putString("wall_material", wallMaterial);
        tag.putLong("temperature", temperature);
        tag.putLong("oxygen", oxygenUnits);
        tag.putLong("heat", heatBuffer.stored());
        tag.putBoolean("enabled", enabled);
        tag.putLong("last_incoming_size", lastIncomingSize);
        tag.putBoolean("formed", formed);
        ListTag list = new ListTag();
        for (Content content : contents) {
            CompoundTag entry = new CompoundTag();
            entry.putString("material", content.material);
            entry.putLong("units", content.units);
            list.add(entry);
        }
        tag.put("contents", list);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
