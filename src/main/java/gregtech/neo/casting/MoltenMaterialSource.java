package gregtech.neo.casting;

import gregtech.neo.block.MoldBlockEntity;

public interface MoltenMaterialSource {
    long castingTemperature();

    boolean pourInto(MoldBlockEntity mold);
}
