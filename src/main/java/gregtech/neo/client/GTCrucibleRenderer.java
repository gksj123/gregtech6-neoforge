package gregtech.neo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gregtech.neo.GT6Neo;
import gregtech.neo.block.CrucibleBlockEntity;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class GTCrucibleRenderer implements BlockEntityRenderer<CrucibleBlockEntity> {
    private static final ResourceLocation CONTENT =
        ResourceLocation.fromNamespaceAndPath(GT6Neo.MOD_ID, "block/crucible_content");

    public GTCrucibleRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void install(IEventBus modEventBus) {
        modEventBus.addListener(GTCrucibleRenderer::register);
    }

    private static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GTBlockEntities.CRUCIBLE.get(), GTCrucibleRenderer::new);
    }

    @Override
    public void render(
        CrucibleBlockEntity crucible,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        if (!crucible.isFormed() || crucible.fillLevel() <= 0) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(CONTENT);
        VertexConsumer consumer = buffers.getBuffer(
            RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
        );
        float y = 1.125F + crucible.fillFraction() * 1.70F;
        int color = crucible.contentColor();
        int alpha = crucible.contentMolten() ? 255 : 170;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                float x0 = x;
                float x1 = x + 1.0F;
                float z0 = z;
                float z1 = z + 1.0F;
                consumer.addVertex(pose.last(), x0, y, z1)
                    .setNormal(pose.last(), 0.0F, 1.0F, 0.0F)
                    .setColor(red, green, blue, alpha / 255.0F)
                    .setUv(sprite.getU0(), sprite.getV1())
                    .setLight(packedLight)
                    .setOverlay(packedOverlay);
                consumer.addVertex(pose.last(), x1, y, z1)
                    .setNormal(pose.last(), 0.0F, 1.0F, 0.0F)
                    .setColor(red, green, blue, alpha / 255.0F)
                    .setUv(sprite.getU1(), sprite.getV1())
                    .setLight(packedLight)
                    .setOverlay(packedOverlay);
                consumer.addVertex(pose.last(), x1, y, z0)
                    .setNormal(pose.last(), 0.0F, 1.0F, 0.0F)
                    .setColor(red, green, blue, alpha / 255.0F)
                    .setUv(sprite.getU1(), sprite.getV0())
                    .setLight(packedLight)
                    .setOverlay(packedOverlay);
                consumer.addVertex(pose.last(), x0, y, z0)
                    .setNormal(pose.last(), 0.0F, 1.0F, 0.0F)
                    .setColor(red, green, blue, alpha / 255.0F)
                    .setUv(sprite.getU0(), sprite.getV0())
                    .setLight(packedLight)
                    .setOverlay(packedOverlay);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(CrucibleBlockEntity crucible) {
        return new AABB(crucible.getBlockPos()).inflate(1.5, 1.0, 1.5)
            .expandTowards(0, 2.0, 0);
    }
}
