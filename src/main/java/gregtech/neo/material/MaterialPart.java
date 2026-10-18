package gregtech.neo.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record MaterialPart(String material, String prefix) {
    public static final Codec<MaterialPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("material").forGetter(MaterialPart::material),
        Codec.STRING.fieldOf("prefix").forGetter(MaterialPart::prefix)
    ).apply(instance, MaterialPart::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialPart> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            MaterialPart::material,
            ByteBufCodecs.STRING_UTF8,
            MaterialPart::prefix,
            MaterialPart::new
        );
}
