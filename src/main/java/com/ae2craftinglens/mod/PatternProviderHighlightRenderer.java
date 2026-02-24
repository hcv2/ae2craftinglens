package com.ae2craftinglens.mod;

import org.joml.Matrix4f;

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
    
    private static final RenderType HIGHLIGHT_LINES = RenderType.create(
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
        
        for (PatternProviderHighlightManager.HighlightedProvider hp : manager.getActiveHighlights(player.getUUID())) {
            if (hp.getDimension().equals(level.dimension())) {
                renderHighlight(poseStack, bufferSource, hp.getPos(), hp.getRemainingSeconds());
            }
        }
        
        bufferSource.endBatch();
        
        poseStack.popPose();
    }
    
    private static void renderHighlight(PoseStack poseStack, MultiBufferSource bufferSource, BlockPos pos, float remainingSeconds) {
        float alpha = Math.min(1.0f, remainingSeconds / 2.0f);
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));
        float r = 0.2f + 0.3f * pulse;
        float g = 0.8f;
        float b = 1.0f;
        
        AABB box = new AABB(pos).inflate(0.002);
        
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HIGHLIGHT_LINES);
        
        renderBox(poseStack, vertexConsumer, box, r, g, b, alpha);
        
        float offset = 0.0005f;
        AABB boxOffset1 = box.inflate(offset);
        renderBox(poseStack, vertexConsumer, boxOffset1, r, g, b, alpha * 0.8f);
        AABB boxOffset2 = box.inflate(-offset);
        renderBox(poseStack, vertexConsumer, boxOffset2, r, g, b, alpha * 0.8f);
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
