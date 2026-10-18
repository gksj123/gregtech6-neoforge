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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FaucetBlockEntity extends BlockEntity {
    private String wallMaterial = "stone";
    private int sourceLine;
    private boolean acidProof;
    private boolean automatic;

    public FaucetBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.FAUCET.get(), pos, state);
    }

    public void configure(CastingPart part) {
        if (part == null) {
            return;
        }
        wallMaterial = part.wallMaterial();
        sourceLine = part.sourceLine();
        acidProof = part.acidProof();
        setChanged();
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean shouldPour = automatic
            ? level.getGameTime() % 20 == 5
            : level.hasNeighborSignal(worldPosition);
        if (shouldPour) {
            tryPour();
        }
    }

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
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            level.setBlockAndUpdate(worldPosition, Blocks.LAVA.defaultBlockState());
            return false;
        }
        return target.pourFrom(source);
    }

    private MoldBlockEntity findMoldBelow() {
        for (int offset = 1; offset <= 4; offset++) {
            BlockEntity entity = level.getBlockEntity(worldPosition.below(offset));
            if (entity instanceof MoldBlockEntity mold) {
                return mold;
            }
            if (offset > 1 && !level.getBlockState(worldPosition.below(offset)).isAir()) {
                return null;
            }
        }
        return null;
    }

    public long maximumTemperature() {
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(wallMaterial);
        double melting = gameplay == null ? 1000 : gameplay.meltingKelvin();
        return (long) (melting * 1.25D);
    }

    public void toggleAutomatic() {
        automatic = !automatic;
        setChanged();
    }

    public void resetAutomation() {
        automatic = false;
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
        wallMaterial = tag.contains("wall_material") ? tag.getString("wall_material") : "stone";
        sourceLine = tag.getInt("source_line");
        acidProof = tag.getBoolean("acid_proof");
        automatic = tag.getBoolean("automatic");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("wall_material", wallMaterial);
        tag.putInt("source_line", sourceLine);
        tag.putBoolean("acid_proof", acidProof);
        tag.putBoolean("automatic", automatic);
    }
}
