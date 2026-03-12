package com.reflectshield.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reflectshield.ReflectShieldMod;
import com.reflectshield.client.gui.ConfigScreen;
import com.reflectshield.common.config.ModConfig;
import com.reflectshield.common.network.NetworkHandler;
import com.reflectshield.common.network.PacketReflectKey;
import com.reflectshield.common.util.ItemMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReflectShieldMod.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {

    /** 本地按键是否处于激活状态 */
    private static boolean isReflecting = false;

    /** 碰撞盒激活的开始时间（ms） */
    private static long shieldStartTime = 0L;

    /**
     * 每客户端 tick 检测按键状态变化并同步到服务端。
     * 使用 ClientTickEvent 而非 InputEvent.Key，以确保每帧只发一次包。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // 有界面打开时不触发（但配置界面除外，那里另外处理）
        if (mc.screen != null && !(mc.screen instanceof ConfigScreen)) return;

        // 配置界面快捷键（消耗按键点击，避免重复触发）
        if (KeyBindings.CONFIG_KEY.consumeClick() && mc.screen == null) {
            mc.setScreen(new ConfigScreen(null));
            return;
        }

        // 检测单次点击（consumeClick 每次点击只返回 true 一次）
        // 同时在客户端检查白名单：只有手持符合白名单的物品才激活（与服务端逻辑保持一致）
        if (KeyBindings.REFLECT_KEY.consumeClick() && !isReflecting
                && ItemMatcher.matches(mc.player.getMainHandItem())) {
            isReflecting = true;
            shieldStartTime = System.currentTimeMillis();
            NetworkHandler.INSTANCE.sendToServer(new PacketReflectKey(true));
        }

        // 计时到期后自动结束，无需持续按住
        if (isReflecting) {
            long elapsed = System.currentTimeMillis() - shieldStartTime;
            if (elapsed > ModConfig.SHIELD_DURATION_MS.get()) {
                isReflecting = false;
                NetworkHandler.INSTANCE.sendToServer(new PacketReflectKey(false));
            }
        }
    }

    /**
     * 渲染碰撞盒线框。
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!isReflecting) return;
        if (!ModConfig.DEBUG_SHOW_SHIELD.get()) return;

        long elapsed = System.currentTimeMillis() - shieldStartTime;
        if (elapsed > ModConfig.SHIELD_DURATION_MS.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 根据模式计算 8 个顶点
        final Vec3[] v;
        if (ModConfig.SHIELD_USE_PLAYER_HITBOX.get()) {
            // 玩家自身 AABB 的 8 个顶点
            AABB bb = player.getBoundingBox();
            v = new Vec3[]{
                new Vec3(bb.minX, bb.minY, bb.minZ), new Vec3(bb.minX, bb.minY, bb.maxZ),
                new Vec3(bb.minX, bb.maxY, bb.minZ), new Vec3(bb.minX, bb.maxY, bb.maxZ),
                new Vec3(bb.maxX, bb.minY, bb.minZ), new Vec3(bb.maxX, bb.minY, bb.maxZ),
                new Vec3(bb.maxX, bb.maxY, bb.minZ), new Vec3(bb.maxX, bb.maxY, bb.maxZ),
            };
        } else {
            // OBB：8 个旋转顶点
            double W = ModConfig.SHIELD_WIDTH.get();
            double H = ModConfig.SHIELD_HEIGHT.get();
            double D = ModConfig.SHIELD_DEPTH.get();
            double dist = ModConfig.SHIELD_DISTANCE.get();

            Vec3 eye = player.getEyePosition(mc.getFrameTime());
            Vec3 look = player.getLookAngle().normalize();
            Vec3 worldUp = new Vec3(0, 1, 0);
            Vec3 right = look.cross(worldUp).normalize();
            if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
            Vec3 up = right.cross(look).normalize();

            Vec3 center = eye.add(look.scale(dist));
            Vec3 hw = right.scale(W / 2.0);
            Vec3 hh = up.scale(H / 2.0);
            Vec3 hd = look.scale(D / 2.0);

            v = new Vec3[8];
            int idx = 0;
            for (int ri = -1; ri <= 1; ri += 2)
                for (int ui = -1; ui <= 1; ui += 2)
                    for (int di = -1; di <= 1; di += 2)
                        v[idx++] = center.add(hw.scale(ri)).add(hh.scale(ui)).add(hd.scale(di));
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        // 颜色渐变（从白色 → 橙色）
        float ratio = 1.0f - (float) elapsed / ModConfig.SHIELD_DURATION_MS.get();
        float r = 1.0f, g = ratio, b = ratio * 0.2f, alpha = 0.9f;

        // 12 条边：每对顶点只在一个轴向上翻转
        // ri/ui/di 组合，两个顶点差一个轴索引
        int[][] edges = {
            {0,1},{2,3},{4,5},{6,7},  // 沿 hd 轴的 4 条边
            {0,2},{1,3},{4,6},{5,7},  // 沿 hh 轴的 4 条边
            {0,4},{1,5},{2,6},{3,7}   // 沿 hw 轴的 4 条边
        };
        PoseStack.Pose pose = ps.last();
        for (int[] edge : edges) {
            Vec3 a = v[edge[0]], b2 = v[edge[1]];
            lineConsumer.vertex(pose.pose(), (float)a.x, (float)a.y, (float)a.z)
                    .color(r, g, b, alpha)
                    .normal(pose.normal(), (float)(b2.x - a.x), (float)(b2.y - a.y), (float)(b2.z - a.z))
                    .endVertex();
            lineConsumer.vertex(pose.pose(), (float)b2.x, (float)b2.y, (float)b2.z)
                    .color(r, g, b, alpha)
                    .normal(pose.normal(), (float)(b2.x - a.x), (float)(b2.y - a.y), (float)(b2.z - a.z))
                    .endVertex();
        }

        bufferSource.endBatch(RenderType.lines());
        ps.popPose();
    }

    /**
     * 计算玩家面前与视线垂直的虚拟碰撞盒（客户端，与服务端算法相同）。
     */
    public static AABB computeClientShieldAABB(LocalPlayer player) {
        double W = ModConfig.SHIELD_WIDTH.get();
        double H = ModConfig.SHIELD_HEIGHT.get();
        double D = ModConfig.SHIELD_DEPTH.get();
        double dist = ModConfig.SHIELD_DISTANCE.get();

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        Vec3 center = eye.add(look.scale(dist));

        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();
        if (right.lengthSqr() < 1e-6) {
            right = new Vec3(1, 0, 0);
        }
        Vec3 up = right.cross(look).normalize();

        double hw = W / 2.0, hh = H / 2.0, hd = D / 2.0;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int ri = -1; ri <= 1; ri += 2) {
            for (int ui = -1; ui <= 1; ui += 2) {
                for (int di = -1; di <= 1; di += 2) {
                    double vx = center.x + ri * hw * right.x + ui * hh * up.x + di * hd * look.x;
                    double vy = center.y + ri * hw * right.y + ui * hh * up.y + di * hd * look.y;
                    double vz = center.z + ri * hw * right.z + ui * hh * up.z + di * hd * look.z;
                    if (vx < minX) minX = vx;
                    if (vy < minY) minY = vy;
                    if (vz < minZ) minZ = vz;
                    if (vx > maxX) maxX = vx;
                    if (vy > maxY) maxY = vy;
                    if (vz > maxZ) maxZ = vz;
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 供 ConfigScreen 查询当前激活状态（用于冷却显示） */
    public static boolean isReflecting() {
        return isReflecting;
    }

    /** 供 ConfigScreen 强制打开时重置状态 */
    public static void resetState() {
        if (isReflecting) {
            isReflecting = false;
            NetworkHandler.INSTANCE.sendToServer(new PacketReflectKey(false));
        }
    }
}
