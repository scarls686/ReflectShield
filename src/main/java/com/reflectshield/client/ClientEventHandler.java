package com.reflectshield.client;

import com.reflectshield.ReflectShieldMod;
import com.reflectshield.client.gui.ConfigScreen;
import com.reflectshield.common.config.ModConfig;
import com.reflectshield.common.network.NetworkHandler;
import com.reflectshield.common.network.PacketReflectKey;
import com.reflectshield.common.util.ItemMatcher;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端事件处理：按键检测、激活状态跟踪。
 * <p>
 * 渲染逻辑已拆至 {@link ClientShieldRenderer}。
 * EF 相关的客户端状态查询隔离在 {@code EfClientHandler} 中。
 */
@Mod.EventBusSubscriber(modid = ReflectShieldMod.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {

    /** 本地按键是否处于激活状态 */
    private static boolean isReflecting = false;

    /** 碰撞盒激活的开始时间（ms） */
    private static long shieldStartTime = 0L;

    // -----------------------------------------------------------------------
    // 由网络包调用
    // -----------------------------------------------------------------------

    /**
     * 由 PacketShieldActivate 在客户端线程调用。
     * EF 攻击事件在服务端激活了盾，通知客户端同步显示 debug 碰撞箱。
     */
    public static void onShieldActivatedByServer() {
        isReflecting = true;
        shieldStartTime = System.currentTimeMillis();
    }

    // -----------------------------------------------------------------------
    // 每客户端 tick
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof ConfigScreen)) return;

        // 配置界面快捷键
        if (KeyBindings.CONFIG_KEY.consumeClick() && mc.screen == null) {
            mc.setScreen(new ConfigScreen(null));
            return;
        }

        // 按键路径：EF 管理且处于战斗模式时禁用，否则正常工作
        boolean efActive = isEfActiveForLocalPlayer();
        if (KeyBindings.REFLECT_KEY.consumeClick() && !isReflecting && !efActive
                && ItemMatcher.matches(mc.player.getMainHandItem())) {
            isReflecting = true;
            shieldStartTime = System.currentTimeMillis();
            NetworkHandler.INSTANCE.sendToServer(new PacketReflectKey(true));
        }

        // 计时到期后自动结束
        if (isReflecting) {
            long elapsed = System.currentTimeMillis() - shieldStartTime;
            if (elapsed > ModConfig.SHIELD_DURATION_MS.get()) {
                isReflecting = false;
                NetworkHandler.INSTANCE.sendToServer(new PacketReflectKey(false));
            }
        }
    }

    // -----------------------------------------------------------------------
    // 查询接口（供 ClientShieldRenderer、ConfigScreen 使用）
    // -----------------------------------------------------------------------

    public static boolean isReflecting() {
        return isReflecting;
    }

    /** 返回自激活以来经过的毫秒数 */
    public static long getElapsed() {
        return System.currentTimeMillis() - shieldStartTime;
    }

    /** 供 ConfigScreen 强制打开时重置状态 */
    public static void resetState() {
        if (isReflecting) {
            isReflecting = false;
            NetworkHandler.INSTANCE.sendToServer(new PacketReflectKey(false));
        }
    }

    // -----------------------------------------------------------------------
    // EF 查询（通过 ModList 保护，无直接 EF 引用）
    // -----------------------------------------------------------------------

    /**
     * 判断本地玩家是否应走 EF 路径。
     * 没装 EF 时始终返回 false。
     */
    private static boolean isEfActiveForLocalPlayer() {
        if (!ModList.get().isLoaded("epicfight")) return false;
        return com.reflectshield.compat.epicfight.EfClientHandler.isEfActiveForLocalPlayer();
    }
}
