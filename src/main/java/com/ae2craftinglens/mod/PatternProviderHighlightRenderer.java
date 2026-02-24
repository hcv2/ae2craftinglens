package com.ae2craftinglens.mod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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

@EventBusSubscriber(modid = AE2CraftingLens.MODID, value = Dist.CLIENT)
public class PatternProviderHighlightRenderer {
    
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
        
        RenderSystem.disableDepthTest();
        
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        
        for (PatternProviderHighlightManager.HighlightedProvider hp : manager.getActiveHighlights(player.getUUID())) {
            if (hp.getDimension().equals(level.dimension())) {
                renderHighlight(poseStack, bufferSource, hp.getPos(), hp.getRemainingSeconds());
            }
        }
        
        bufferSource.endBatch();
        
        RenderSystem.enableDepthTest();
        
        poseStack.popPose();
    }
    
    @SuppressWarnings("null")
    private static void renderHighlight(PoseStack poseStack, MultiBufferSource bufferSource, BlockPos pos, float remainingSeconds) {
        float alpha = Math.min(1.0f, remainingSeconds / 2.0f);
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));
        float r = 0.2f + 0.3f * pulse;
        float g = 0.8f;
        float b = 1.0f;
        
        AABB box = new AABB(pos).inflate(0.002);
        
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        
        renderBox(poseStack, vertexConsumer, box, r, g, b, alpha);
        
        float offset = 0.0005f;
        AABB boxOffset1 = box.inflate(offset);
        renderBox(poseStack, vertexConsumer, boxOffset1, r, g, b, alpha * 0.8f);
        AABB boxOffset2 = box.inflate(-offset);
        renderBox(poseStack, vertexConsumer, boxOffset2, r, g, b, alpha * 0.8f);
        
        VertexConsumer translucentConsumer = bufferSource.getBuffer(RenderType.translucent());
        renderFilledBox(poseStack, translucentConsumer, box.inflate(-0.001), r, g, b, alpha * 0.15f);
    }
    
    @SuppressWarnings("null")
    private static void renderBox(PoseStack poseStack, VertexConsumer consumer, AABB box, float r, float g, float b, float a) {
        var pose = poseStack.last();
        
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setNormal(pose, 1, 0, 0);
        
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(pose, 0, 1, 0);
        
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setNormal(pose, -1, 0, 0);
        
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setNormal(pose, 0, -1, 0);
        
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(pose, 1, 0, 0);
        
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, 1, 0);
        
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(pose, -1, 0, 0);
        
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, -1, 0);
        
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, 0, 1);
        
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, 0, 1);
        
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, 0, 1);
        
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, 0, 1);
    }
    
    @SuppressWarnings("null")
    private static void renderFilledBox(PoseStack poseStack, VertexConsumer consumer, AABB box, float r, float g, float b, float a) {
        var pose = poseStack.last();
        
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 0, -1);
        
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 0, 1);
        
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, -1, 0, 0);
        
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, 1, 0, 0);
        
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, 0, -1, 0);
        
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 1, 0);
    }
}
