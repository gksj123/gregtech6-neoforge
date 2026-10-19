package gregtech.neo.casting;

import gregtech.neo.block.MoldBlockEntity;
import net.minecraft.core.Direction;

/**
 * A crucible-like block that can pour one complete casting amount into a mold.
 *
 * <p>GT6 1.7.10: gregapi/tileentity/machines/ITileEntityCrucible.java and
 * gregtech/tileentity/tools/MultiTileEntitySmeltery.java:498 call
 * {@code fillMold(materialStack, temperature, moldSide)} and subtract the
 * returned amount from the source stack.</p>
 */
public interface MoltenMaterialSource {
    long castingTemperature();

    /**
     * Offers the source's molten contents to the mold. The mold decides whether
     * its shape, wall material, temperature and input side are valid and returns
     * the amount it consumed. The source must subtract exactly that amount.
     */
    boolean pourInto(MoldBlockEntity mold, Direction moldSide);
}
