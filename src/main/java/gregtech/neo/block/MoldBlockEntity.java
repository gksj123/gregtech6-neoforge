package gregtech.neo.block;

import gregtech.neo.casting.CastingPart;
import gregtech.neo.casting.GTMoldRegistry;
import gregtech.neo.casting.MoltenMaterialSource;
import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import gregtech.neo.material.GTMaterial;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialItems;
import gregtech.neo.material.GTMaterialPrefix;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MoldBlockEntity extends BlockEntity implements EnergyReceiver, TemperatureProvider {
    public static final long ENVIRONMENT_TEMPERATURE = 293L;
    public static final double HEAT_RESISTANCE_BONUS = 1.25D;
    private static final int MAX_MASK = 0x1FFFFFF;

    private String wallMaterial = "stone";
    private int sourceLine;
    private boolean acidProof;
    private int shapeMask;
    private String contentMaterial = "";
    private long contentUnits;
    private long contentTemperature = ENVIRONMENT_TEMPERATURE;
    private String solidMaterial = "";
    private String solidPrefix = "";
    private int outputCount;
    private boolean autoPull;
    private String lastVisualKey = "";

    public MoldBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.MOLD.get(), pos, state);
    }

    public void configure(CastingPart part) {
        if (part == null || contentUnits > 0 || outputCount > 0) {
            return;
        }
        wallMaterial = part.wallMaterial();
        sourceLine = part.sourceLine();
        shapeMask = part.shapeMask() & MAX_MASK;
        acidProof = part.acidProof();
        contentTemperature = ENVIRONMENT_TEMPERATURE;
        setChanged();
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        cool();
        if (contentUnits > 0) {
            updateContentState();
        } else if (autoPull && level.getGameTime() % 20 == 5) {
            pullFromNeighbors();
        }
        syncVisualState();
        if (level.getGameTime() % 20 == 0) {
            setChanged();
        }
    }

    private void cool() {
        long target = ENVIRONMENT_TEMPERATURE;
        if (contentTemperature > target) {
            contentTemperature -= Math.min(5, contentTemperature - target);
        } else if (contentTemperature < target) {
            contentTemperature += Math.min(5, target - contentTemperature);
        }
        contentTemperature = Math.max(1, contentTemperature);
    }

    private void updateContentState() {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(contentMaterial);
        if (gameplay == null) {
            clearContent();
            return;
        }
        if (contentTemperature > gameplay.boilingKelvin()
            || contentTemperature > maximumTemperature()) {
            meltDown();
            return;
        }
        if (contentTemperature >= gameplay.meltingKelvin()) {
            return;
        }

        String prefixId = GTMoldRegistry.prefixForMask(shapeMask);
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(prefixId);
        if (prefix == null || prefix.amount() <= 0) {
            clearContent();
            return;
        }
        int count = (int) Math.min(64, contentUnits / prefix.amount());
        solidMaterial = contentMaterial;
        solidPrefix = prefixId;
        outputCount = Math.max(0, count);
        contentMaterial = "";
        contentUnits = 0;
        setChanged();
    }

    private void meltDown() {
        clearContent();
        if (level != null) {
            level.setBlockAndUpdate(worldPosition, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
        }
    }

    private void clearContent() {
        contentMaterial = "";
        contentUnits = 0;
        contentTemperature = ENVIRONMENT_TEMPERATURE;
        solidMaterial = "";
        solidPrefix = "";
        outputCount = 0;
        setChanged();
    }

    public boolean takeOrPour(Player player) {
        if (outputCount > 0) {
            ItemStack output = GTMaterialItems.createPart(solidMaterial, solidPrefix, outputCount);
            if (!output.isEmpty()) {
                player.getInventory().placeItemBackInInventory(output);
                if (contentTemperature > 320) {
                    player.setRemainingFireTicks(40);
                    player.hurt(level.damageSources().onFire(), 1.0F);
                }
                solidMaterial = "";
                solidPrefix = "";
                outputCount = 0;
                setChanged();
                return true;
            }
        }
        return pullFromNeighbors();
    }

    public boolean pullFromNeighbors() {
        if (level == null || !canAccept(shapeMask)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbor instanceof MoltenMaterialSource source && source.pourInto(this)) {
                markUpdated();
                return true;
            }
        }
        return false;
    }

    public boolean pourFrom(MoltenMaterialSource source) {
        if (source == null || !canAccept(shapeMask)) {
            return false;
        }
        boolean result = source.pourInto(this);
        if (result) {
            markUpdated();
        }
        return result;
    }

    public boolean canAccept(int mask) {
        return (mask & MAX_MASK) != 0 && contentUnits <= 0 && outputCount <= 0;
    }

    public boolean canAcceptMaterial(String material) {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(material);
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(GTMoldRegistry.prefixForMask(shapeMask));
        return gameplay != null && prefix != null && prefix.amount() > 0
            && GTMaterialRegistry.material(material) != null;
    }

    public boolean canAcceptTemperature(long temperature) {
        return temperature <= maximumTemperature();
    }

    public boolean fillMold(String material, long units, long temperature) {
        if (!canAccept(shapeMask) || !canAcceptMaterial(material) || units <= 0) {
            return false;
        }
        if (!canAcceptTemperature(temperature)) {
            meltDown();
            return false;
        }
        contentMaterial = material;
        contentUnits = units;
        contentTemperature = temperature;
        markUpdated();
        return true;
    }

    public long requiredUnits() {
        return GTMoldRegistry.requiredUnits(shapeMask);
    }

    public long maximumTemperature() {
        GTMaterialGameplayRegistry.MaterialGameplay wall =
            GTMaterialGameplayRegistry.material(wallMaterial);
        double melting = wall == null ? 1000 : wall.meltingKelvin();
        return (long) (melting * HEAT_RESISTANCE_BONUS);
    }

    public void carve(BlockHitResult hit) {
        if (contentUnits > 0 || outputCount > 0) {
            return;
        }
        double localX = hit.getLocation().x - worldPosition.getX();
        double localZ = hit.getLocation().z - worldPosition.getZ();
        int oldMask = shapeMask;
        shapeMask = GTMoldRegistry.carve(shapeMask, localX, localZ);
        if (shapeMask != oldMask) {
            markUpdated();
        }
    }

    public void rotate() {
        if (contentUnits > 0 || outputCount > 0) {
            return;
        }
        shapeMask = GTMoldRegistry.rotateClockwise(shapeMask) & MAX_MASK;
        markUpdated();
    }

    public void toggleAutoPull() {
        autoPull = !autoPull;
        setChanged();
    }

    public void resetAutomation() {
        autoPull = false;
        setChanged();
    }

    public void damageTouchingEntity(LivingEntity entity) {
        if (contentTemperature > 320) {
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 40));
            entity.hurt(level.damageSources().onFire(), 1.0F);
        }
    }

    public int shapeMask() {
        return shapeMask;
    }

    public String wallMaterial() {
        return wallMaterial;
    }

    public int sourceLine() {
        return sourceLine;
    }

    public boolean acidProof() {
        return acidProof;
    }

    public String contentMaterial() {
        return contentMaterial;
    }

    public String solidMaterial() {
        return solidMaterial;
    }

    public String solidPrefix() {
        return solidPrefix;
    }

    public long contentUnits() {
        return contentUnits;
    }

    public long contentTemperature() {
        return contentTemperature;
    }

    @Override
    public long temperatureKelvin() {
        return contentTemperature;
    }

    @Override
    public long maximumTemperatureKelvin() {
        return maximumTemperature();
    }

    @Override
    public String temperatureSourceName() {
        return "Casting Mold";
    }

    @Override
    public long receivePacket(EnergyPacket packet, Direction side, boolean simulate) {
        if (packet.kind() != EnergyKind.CRYO || contentTemperature <= 1) {
            return 0;
        }
        if (!simulate) {
            contentTemperature = Math.max(
                1,
                contentTemperature - packet.totalEnergy()
            );
            setChanged();
            syncVisualState();
        }
        return packet.amount();
    }

    public int outputCount() {
        return outputCount;
    }

    public boolean autoPull() {
        return autoPull;
    }

    public boolean hasVisualContent() {
        return contentUnits > 0 || outputCount > 0;
    }

    public boolean isMolten() {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(displayMaterial());
        return gameplay != null && contentUnits > 0 && contentTemperature >= gameplay.meltingKelvin();
    }

    public String displayMaterial() {
        return contentUnits > 0 ? contentMaterial : solidMaterial;
    }

    public int displayColor() {
        GTMaterial material = GTMaterialRegistry.material(displayMaterial());
        return 0xFF000000 | (material == null ? 0x808080 : material.provisionalColor());
    }

    public ItemStack takeOutputForTesting() {
        if (outputCount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack output = GTMaterialItems.createPart(solidMaterial, solidPrefix, outputCount);
        solidMaterial = "";
        solidPrefix = "";
        outputCount = 0;
        setChanged();
        return output;
    }

    public void forceSolidifyForTesting() {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(contentMaterial);
        if (gameplay != null) {
            contentTemperature = Math.max(1, (long) gameplay.meltingKelvin() - 1);
            updateContentState();
            markUpdated();
        }
    }

    public Component describe() {
        GTMoldRegistry.MaskLookup shape = GTMoldRegistry.lookup(shapeMask);
        String content = contentUnits > 0
            ? contentMaterial + ":" + contentUnits + "U @ " + contentTemperature + "K"
            : outputCount > 0
                ? solidMaterial + ":" + solidPrefix + " x" + outputCount + " @ " + contentTemperature + "K"
                : "EMPTY";
        return Component.literal(
            "Mold (" + wallMaterial + ") | SHAPE: " + shape.sourceShape()
                + " -> " + shape.prefix() + " | MASK: 0x" + Integer.toHexString(shapeMask)
                + " | CONTENT: " + content + " | LIMIT: " + maximumTemperature()
                + "K | AUTO: " + autoPull
        );
    }

    private void syncVisualState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        String key = shapeMask + "|" + displayMaterial() + "|" + contentUnits
            + "|" + outputCount + "|" + isMolten();
        if (key.equals(lastVisualKey)) {
            return;
        }
        lastVisualKey = key;
        markUpdated();
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wallMaterial = tag.contains("wall_material") ? tag.getString("wall_material") : "stone";
        sourceLine = tag.getInt("source_line");
        acidProof = tag.getBoolean("acid_proof");
        shapeMask = tag.getInt("shape") & MAX_MASK;
        contentMaterial = tag.getString("content_material");
        contentUnits = tag.getLong("content_units");
        contentTemperature = tag.contains("content_temperature")
            ? tag.getLong("content_temperature")
            : ENVIRONMENT_TEMPERATURE;
        solidMaterial = tag.getString("solid_material");
        solidPrefix = tag.getString("solid_prefix");
        outputCount = tag.getInt("output_count");
        autoPull = tag.getBoolean("auto_pull");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("wall_material", wallMaterial);
        tag.putInt("source_line", sourceLine);
        tag.putBoolean("acid_proof", acidProof);
        tag.putInt("shape", shapeMask);
        tag.putString("content_material", contentMaterial);
        tag.putLong("content_units", contentUnits);
        tag.putLong("content_temperature", contentTemperature);
        tag.putString("solid_material", solidMaterial);
        tag.putString("solid_prefix", solidPrefix);
        tag.putInt("output_count", outputCount);
        tag.putBoolean("auto_pull", autoPull);
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
}
