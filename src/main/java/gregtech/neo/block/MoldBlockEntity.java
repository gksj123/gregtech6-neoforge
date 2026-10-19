package gregtech.neo.block;

import gregtech.neo.casting.CastingPart;
import gregtech.neo.casting.GTMoldRegistry;
import gregtech.neo.casting.MoltenMaterialSource;
import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import gregtech.neo.core.GTTemperatureDamage;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;

import java.math.BigInteger;
import java.util.Set;

/**
 * GT6 1.7.10: gregtech/tileentity/tools/MultiTileEntityMold.java.
 *
 * <p>The source mold is a flat 5x5 casting tray: a one-pixel base, four-pixel
 * outer walls and a 5x5 grid of three-pixel cells. A chisel removes cells; the
 * remaining cell pattern selects the output prefix through {@code MOLD_RECIPES}.
 * This class keeps the source bit layout (bit {@code x*5+z}), input-side rules,
 * cooling, auto-input connection mask and meltdown behaviour.</p>
 */
public final class MoldBlockEntity extends BlockEntity implements EnergyReceiver, TemperatureProvider {
    public static final long ENVIRONMENT_TEMPERATURE = 293L;
    public static final double HEAT_RESISTANCE_BONUS = 1.25D;
    public static final int MAX_MASK = 0x1FFFFFF;
    private static final int COOLING_PER_TICK = 5;
    private static final long NUGGET_UNITS = GTMaterialGameplayRegistry.MATERIAL_UNIT / 9L;

    /**
     * GT6 1.7.10: gregapi/data/MT.java:1013 (Ice) and :1057 (Glass) are the two
     * materials tagged TD.Processing.COOL2CRYSTAL. The source only rewrites
     * plate -> plateGem and plateTiny -> plateGemTiny in that case.
     */
    private static final Set<String> COOL_TO_CRYSTAL = Set.of("ice", "glass");

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
    private int autoPullDirections;
    private boolean useRedstone;
    private boolean needsAutoPull;
    private boolean lastRedstone;
    private String lastVisualKey = "";

