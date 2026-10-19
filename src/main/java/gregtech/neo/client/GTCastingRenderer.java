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

/**
 * GT6 1.7.10: MultiTileEntityMold.java:438-545.
 *
 * <p>The source renders the flat tray in two layers: a full inner plate whose
 * top is at 3 px (molten) or 2 px (solid), and 25 three-pixel-tall cells that
 * are only drawn when the corresponding shape bit is clear. This renderer
 * reproduces both layers so an uncarved mold looks like the source's full 5x5
 * grid, while chiselling opens the holes that expose the casting surface.</p>
 */
public final class GTCastingRenderer implements BlockEntityRenderer<MoldBlockEntity> {
    private static final ResourceLocation WALL =
        ResourceLocation.fromNamespaceAndPath(GT6Neo.MOD_ID, "block/mold_wall");
    private static final ResourceLocation MOLTEN =
        ResourceLocation.fromNamespaceAndPath(GT6Neo.MOD_ID, "block/crucible_content");
    private static final ResourceLocation SOLID =
        ResourceLocation.fromNamespaceAndPath(GT6Neo.MOD_ID, "block/material_block");
    private static final float CELL_ORIGIN = 2.0F / 16.0F;
    private static final float CELL_SIZE = 12.0F / 5.0F / 16.0F;
    private static final float CELL_BOTTOM = 1.0F / 16.0F;
    private static final float CELL_TOP = 3.0F / 16.0F;

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
        if (mold == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        TextureAtlasSprite wallSprite = minecraft
            .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(WALL);
        int wallColor = mold.wallColor();
        int wallR = (wallColor >> 16 & 0xFF);
        int wallG = (wallColor >> 8 & 0xFF);
        int wallB = (wallColor & 0xFF);

        if (mold.hasVisualContent()) {
            boolean molten = mold.isMolten();
            TextureAtlasSprite contentSprite = minecraft
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(molten ? MOLTEN : SOLID);
            float contentTop = molten ? CELL_TOP - 0.005F : 2.0F / 16.0F;
            VertexConsumer content = buffers.getBuffer(
                molten
                    ? RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
                    : RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS)
            );
            int color = mold.displayColor();
            int r = color >> 16 & 0xFF;
            int g = color >> 8 & 0xFF;
            int b = color & 0xFF;
            float alpha = molten ? 0.90F : 0.96F;
            addTop(
                content,
                pose.last(),
                contentSprite,
                1.0F / 16.0F,
                15.0F / 16.0F,
                1.0F / 16.0F,
                15.0F / 16.0F,
                contentTop,
                r,
                g,
                b,
                alpha,
                packedLight,
                packedOverlay
            );
        }

