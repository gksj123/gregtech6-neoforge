package gregtech.neo.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Registry-only fluid used by GT6 fluid handlers. It is intentionally not
 * placeable as a world block; GT6 machines transfer it through capabilities.
 */
public final class SimpleGTFluid extends Fluid {
    private final FluidType fluidType;
    private final Item bucketItem;

    public SimpleGTFluid(FluidType fluidType, Item bucketItem) {
        this.fluidType = fluidType;
        this.bucketItem = bucketItem;
        registerDefaultState(stateDefinition.any());
    }

    @Override
    public FluidType getFluidType() {
        return fluidType;
    }

    @Override
    public Item getBucket() {
        return bucketItem;
    }

    @Override
    protected boolean canBeReplacedWith(
        FluidState state,
        BlockGetter level,
        BlockPos pos,
        Fluid fluid,
        Direction direction
    ) {
        return false;
    }

    @Override
    protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
        return Vec3.ZERO;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return 1;
    }

    @Override
    protected float getExplosionResistance() {
        return 0.0F;
    }

    @Override
    public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public float getOwnHeight(FluidState state) {
        return 0.0F;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean isSource(FluidState state) {
        return true;
    }

    @Override
    public int getAmount(FluidState state) {
        return 1;
    }

    @Override
    public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}