    public MoldBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.MOLD.get(), pos, state);
    }

    public void configure(CastingPart part) {
        if (part == null || contentUnits > 0 || outputCount > 0) {
            return;
        }
        wallMaterial = part.wallMaterial() == null || part.wallMaterial().isBlank()
            ? "stone"
            : part.wallMaterial();
        sourceLine = part.sourceLine();
        shapeMask = part.shapeMask() & MAX_MASK;
        acidProof = part.acidProof();
        contentTemperature = environmentTemperature();
        needsAutoPull = true;
        setChanged();
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        cool();
        if (contentUnits > 0) {
            updateContentState();
            if (level.getBlockState(worldPosition)
                .is(net.minecraft.world.level.block.Blocks.LAVA)) {
                return;
            }
        } else if (outputCount > 0) {
            checkOutputOverheat();
        } else {
            tryAutoPull();
        }
        syncVisualState();
        if (level.getGameTime() % 20 == 0) {
            setChanged();
        }
    }

    private void cool() {
        long target = environmentTemperature();
        if (contentTemperature > target) {
            contentTemperature -= Math.min(COOLING_PER_TICK, contentTemperature - target);
        } else if (contentTemperature < target) {
            contentTemperature += Math.min(COOLING_PER_TICK, target - contentTemperature);
        }
        contentTemperature = Math.max(1, contentTemperature);
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:178-203. Content above the wall limit
     * or above the material boiling point turns the mold into flowing lava.
     * Otherwise, once the content is below the melting point it becomes one
     * complete prefix stack. The source keeps the output in slot 0; this port
     * keeps the same pending stack in its block entity until picked up.
     */
    private void updateContentState() {
        if (contentMaterial.isBlank() || contentUnits <= 0) {
            return;
        }
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(contentMaterial);
        if (gameplay == null) {
            clearContentAndOutput();
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

        GTMaterialGameplayRegistry.MaterialGameplay target = solidifyingTarget(contentMaterial);
        String outputMaterial = target == null ? contentMaterial : target.slug();
        String prefixId = outputPrefixFor(outputMaterial, shapeMask);
        GTMaterialPrefix prefix = prefixId == null ? null : GTMaterialRegistry.prefix(prefixId);
        if (prefix == null || prefix.amount() <= 0
            || !GTMaterialItems.canCreate(outputMaterial, prefixId)) {
            clearContentAndOutput();
            return;
        }
        int count = (int) Math.min(64, contentUnits / prefix.amount());
        if (count <= 0) {
            clearContentAndOutput();
            return;
        }
        solidMaterial = outputMaterial;
        solidPrefix = prefixId;
        outputCount = count;
        contentMaterial = "";
        contentUnits = 0;
        markUpdated();
    }

    private void checkOutputOverheat() {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(solidMaterial);
        if (gameplay == null) {
            return;
        }
        if (contentTemperature > gameplay.boilingKelvin()
            || contentTemperature > maximumTemperature()) {
            meltDown();
        }
    }

    private void meltDown() {
        clearContentAndOutput();
        if (level != null) {
            level.playSound(
                null,
                worldPosition,
                SoundEvents.LAVA_EXTINGUISH,
                SoundSource.BLOCKS,
                0.7F,
                1.2F
            );
            level.setBlockAndUpdate(
                worldPosition,
                net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState()
            );
        }
    }

    private void clearContentAndOutput() {
        contentMaterial = "";
        contentUnits = 0;
        contentTemperature = environmentTemperature();
        solidMaterial = "";
        solidPrefix = "";
        outputCount = 0;
        needsAutoPull = true;
        setChanged();
    }

    private void clearOutput() {
        solidMaterial = "";
        solidPrefix = "";
        outputCount = 0;
        contentTemperature = environmentTemperature();
        needsAutoPull = true;
        setChanged();
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:246-264.
     *
     * <p>The source only accepts a casting from the top or one of the four
     * horizontal sides. It stores the full prefix amount as content and returns
     * the amount the crucible must consume. The original also scales that
     * consumed amount by {@code mTargetSolidifying.mAmount}; that material
     * mapping is not exposed by the current generated material data, so the
     * common {@code U}-sized case is used here.</p>
     */
    public long fillMold(String material, long units, long temperature, Direction side) {
        if (material == null || material.isBlank() || units <= 0 || !isMoldInputSide(side)) {
            return 0;
        }
        if (!acidProof && isAcidic(material)) {
            return 0;
        }
        if (contentUnits > 0 || outputCount > 0 || !contentMaterial.isBlank()) {
            return 0;
        }
        if (recipePrefix(shapeMask) == null) {
            return 0;
        }
        GTMaterialGameplayRegistry.MaterialGameplay target = solidifyingTarget(material);
        String outputMaterial = target == null ? material : target.slug();
        String outputPrefix = outputPrefixFor(outputMaterial, shapeMask);
        if (outputPrefix == null || !GTMaterialItems.canCreate(outputMaterial, outputPrefix)) {
            return 0;
        }
        long required = requiredUnits(shapeMask);
        GTMaterialGameplayRegistry.MaterialGameplay source =
            GTMaterialGameplayRegistry.material(material);
        long targetAmount = source == null || source.solidifyingAmount() <= 0
            ? GTMaterialGameplayRegistry.MATERIAL_UNIT
            : source.solidifyingAmount();
        long consumed = unitsRoundedUp(required, targetAmount);
        if (required <= 0 || consumed <= 0 || units < consumed) {
            return 0;
        }
        contentMaterial = material;
        contentUnits = required;
        contentTemperature = temperature;
        markUpdated();
        return consumed;
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:234-243. An unknown mask falls back
     * to one nugget per set bit; a zero mask has no recipe at all.
     */
    public long requiredUnits(int mask) {
        if ((mask & MAX_MASK) == 0) {
            return 0;
        }
        String prefixId = recipePrefix(mask);
        if (prefixId == null) {
            return 0;
        }
        if ("nugget".equals(prefixId)) {
            return (long) Integer.bitCount(mask & MAX_MASK) * NUGGET_UNITS;
        }
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(prefixId);
        return prefix == null ? 0 : prefix.amount();
    }

    public String recipePrefix(int mask) {
        if ((mask & MAX_MASK) == 0) {
            return null;
        }
        return GTMoldRegistry.prefixForMask(mask);
    }

    private String outputPrefixFor(String material, int mask) {
        String prefix = recipePrefix(mask);
        if (prefix == null) {
            return null;
        }
        if (COOL_TO_CRYSTAL.contains(material)) {
            if ("plate".equals(prefix)) {
                return "plateGem";
            }
            if ("plateTiny".equals(prefix)) {
                return "plateGemTiny";
            }
        }
        return prefix;
    }

    private static boolean isAcidic(String material) {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(material);
        return gameplay != null && gameplay.acidic();
    }

    public boolean canAccept(int mask) {
        return (mask & MAX_MASK) != 0 && contentUnits <= 0 && outputCount <= 0;
    }

    public boolean canAcceptMaterial(String material) {
        GTMaterialGameplayRegistry.MaterialGameplay target = solidifyingTarget(material);
        String outputMaterial = target == null ? material : target.slug();
        String prefix = outputPrefixFor(outputMaterial, shapeMask);
        return prefix != null && GTMaterialItems.canCreate(outputMaterial, prefix);
    }

    public boolean canAcceptTemperature(long temperature) {
        return temperature <= maximumTemperature();
    }

    public long requiredUnits() {
        return requiredUnits(shapeMask);
    }

    public long maximumTemperature() {
        GTMaterialGameplayRegistry.MaterialGameplay wall =
            GTMaterialGameplayRegistry.material(wallMaterial);
        double melting = wall == null ? 1000.0D : wall.meltingKelvin();
        return (long) (melting * HEAT_RESISTANCE_BONUS);
    }

    public long environmentTemperature() {
        if (level == null) {
            return ENVIRONMENT_TEMPERATURE;
        }
        float biomeTemperature = level.getBiome(worldPosition).value().getBaseTemperature();
        return Math.max(1, (long) (270.0F + biomeTemperature * 20.0F));
    }

    private GTMaterialGameplayRegistry.MaterialGameplay solidifyingTarget(String material) {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(material);
        if (gameplay == null
            || gameplay.solidifyingMaterial() == null
            || gameplay.solidifyingMaterial().isBlank()) {
            return gameplay;
        }
        return GTMaterialGameplayRegistry.material(gameplay.solidifyingMaterial());
    }

    private static long unitsRoundedUp(long required, long targetAmount) {
        if (required <= 0 || targetAmount <= 0) {
            return 0;
        }
        try {
            long numerator = Math.addExact(
                Math.multiplyExact(required, targetAmount),
                GTMaterialGameplayRegistry.MATERIAL_UNIT - 1
            );
            return numerator / GTMaterialGameplayRegistry.MATERIAL_UNIT;
        } catch (ArithmeticException ignored) {
            BigInteger numerator = BigInteger.valueOf(required)
                .multiply(BigInteger.valueOf(targetAmount))
                .add(BigInteger.valueOf(GTMaterialGameplayRegistry.MATERIAL_UNIT - 1));
            return numerator
                .divide(BigInteger.valueOf(GTMaterialGameplayRegistry.MATERIAL_UNIT))
                .min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue();
        }
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:328-336. Only the top face carves a
     * 5x5 cell, and only while both the content and output slots are empty.
     */
    public boolean carve(BlockHitResult hit) {
        if (hit == null || contentUnits > 0 || outputCount > 0) {
            return false;
        }
        double localX = hit.getLocation().x - hit.getBlockPos().getX();
        double localZ = hit.getLocation().z - hit.getBlockPos().getZ();
        int oldMask = shapeMask;
        shapeMask = GTMoldRegistry.carve(shapeMask, localX, localZ) & MAX_MASK;
        if (shapeMask != oldMask) {
            if (level != null) {
                level.playSound(
                    null,
                    worldPosition,
                    SoundEvents.STONE_HIT,
                    SoundSource.BLOCKS,
                    1.0F,
                    0.8F
                );
            }
            needsAutoPull = true;
            markUpdated();
            return true;
        }
        return false;
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:375-405. Wrench, screwdriver and
     * rotator use the same clockwise 90 degree transformation.
     */
    public void rotate() {
        shapeMask = GTMoldRegistry.rotateClockwise(shapeMask) & MAX_MASK;
        needsAutoPull = true;
        markUpdated();
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:343-355. A monkey wrench on the top
     * face selects one horizontal auto-input direction near an edge, or toggles
     * the redstone requirement when used on the centre.
     */
    public String toggleAutoPull(Direction clickedFace, BlockHitResult hit) {
        if (clickedFace != Direction.UP || hit == null) {
            return null;
        }
        Direction side = wrenchSide(clickedFace, hit);
        if (side.getAxis().isHorizontal()) {
            autoPullDirections ^= 1 << side.ordinal();
            needsAutoPull = true;
            markUpdated();
            return "Crucible Auto-Input: "
                + ((autoPullDirections & (1 << side.ordinal())) != 0 ? "ON" : "OFF");
        }
        useRedstone = !useRedstone;
        setChanged();
        return useRedstone
            ? "Crucible Auto-Input: REDSTONE"
                + (autoPullDirections == 0 ? " (WARNING: No Direction Selected!)" : "")
            : "Crucible Auto-Input: NO REDSTONE";
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:337-342.
     */
    public String resetAutomation() {
        autoPullDirections = 0;
        useRedstone = false;
        needsAutoPull = true;
        markUpdated();
        return "Crucible Auto-Input: OFF & NO REDSTONE";
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:169-175. In automatic mode the mold
     * pulls at {@code SERVER_TIME % 20 == 5}; in redstone mode the pull also
     * requires an incoming signal. Only the connected faces are considered.
     */
    private void tryAutoPull() {
        if (level == null || autoPullDirections == 0
            || contentUnits > 0 || outputCount > 0 || !contentMaterial.isBlank()) {
            return;
        }
        boolean redstone = level.hasNeighborSignal(worldPosition);
        boolean trigger = level.getGameTime() % 20 == 5 || needsAutoPull;
        if (useRedstone) {
            if (!redstone) {
                lastRedstone = false;
                return;
            }
            trigger = trigger || !lastRedstone;
        }
        lastRedstone = redstone;
        if (!trigger) {
            return;
        }
        for (Direction side : Direction.values()) {
            if ((autoPullDirections & (1 << side.ordinal())) == 0) {
                continue;
            }
            if (pourFromNeighbor(side)) {
                needsAutoPull = false;
                return;
            }
        }
        // The source resets mInventoryChanged/mBlockUpdated after each scan.
        // Keep the 20-tick phase, but do not rescan every tick after a miss.
        needsAutoPull = false;
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:267-294. Empty-hand interaction tries
     * to pick up the output first. If the output is empty and the top face was
     * clicked, the hit position selects which adjacent crucible is poured from.
     */
    public boolean pourFromNeighborByHit(BlockHitResult hit) {
        if (hit == null || hit.getDirection() != Direction.UP) {
            return false;
        }
        Direction side = wrenchSide(hit.getDirection(), hit);
        if (side.getAxis().isHorizontal()) {
            return pourFromNeighbor(side);
        }
        for (Direction horizontal : Direction.Plane.HORIZONTAL) {
            if (pourFromNeighbor(horizontal)) {
                return true;
            }
        }
        return false;
    }

    public boolean pourFromNeighbor(Direction side) {
        if (level == null || side == null) {
            return false;
        }
        BlockPos neighborPos = worldPosition.relative(side);
        MoltenMaterialSource source = findMoltenSource(neighborPos);
        if (source != null && source.pourInto(this, side)) {
            needsAutoPull = false;
            markUpdated();
            return true;
        }
        return false;
    }

    private MoltenMaterialSource findMoltenSource(BlockPos neighborPos) {
        if (level == null) {
            return null;
        }
        BlockEntity direct = level.getBlockEntity(neighborPos);
        if (direct instanceof MoltenMaterialSource source) {
            return source;
        }
        // GT6 1.7.10: MultiTileEntityCrucible.java:142 permits molds on the
        // second layer of walls. Those wall blocks have no block entity, so the
        // nearby formed controller owns the molten contents.
        for (BlockPos candidate : BlockPos.betweenClosed(
            neighborPos.offset(-2, -2, -2),
            neighborPos.offset(2, 2, 2)
        )) {
            BlockEntity blockEntity = level.getBlockEntity(candidate);
            if (blockEntity instanceof CrucibleBlockEntity crucible
                && crucible.isFormed()
                && crucible.isPart(neighborPos)) {
                return crucible;
            }
        }
        return null;
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:296-322. The output is taken with the
     * current hotbar stack first, then merged into it, then inserted into the
     * inventory. The source applies temperature damage when the mold is hot.
     */
    public boolean pickUp(Player player, boolean damage) {
        if (player == null || outputCount <= 0) {
            return false;
        }
        ItemStack output = GTMaterialItems.createPart(solidMaterial, solidPrefix, outputCount);
        if (output.isEmpty()) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, output.copy());
        } else if (ItemStack.isSameItemSameComponents(held, output)
            && held.getCount() + output.getCount() <= held.getMaxStackSize()) {
            held.grow(output.getCount());
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        } else {
            if (!canAddAll(player, output)) {
                return false;
            }
            player.getInventory().add(output.copy());
        }
        if (damage) {
            damagePlayer(player);
        }
        clearOutput();
        markUpdated();
        return true;
    }

    private void damagePlayer(Player player) {
        if (level == null || player == null) {
            return;
        }
        GTTemperatureDamage.apply(player, contentTemperature, 1.0F, 5.0F);
    }

    public void damageTouchingEntity(LivingEntity entity) {
        if (level == null || entity == null) {
            return;
        }
        GTTemperatureDamage.apply(entity, contentTemperature, 1.0F, 5.0F);
    }

    private static boolean canAddAll(Player player, ItemStack stack) {
        int remaining = stack.getCount();
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing.isEmpty()) {
                remaining -= Math.min(stack.getMaxStackSize(), remaining);
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                remaining -= Math.max(0, existing.getMaxStackSize() - existing.getCount());
            }
        }
        return remaining <= 0;
    }

    /**
     * GT6 1.7.10: UT.Code.getSideWrenching(byte, float, float, float).
     * The returned side is the face a monkey wrench would select. A vertical
     * result means the centre of the top face was hit.
     */
    public static Direction wrenchSide(Direction clickedFace, BlockHitResult hit) {
        double localX = hit.getLocation().x - hit.getBlockPos().getX();
        double localZ = hit.getLocation().z - hit.getBlockPos().getZ();
        return switch (clickedFace) {
            case UP, DOWN -> {
                if (localX < 0.25D) {
                    yield localZ < 0.25D || localZ > 0.75D ? clickedFace.getOpposite() : Direction.WEST;
                }
                if (localX > 0.75D) {
                    yield localZ < 0.25D || localZ > 0.75D ? clickedFace.getOpposite() : Direction.EAST;
                }
                if (localZ < 0.25D) {
                    yield Direction.NORTH;
                }
                if (localZ > 0.75D) {
                    yield Direction.SOUTH;
                }
                yield clickedFace;
            }
            default -> clickedFace;
        };
    }

    public static boolean isMoldInputSide(Direction side) {
        return side != null && side != Direction.DOWN;
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

    public int outputCount() {
        return outputCount;
    }

    public int autoPullDirections() {
        return autoPullDirections;
    }

    public boolean useRedstone() {
        return useRedstone;
    }

    public String autoPullStatus() {
        if (useRedstone) {
            return "Crucible Auto-Input: REDSTONE"
                + (autoPullDirections == 0 ? " (WARNING: No Direction Selected!)" : "");
        }
        if (autoPullDirections == 0) {
            return "Crucible Auto-Input: OFF & NO REDSTONE";
        }
        return "Crucible Auto-Input: ON";
    }

    public void markBlockUpdated() {
        needsAutoPull = true;
    }

    public IItemHandler outputItemHandler() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 1;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                if (slot != 0 || outputCount <= 0) {
                    return ItemStack.EMPTY;
                }
                return GTMaterialItems.createPart(solidMaterial, solidPrefix, outputCount);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot != 0 || amount <= 0 || outputCount <= 0
                    || contentTemperature - 50 >= environmentTemperature()) {
                    return ItemStack.EMPTY;
                }
                ItemStack extracted = GTMaterialItems.createPart(
                    solidMaterial,
                    solidPrefix,
                    Math.min(amount, outputCount)
                );
                if (!simulate && !extracted.isEmpty()) {
                    outputCount -= extracted.getCount();
                    if (outputCount <= 0) {
                        clearOutput();
                    } else {
                        setChanged();
                    }
                }
                return extracted;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;
            }
        };
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

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:571-579. CU reduces the temperature
     * by {@code abs(size * amount)} down to 1 K.
     */
    @Override
    public long receivePacket(EnergyPacket packet, Direction side, boolean simulate) {
        if (packet.kind() != EnergyKind.CRYO || contentTemperature <= 1) {
            return 0;
        }
        if (!simulate) {
            contentTemperature = Math.max(
                1,
                contentTemperature - Math.abs(packet.totalEnergy())
            );
            needsAutoPull = true;
            setChanged();
            syncVisualState();
        }
        return packet.amount();
    }

    public boolean hasVisualContent() {
        return contentUnits > 0 || outputCount > 0;
    }

    public boolean isMolten() {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(displayMaterial());
        return gameplay != null && contentUnits > 0
            && contentTemperature >= gameplay.meltingKelvin();
    }

    public String displayMaterial() {
        return contentUnits > 0 ? contentMaterial : solidMaterial;
    }

    public int wallColor() {
        GTMaterial material = GTMaterialRegistry.material(wallMaterial);
        return 0xFF000000 | (material == null ? 0x808080 : material.provisionalColor());
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
        contentTemperature = environmentTemperature();
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
        String shape = shapeMask == 0
            ? "Use a Chisel in order to select the Shape of the Mold"
            : moldRecipeDescription();
        String content = contentUnits > 0
            ? contentMaterial + ":" + contentUnits + "U @ " + contentTemperature + "K"
            : outputCount > 0
                ? solidMaterial + ":" + solidPrefix + " x" + outputCount + " @ " + contentTemperature + "K"
                : "EMPTY";
        return Component.literal(
            "Mold (" + wallMaterial + ") | " + shape
                + " | CONTENT: " + content + " | LIMIT: " + maximumTemperature()
                + "K | " + autoPullStatus()
        );
    }

    public String moldRecipeDescription() {
        String prefixId = recipePrefix(shapeMask);
        GTMaterialPrefix prefix = prefixId == null ? null : GTMaterialRegistry.prefix(prefixId);
        String name = prefix == null ? String.valueOf(prefixId) : prefix.prettyCategory();
        return "This Mold produces " + name + " "
            + displayUnits(requiredUnits(shapeMask)) + " Units";
    }

    private static String displayUnits(long amount) {
        long whole = amount / GTMaterialGameplayRegistry.MATERIAL_UNIT;
        long thousandths = (amount % GTMaterialGameplayRegistry.MATERIAL_UNIT)
            * 1000 / GTMaterialGameplayRegistry.MATERIAL_UNIT;
        return whole + "." + String.format(java.util.Locale.ROOT, "%03d", thousandths);
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
        wallMaterial = tag.contains("gt.material")
            ? tag.getString("gt.material")
            : tag.contains("wall_material") ? tag.getString("wall_material") : "stone";
        if (wallMaterial == null || wallMaterial.isBlank()) {
            wallMaterial = "stone";
        }
        sourceLine = tag.getInt("source_line");
        acidProof = tag.contains("gt.acidproof")
            ? tag.getBoolean("gt.acidproof")
            : tag.getBoolean("acid_proof");
        shapeMask = (tag.contains("gt.mold") ? tag.getInt("gt.mold") : tag.getInt("shape"))
            & MAX_MASK;
        CompoundTag content = tag.getCompound("gt.materials");
        contentMaterial = content.getString("m");
        contentUnits = content.getLong("a");
        if (contentMaterial.isBlank() && tag.contains("content_material")) {
            contentMaterial = tag.getString("content_material");
        }
        if (contentUnits == 0 && tag.contains("content_units")) {
            contentUnits = tag.getLong("content_units");
        }
        contentTemperature = tag.contains("gt.temperature")
            ? tag.getLong("gt.temperature")
            : tag.contains("content_temperature")
                ? tag.getLong("content_temperature")
                : ENVIRONMENT_TEMPERATURE;
        solidMaterial = tag.contains("output_material")
            ? tag.getString("output_material")
            : tag.getString("solid_material");
        solidPrefix = tag.contains("output_prefix")
            ? tag.getString("output_prefix")
            : tag.getString("solid_prefix");
        outputCount = tag.contains("output_count")
            ? tag.getInt("output_count")
            : 0;
        autoPullDirections = (tag.contains("gt.connection")
            ? tag.getByte("gt.connection")
            : tag.getByte("auto_pull_directions")) & 0x3F;
        if (!tag.contains("gt.connection") && !tag.contains("auto_pull_directions")
            && tag.getBoolean("auto_pull")) {
            // Previous port revision used one boolean for all four horizontal
            // faces. Keep that behaviour when loading an old save.
            autoPullDirections = 0x3C;
        }
        useRedstone = tag.getBoolean("gt.mode");
        needsAutoPull = true;
        lastRedstone = false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("gt.material", wallMaterial);
        tag.putInt("source_line", sourceLine);
        tag.putBoolean("gt.acidproof", acidProof);
        tag.putInt("gt.mold", shapeMask);
        CompoundTag content = new CompoundTag();
        content.putString("m", contentMaterial);
        content.putLong("a", contentUnits);
        tag.put("gt.materials", content);
        tag.putLong("gt.temperature", contentTemperature);
        tag.putString("output_material", solidMaterial);
        tag.putString("output_prefix", solidPrefix);
        tag.putInt("output_count", outputCount);
        tag.putByte("gt.connection", (byte) autoPullDirections);
        tag.putBoolean("gt.mode", useRedstone);
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