        int mask = mold.shapeMask();
        VertexConsumer cells = buffers.getBuffer(
            RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS)
        );
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                int index = x * 5 + z;
                if ((mask & (1 << index)) != 0) {
                    continue;
                }
                float x0 = CELL_ORIGIN + x * CELL_SIZE;
                float z0 = CELL_ORIGIN + z * CELL_SIZE;
                float x1 = x0 + CELL_SIZE;
                float z1 = z0 + CELL_SIZE;
                // GT6 1.7.10: setBlockBounds2 hide the side of a cell when its
                // neighbouring cell is still present; the top face is always drawn.
                if (z == 0 || carved(mask, x, z - 1)) {
                    addNorth(
                        cells, pose.last(), wallSprite,
                        x0, x1, CELL_BOTTOM, CELL_TOP, z0,
                        wallR, wallG, wallB, packedLight, packedOverlay
                    );
                }
                if (z == 4 || carved(mask, x, z + 1)) {
                    addSouth(
                        cells, pose.last(), wallSprite,
                        x0, x1, CELL_BOTTOM, CELL_TOP, z1,
                        wallR, wallG, wallB, packedLight, packedOverlay
                    );
                }
                if (x == 0 || carved(mask, x - 1, z)) {
                    addWest(
                        cells, pose.last(), wallSprite,
                        z0, z1, CELL_BOTTOM, CELL_TOP, x0,
                        wallR, wallG, wallB, packedLight, packedOverlay
                    );
                }
                if (x == 4 || carved(mask, x + 1, z)) {
                    addEast(
                        cells, pose.last(), wallSprite,
                        z0, z1, CELL_BOTTOM, CELL_TOP, x1,
                        wallR, wallG, wallB, packedLight, packedOverlay
                    );
                }
                addTop(
                    cells, pose.last(), wallSprite,
                    x0, x1, z0, z1, CELL_TOP,
                    wallR, wallG, wallB, 1.0F, packedLight, packedOverlay
                );
            }
        }
    }

    private static boolean carved(int mask, int x, int z) {
        return x < 0 || x > 4 || z < 0 || z > 4 || (mask & (1 << (x * 5 + z))) != 0;
    }

    private static void addTop(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        TextureAtlasSprite sprite,
        float x0,
        float x1,
        float z0,
        float z1,
        float y,
        int r,
        int g,
        int b,
        float a,
        int light,
        int overlay
    ) {
        consumer.addVertex(pose, x0, y, z1)
            .setNormal(pose, 0.0F, 1.0F, 0.0F)
            .setColor(r, g, b, (int) (a * 255))
            .setUv(sprite.getU0(), sprite.getV1())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x1, y, z1)
            .setNormal(pose, 0.0F, 1.0F, 0.0F)
            .setColor(r, g, b, (int) (a * 255))
            .setUv(sprite.getU1(), sprite.getV1())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x1, y, z0)
            .setNormal(pose, 0.0F, 1.0F, 0.0F)
            .setColor(r, g, b, (int) (a * 255))
            .setUv(sprite.getU1(), sprite.getV0())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x0, y, z0)
            .setNormal(pose, 0.0F, 1.0F, 0.0F)
            .setColor(r, g, b, (int) (a * 255))
            .setUv(sprite.getU0(), sprite.getV0())
            .setLight(light)
            .setOverlay(overlay);
    }

    private static void addNorth(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        TextureAtlasSprite sprite,
        float x0,
        float x1,
        float y0,
        float y1,
        float z,
        int r,
        int g,
        int b,
        int light,
        int overlay
    ) {
        consumer.addVertex(pose, x0, y0, z)
            .setNormal(pose, 0.0F, 0.0F, -1.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU0(), sprite.getV1())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x0, y1, z)
            .setNormal(pose, 0.0F, 0.0F, -1.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU0(), sprite.getV0())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x1, y1, z)
            .setNormal(pose, 0.0F, 0.0F, -1.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU1(), sprite.getV0())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x1, y0, z)
            .setNormal(pose, 0.0F, 0.0F, -1.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU1(), sprite.getV1())
            .setLight(light)
            .setOverlay(overlay);
    }

    private static void addSouth(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        TextureAtlasSprite sprite,
        float x0,
        float x1,
        float y0,
        float y1,
        float z,
        int r,
        int g,
        int b,
        int light,
        int overlay
    ) {
        consumer.addVertex(pose, x1, y0, z)
            .setNormal(pose, 0.0F, 0.0F, 1.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU0(), sprite.getV1())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x1, y1, z)
            .setNormal(pose, 0.0F, 0.0F, 1.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU0(), sprite.getV0())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x0, y1, z)
            .setNormal(pose, 0.0F, 0.0F, 1.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU1(), sprite.getV0())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x0, y0, z)
            .setNormal(pose, 0.0F, 0.0F, 1.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU1(), sprite.getV1())
            .setLight(light)
            .setOverlay(overlay);
    }

    private static void addWest(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        TextureAtlasSprite sprite,
        float z0,
        float z1,
        float y0,
        float y1,
        float x,
        int r,
        int g,
        int b,
        int light,
        int overlay
    ) {
        consumer.addVertex(pose, x, y0, z0)
            .setNormal(pose, -1.0F, 0.0F, 0.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU0(), sprite.getV1())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x, y0, z1)
            .setNormal(pose, -1.0F, 0.0F, 0.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU1(), sprite.getV1())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x, y1, z1)
            .setNormal(pose, -1.0F, 0.0F, 0.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU1(), sprite.getV0())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x, y1, z0)
            .setNormal(pose, -1.0F, 0.0F, 0.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU0(), sprite.getV0())
            .setLight(light)
            .setOverlay(overlay);
    }

    private static void addEast(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        TextureAtlasSprite sprite,
        float z0,
        float z1,
        float y0,
        float y1,
        float x,
        int r,
        int g,
        int b,
        int light,
        int overlay
    ) {
        consumer.addVertex(pose, x, y0, z1)
            .setNormal(pose, 1.0F, 0.0F, 0.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU0(), sprite.getV1())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x, y0, z0)
            .setNormal(pose, 1.0F, 0.0F, 0.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU1(), sprite.getV1())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x, y1, z0)
            .setNormal(pose, 1.0F, 0.0F, 0.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU1(), sprite.getV0())
            .setLight(light)
            .setOverlay(overlay);
        consumer.addVertex(pose, x, y1, z1)
            .setNormal(pose, 1.0F, 0.0F, 0.0F)
            .setColor(r, g, b, 255)
            .setUv(sprite.getU0(), sprite.getV0())
            .setLight(light)
            .setOverlay(overlay);
    }

    @Override
    public AABB getRenderBoundingBox(MoldBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.25);
    }
}
