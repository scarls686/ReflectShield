package com.reflectshield.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reflectshield.ReflectShieldMod;
import com.reflectshield.common.config.ModConfig;
import com.reflectshield.common.reflect.ShieldAABB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 碰撞盒线框渲染（客户端）。
 * 从 ClientEventHandler 中拆出，职责单一。
 */
@Mod.EventBusSubscriber(modid = ReflectShieldMod.MOD_ID, value = Dist.CLIENT)
public class ClientShieldRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!ClientEventHandler.isReflecting()) return;
        if (!ModConfig.DEBUG_SHOW_SHIELD.get()) return;

        long elapsed = ClientEventHandler.getElapsed();
        long duration = ModConfig.SHIELD_DURATION_MS.get();
        if (elapsed > duration) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        Vec3 eye = player.getEyePosition(mc.getFrameTime());
        Vec3 look = player.getLookAngle().normalize();
        Vec3[] v = ShieldAABB.computeVertices(player, eye, look);

        Vec3 camera = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        // 颜色渐变（从白色 → 橙色）
        float ratio = 1.0f - (float) elapsed / duration;
        float r = 1.0f, g = ratio, b = ratio * 0.2f, alpha = 0.9f;

        // 12 条边
        int[][] edges = {
                {0, 1}, {2, 3}, {4, 5}, {6, 7},   // 沿 hd 轴
                {0, 2}, {1, 3}, {4, 6}, {5, 7},   // 沿 hh 轴
                {0, 4}, {1, 5}, {2, 6}, {3, 7}    // 沿 hw 轴
        };
        PoseStack.Pose pose = ps.last();
        for (int[] edge : edges) {
            Vec3 a = v[edge[0]], b2 = v[edge[1]];
            lineConsumer.vertex(pose.pose(), (float) a.x, (float) a.y, (float) a.z)
                    .color(r, g, b, alpha)
                    .normal(pose.normal(), (float) (b2.x - a.x), (float) (b2.y - a.y), (float) (b2.z - a.z))
                    .endVertex();
            lineConsumer.vertex(pose.pose(), (float) b2.x, (float) b2.y, (float) b2.z)
                    .color(r, g, b, alpha)
                    .normal(pose.normal(), (float) (b2.x - a.x), (float) (b2.y - a.y), (float) (b2.z - a.z))
                    .endVertex();
        }

        bufferSource.endBatch(RenderType.lines());
        ps.popPose();
    }
}
