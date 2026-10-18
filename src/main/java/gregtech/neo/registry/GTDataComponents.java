package gregtech.neo.registry;

import gregtech.neo.GT6Neo;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.machine.MachinePart;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GTDataComponents {
    private GTDataComponents() {
    }

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
        DeferredRegister.createDataComponents(GT6Neo.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MaterialPart>> MATERIAL_PART =
        DATA_COMPONENTS.registerComponentType(
            "material_part",
            builder -> builder
                .persistent(MaterialPart.CODEC)
                .networkSynchronized(MaterialPart.STREAM_CODEC)
        );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MachinePart>> MACHINE_PART =
        DATA_COMPONENTS.registerComponentType(
            "machine_part",
            builder -> builder
                .persistent(MachinePart.CODEC)
                .networkSynchronized(MachinePart.STREAM_CODEC)
        );
}
