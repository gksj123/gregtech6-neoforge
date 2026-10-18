package gregtech.neo.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record MachinePart(int sourceLine, String category) {
    public static final Codec<MachinePart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("source_line").forGetter(MachinePart::sourceLine),
        Codec.STRING.fieldOf("category").forGetter(MachinePart::category)
    ).apply(instance, MachinePart::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MachinePart> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            MachinePart::sourceLine,
            ByteBufCodecs.STRING_UTF8,
            MachinePart::category,
            MachinePart::new
        );
}
