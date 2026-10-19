package gregtech.neo.block;

import gregtech.neo.casting.CastingPart;
import gregtech.neo.casting.MoltenMaterialSource;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * GT6 1.7.10: gregtech/tileentity/tools/MultiTileEntityFaucet.java.
 *
 * <p>The faucet is an ITileEntityMold itself: the crucible behind it pours into
 * the faucet, and the faucet forwards the material to the first mold below.
 * Automatic mode pulls every 20 ticks; redstone mode only acts on a redstone
 * change. Overheating turns the faucet into lava.</p>
 */
public final class FaucetBlockEntity extends BlockEntity {
    private String wallMaterial = "stone";
    private int sourceLine;
    private boolean acidProof;
    private boolean automatic;
    private boolean lastRedstone;

    public FaucetBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.FAUCET.get(), pos, state);
    }

    public void configure(CastingPart part) {
        if (part == null) {
            return;
        }
        wallMaterial = part.wallMaterial() == null || part.wallMaterial().isBlank()
            ? "stone"
            : part.wallMaterial();
        sourceLine = part.sourceLine();
        acidProof = part.acidProof();
        lastRedstone = false;
        setChanged();
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean redstone = level.hasNeighborSignal(worldPosition);
        boolean shouldPour;
        if (automatic) {
            shouldPour = level.getGameTime() % 20 == 5;
        } else {
            shouldPour = redstone && !lastRedstone;
        }
        lastRedstone = redstone;
        if (shouldPour) {
            tryPour();
        }
    }

    /**
     * GT6 1.7.10: MultiTileEntityFaucet.java:111-135 and :138-146. The faucet
     * checks its own wall limit first and then passes the material to the mold
     * below with the mold's top side as the input side.
     */
    public boolean tryPour() {
        if (level == null) {
            return false;
        }
        Direction facing = getBlockState().getValue(FaucetBlock.FACING);
        BlockEntity sourceEntity = level.getBlockEntity(worldPosition.relative(facing));
        if (!(sourceEntity instanceof MoltenMaterialSource source)) {
            return false;
        }
        MoldBlockEntity target = findMoldBelow();
        if (target == null) {
            return false;
        }
        if (source.castingTemperature() > maximumTemperature()) {
            level.playSound(
                null,
                worldPosition,
                SoundEvents.LAVA_EXTINGUISH,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
            );
            level.setBlockAndUpdate(worldPosition, Blocks.LAVA.defaultBlockState());
            return false;
        }
        return source.pourInto(target, Direction.UP);
    }

    /**
     * GT6 1.7.10: MultiTileEntityFaucet.java:117-121. The source skips other
     * faucets and non-colliding blocks while searching downwards.
     */
    private MoldBlockEntity findMoldBelow() {
        for (int offset = 1; offset <= 4; offset++) {
            BlockPos below = worldPosition.below(offset);
            BlockEntity entity = level.getBlockEntity(below);
            if (entity instanceof MoldBlockEntity mold) {
                return mold;
            }
            if (entity instanceof FaucetBlockEntity) {
                continue;
            }
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return null;
            }
        }
        return null;
    }

    public long maximumTemperature() {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(wallMaterial);
        double melting = gameplay == null ? 1000.0D : gameplay.meltingKelvin();
        return (long) (melting * 1.25D);
    }

    public void toggleAutomatic() {
        automatic = !automatic;
        lastRedstone = false;
        setChanged();
    }

    public void resetAutomation() {
        automatic = false;
        lastRedstone = false;
        setChanged();
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

    public boolean automatic() {
        return automatic;
    }

    public Component describe() {
        return Component.literal(
            "Crucible Faucet (" + wallMaterial + ") | MODE: "
                + (automatic ? "AUTOMATIC" : "REDSTONE")
                + " | LIMIT: " + maximumTemperature() + "K"
        );
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
        acidProof = tag.getBoolean("gt.acidproof");
        automatic = tag.contains("gt.mode")
            ? tag.getBoolean("gt.mode")
            : tag.getBoolean("automatic");
        lastRedstone = false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("gt.material", wallMaterial);
        tag.putInt("source_line", sourceLine);
        tag.putBoolean("gt.acidproof", acidProof);
        tag.putBoolean("gt.mode", automatic);
    }
}