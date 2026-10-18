package gregtech.neo.casting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record CastingPart(
    String wallMaterial,
    int sourceLine,
    int shapeMask,
    boolean acidProof
) {
    public static final Codec<CastingPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("wall_material").forGetter(CastingPart::wallMaterial),
        Codec.INT.fieldOf("source_line").forGetter(CastingPart::sourceLine),
        Codec.INT.optionalFieldOf("shape_mask", 0).forGetter(CastingPart::shapeMask),
        Codec.BOOL.optionalFieldOf("acid_proof", false).forGetter(CastingPart::acidProof)
    ).apply(instance, CastingPart::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastingPart> STREAM_CODEC =
        StreamCodec.of(
            (buffer, part) -> {
                buffer.writeUtf(part.wallMaterial());
                buffer.writeVarInt(part.sourceLine());
                buffer.writeVarInt(part.shapeMask());
                buffer.writeBoolean(part.acidProof());
            },
            buffer -> new CastingPart(
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean()
            )
        );
}
