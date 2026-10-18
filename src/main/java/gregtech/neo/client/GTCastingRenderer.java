package gregtech.neo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gregtech.neo.GT6Neo;
import gregtech.neo.block.MoldBlockEntity;
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

public final class GTCastingRenderer implements BlockEntityRenderer<MoldBlockEntity> {
    private static final ResourceLocation MOLTEN =
        ResourceLocation.fromNamespaceAndPath(GT6Neo.MOD_ID, "block/crucible_content");
    private static final ResourceLocation SOLID =
        ResourceLocation.fromNamespaceAndPath(GT6Neo.MOD_ID, "block/material_block");

    public GTCastingRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void install(IEventBus modEventBus) {
        modEventBus.addListener(GTCastingRenderer::register);
    }

    private static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GTBlockEntities.MOLD.get(), GTCastingRenderer::new);
    }

    @Override
    public void render(
        MoldBlockEntity mold,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        if (!mold.hasVisualContent()) {
            return;
        }
        ResourceLocation texture = mold.isMolten() ? MOLTEN : SOLID;
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(texture);
        RenderType renderType = mold.isMolten()
            ? RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
            : RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        int color = mold.displayColor();
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = mold.isMolten() ? 0.90F : 0.96F;
        double y = 0.79;
        double cell = 2.0 / 16.0;
        double origin = 3.0 / 16.0;

        int mask = mold.shapeMask();
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 5; column++) {
                if ((mask & (1 << (column + row * 5))) == 0) {
                    continue;
                }
                float x0 = (float) (origin + column * cell);
                float z0 = (float) (origin + row * cell);
                float x1 = x0 + (float) cell;
                float z1 = z0 + (float) cell;
                consumer.addVertex(pose.last().pose(), x0, (float) y, z0)
                    .setColor(red, green, blue, alpha)
                    .setUv(sprite.getU0(), sprite.getV0())
                    .setLight(packedLight)
                    .setOverlay(packedOverlay);
                consumer.addVertex(pose.last().pose(), x1, (float) y, z0)
                    .setColor(red, green, blue, alpha)
                    .setUv(sprite.getU1(), sprite.getV0())
                    .setLight(packedLight)
                    .setOverlay(packedOverlay);
                consumer.addVertex(pose.last().pose(), x1, (float) y, z1)
                    .setColor(red, green, blue, alpha)
                    .setUv(sprite.getU1(), sprite.getV1())
                    .setLight(packedLight)
                    .setOverlay(packedOverlay);
                consumer.addVertex(pose.last().pose(), x0, (float) y, z1)
                    .setColor(red, green, blue, alpha)
                    .setUv(sprite.getU0(), sprite.getV1())
                    .setLight(packedLight)
                    .setOverlay(packedOverlay);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(MoldBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.25);
    }
}
