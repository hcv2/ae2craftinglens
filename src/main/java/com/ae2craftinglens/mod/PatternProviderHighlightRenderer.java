package com.ae2craftinglens.mod;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.OptionalDouble;

@SuppressWarnings("null")
@EventBusSubscriber(modid = AE2CraftingLens.MODID, value = Dist.CLIENT)
public class PatternProviderHighlightRenderer {
    
    private static RenderType highlightLines = null;
    
    private static RenderType getHighlightLines() {
        if (highlightLines == null) {
            highlightLines = RenderType.create(
                "ae2craftinglens_highlight",
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                65536,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setLineState(new RenderType.LineStateShard(OptionalDouble.of(3.0)))
                        .setTransparencyState(RenderType.TransparencyStateShard.GLINT_TRANSPARENCY)
                        .setTextureState(RenderType.TextureStateShard.NO_TEXTURE)
                        .setDepthTestState(RenderType.DepthTestStateShard.NO_DEPTH_TEST)
                        .setCullState(RenderType.CullStateShard.NO_CULL)
                        .setLightmapState(RenderType.LightmapStateShard.NO_LIGHTMAP)
                        .setWriteMaskState(RenderType.WriteMaskStateShard.COLOR_DEPTH_WRITE)
                        .setShaderState(RenderType.ShaderStateShard.RENDERTYPE_LINES_SHADER)
                        .createCompositeState(false)
            );
        }
        return highlightLines;
    }
    
    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        
        PatternProviderHighlightManager manager = PatternProviderHighlightManager.getInstance();
        if (!manager.hasActiveHighlights(player.getUUID())) {
            return;
        }
        
        Level level = mc.level;
        if (level == null) {
            return;
        }
        
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        
        VertexConsumer vertexConsumer = bufferSource.getBuffer(getHighlightLines());
        
        for (PatternProviderHighlightManager.HighlightedProvider hp : manager.getActiveHighlights(player.getUUID())) {
            if (hp.getDimension().equals(level.dimension())) {
                renderHighlight(poseStack, vertexConsumer, hp.getPos(), hp.getRemainingSeconds());
            }
        }
        
        bufferSource.endBatch();
        
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        poseStack.popPose();
    }
    
    private static void renderHighlight(PoseStack poseStack, VertexConsumer consumer, BlockPos pos, float remainingSeconds) {
        long time = System.currentTimeMillis();
        
        float blinkInterval = 500.0f;
        boolean visible = ((int)(time / blinkInterval)) % 2 == 0;
        
        if (!visible) {
            return;
        }
        
        float fadeAlpha = Math.min(1.0f, remainingSeconds / 2.0f);
        
        float colorPulse = (float) (0.5 + 0.5 * Math.sin(time / 150.0));
        float r = 0.0f + 0.5f * colorPulse;
        float g = 0.8f + 0.2f * colorPulse;
        float b = 1.0f;
        
        AABB box = new AABB(pos).inflate(0.002);
        
        renderBox(poseStack, consumer, box, r, g, b, fadeAlpha);
        
        float offset = 0.0005f;
        AABB boxOffset1 = box.inflate(offset);
        renderBox(poseStack, consumer, boxOffset1, r, g, b, fadeAlpha * 0.8f);
        AABB boxOffset2 = box.inflate(-offset);
        renderBox(poseStack, consumer, boxOffset2, r, g, b, fadeAlpha * 0.8f);
    }
    
    private static void renderBox(PoseStack poseStack, VertexConsumer consumer, AABB box, float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        
        Vec3 topLeft = new Vec3(minX, maxY, maxZ);
        Vec3 bottomLeft = new Vec3(minX, minY, maxZ);
        Vec3 topRight = new Vec3(maxX, maxY, maxZ);
        Vec3 bottomRight = new Vec3(maxX, minY, maxZ);
        Vec3 topLeft2 = new Vec3(minX, maxY, minZ);
        Vec3 bottomLeft2 = new Vec3(minX, minY, minZ);
        Vec3 topRight2 = new Vec3(maxX, maxY, minZ);
        Vec3 bottomRight2 = new Vec3(maxX, minY, minZ);
        
        renderBox(consumer, poseStack, topLeft, bottomLeft, topRight, bottomRight, r, g, b, a);
        renderBox(consumer, poseStack, topLeft2, bottomLeft2, topRight2, bottomRight2, r, g, b, a);
        
        renderLine(consumer, poseStack, topRight, topRight2, r, g, b, a);
        renderLine(consumer, poseStack, bottomRight, bottomRight2, r, g, b, a);
        renderLine(consumer, poseStack, bottomLeft, bottomLeft2, r, g, b, a);
        renderLine(consumer, poseStack, topLeft, topLeft2, r, g, b, a);
    }
    
    private static void renderBox(VertexConsumer consumer, PoseStack poseStack, Vec3 topLeft, Vec3 bottomLeft, Vec3 topRight, Vec3 bottomRight, float r, float g, float b, float a) {
        renderLine(consumer, poseStack, topLeft, bottomLeft, r, g, b, a);
        renderLine(consumer, poseStack, topLeft, topRight, r, g, b, a);
        renderLine(consumer, poseStack, bottomRight, bottomLeft, r, g, b, a);
        renderLine(consumer, poseStack, bottomRight, topRight, r, g, b, a);
    }
    
    private static void renderLine(VertexConsumer consumer, PoseStack poseStack, Vec3 from, Vec3 to, float r, float g, float b, float a) {
        Matrix4f mat = poseStack.last().pose();
        Vec3 normal = from.subtract(to);
        consumer.addVertex(mat, (float) from.x, (float) from.y, (float) from.z).setColor(r, g, b, a).setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(mat, (float) to.x, (float) to.y, (float) to.z).setColor(r, g, b, a).setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }
}
