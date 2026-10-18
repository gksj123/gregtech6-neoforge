package gregtech.neo.block;

import gregtech.neo.GT6Neo;
import gregtech.neo.core.EnergyPacket;
import gregtech.neo.core.MachineEnergyProfile;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class LargeCrusherBlockEntity extends AbstractCrusherBlockEntity {
    private boolean formed;
    private boolean destroyed;
    private int structureCooldown;
    private String structureError = "";
    private String reportedStructureError = "";

    public LargeCrusherBlockEntity(BlockPos pos, BlockState state) {
        super(
            GTBlockEntities.LARGE_CRUSHER.get(),
            pos,
            state,
            MachineEnergyProfile.largeCrusher()
        );
        formed = state.getValue(LargeCrusherBlock.FORMED);
    }

    public void tick() {
        if (level == null || level.isClientSide() || destroyed) {
            return;
        }
        if (--structureCooldown <= 0) {
            structureCooldown = 20;
            forceStructureCheck();
        }
        tickProcessing();
        if (formed && isActive() && level.getGameTime() % 10 == 0) {
            damageInteriorEntities();
        }
    }

    public void forceStructureCheck() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof LargeCrusherBlock)) {
            return;
        }
        boolean valid = structureIsValid();
        formed = valid;
        if (!valid && !structureError.equals(reportedStructureError)) {
            GT6Neo.LOGGER.info("GT6 large crusher structure mismatch at {}: {}", worldPosition, structureError);
            reportedStructureError = structureError;
        }
        if (state.getValue(LargeCrusherBlock.FORMED) != valid) {
            level.setBlock(worldPosition, state.setValue(LargeCrusherBlock.FORMED, valid), Block.UPDATE_ALL);
        }
    }

    private boolean structureIsValid() {
        Direction facing = getBlockState().getValue(LargeCrusherBlock.FACING);
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 4; z++) {
                    BlockPos target = worldPosition.offset(worldOffset(facing, x, y, z));
                    if (!level.isLoaded(target)) {
                        return structureFailure("unloaded " + target);
                    }
                    BlockState state = level.getBlockState(target);
                    if (x == 0 && y == 0 && z == 0) {
                        if (!(level.getBlockEntity(target) instanceof LargeCrusherBlockEntity controller) || controller != this) {
                            return structureFailure("controller expected at " + target);
                        }
                    } else if (y == 0) {
                        if (!state.is(GTBlocks.TUNGSTENSTEEL_WALL.get()) && !state.is(GTBlocks.MACHINE_PORT.get())) {
                            return structureFailure("bottom tungstensteel wall or port expected at " + target + ", found " + state);
                        }
                    } else if (y > 0 && isInterior(x, z)) {
                        if (!state.is(GTBlocks.CRUSHER_WHEELS.get())) {
                            return structureFailure("crusher wheels expected at " + target + ", found " + state);
                        }
                    } else if (isBoundary(x, z)) {
                        if (!state.is(GTBlocks.TUNGSTENSTEEL_WALL.get()) && !state.is(GTBlocks.MACHINE_PORT.get())) {
                            return structureFailure("tungstensteel wall or port expected at " + target + ", found " + state);
                        }
                    } else {
                        return structureFailure("unexpected interior coordinate " + target);
                    }
                }
            }
        }
        structureError = "";
        return true;
    }

    private void damageInteriorEntities() {
        Direction facing = getBlockState().getValue(LargeCrusherBlock.FACING);
        BlockPos center = worldPosition.offset(worldOffset(facing, 0, 1, 2));
        AABB interior = new AABB(
            center.getX() - 1.5,
            center.getY(),
            center.getZ() - 1.5,
            center.getX() + 1.5,
            center.getY() + 2.0,
            center.getZ() + 1.5
        );
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, interior)) {
            entity.hurt(level.damageSources().cactus(), 2.0F);
        }
    }

    private boolean structureFailure(String message) {
        structureError = message;
        return false;
    }

    private static boolean isBoundary(int x, int z) {
        return Math.abs(x) == 2 || z == 0 || z == 4;
    }

    private static boolean isInterior(int x, int z) {
        return Math.abs(x) <= 1 && z >= 1 && z <= 3;
    }

    private static BlockPos worldOffset(Direction facing, int localX, int localY, int localZ) {
        int dx = localX;
        int dy = localY;
        int dz = -localZ;
        return switch (facing) {
            case SOUTH -> new BlockPos(dx, dy, dz);
            case NORTH -> new BlockPos(-dx, dy, -dz);
            case EAST -> new BlockPos(-dz, dy, -dx);
            case WEST -> new BlockPos(dz, dy, dx);
            default -> new BlockPos(dx, dy, dz);
        };
    }

    public boolean isPart(BlockPos pos) {
        Direction facing = getBlockState().getValue(LargeCrusherBlock.FACING);
        BlockPos delta = pos.subtract(worldPosition);
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 4; z++) {
                    if ((y == 0 || isBoundary(x, z)) && worldOffset(facing, x, y, z).equals(delta)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected boolean isFormedForProcessing() {
        return formed && !destroyed;
    }

    @Override
    protected int parallelism() {
        return 64;
    }

    @Override
    protected boolean cheapOverclocking() {
        return true;
    }

    @Override
    protected int efficiency() {
        return 5_000;
    }

    @Override
    protected void onEnergySizeExceeded(EnergyPacket packet) {
        destroyed = true;
        if (level != null) {
            level.explode(
                null,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5,
                2.5F,
                Level.ExplosionInteraction.BLOCK
            );
        }
    }

    public boolean isFormed() {
        return formed;
    }

    public void describe(Player player, boolean detailed) {
        if (!detailed) {
            InWorldMessages.send(player, Component.translatable(
                "message.gregtech.crusher.status",
                isActive(),
                formed,
                energyProfile().kind().symbol(),
                storedEnergy(),
                progress(),
                duration()
            ));
            return;
        }
        InWorldMessages.send(player, Component.translatable(
            "message.gregtech.crusher.detail",
            isActive(),
            formed,
            energyProfile().kind().symbol(),
            storedEnergy(),
            capacity(),
            progress(),
            duration(),
            lastIncomingSize(),
            energyProfile().minimumSize(),
            energyProfile().maximumSize(),
            energyProfile().maximumAmount(),
            inputStack().getHoverName(),
            outputStack().getHoverName()
        ));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("formed");
        destroyed = tag.getBoolean("destroyed");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("formed", formed);
        tag.putBoolean("destroyed", destroyed);
    }
}
