package com.ae2craftinglens.mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
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
        // 使用 AFTER_PARTICLES 阶段确保高亮显示在所有方块之上
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        
        PatternProviderHighlightManager manager = PatternProviderHighlightManager.getInstance();
        if (!manager.hasActiveHighlights()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }
        
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        
        // 使用缓冲区源
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        
        for (PatternProviderHighlightManager.HighlightedProvider hp : manager.getActiveHighlights()) {
            if (hp.getLevel() == level) {
                renderHighlight(poseStack, bufferSource, hp.getPos(), hp.getRemainingSeconds());
            }
        }
        
        // 结束批次渲染
        bufferSource.endBatch();
        
        poseStack.popPose();
    }
    
    private static void renderHighlight(PoseStack poseStack, MultiBufferSource bufferSource, BlockPos pos, float remainingSeconds) {
        float alpha = Math.min(1.0f, remainingSeconds / 2.0f);
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));
        float r = 0.2f + 0.3f * pulse;
        float g = 0.8f;
        float b = 1.0f;
        
        @SuppressWarnings("null")
        AABB box = new AABB(pos).inflate(0.002);
        
        // 使用可以穿透方块的渲染类型
        // RenderType.lines() 使用 NO_DEPTH_TEST 可以穿透方块
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        
        renderBox(poseStack, vertexConsumer, box, r, g, b, alpha);
        
        // 添加一个填充的半透明框，使其更明显
        VertexConsumer translucentConsumer = bufferSource.getBuffer(RenderType.translucent());
        renderFilledBox(poseStack, translucentConsumer, box.inflate(-0.001), r, g, b, alpha * 0.2f);
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
        
        // 底部
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setNormal(pose, 1, 0, 0);
        
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(pose, 0, 1, 0);
        
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setNormal(pose, -1, 0, 0);
        
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setNormal(pose, 0, -1, 0);
        
        // 顶部
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(pose, 1, 0, 0);
        
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, 1, 0);
        
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(pose, -1, 0, 0);
        
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setNormal(pose, 0, -1, 0);
        
        // 连接底部和顶部
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
        
        // 渲染填充的半透明面
        // 前面
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 0, -1);
        
        // 后面
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 0, 1);
        
        // 左面
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, -1, 0, 0);
        
        // 右面
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, 1, 0, 0);
        
        // 底面
        consumer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, 0, -1, 0);
        
        // 顶面
        consumer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(0).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(0).setNormal(pose, 0, 1, 0);
    }
}
