package com.reflectshield.common.reflect;

import com.reflectshield.common.config.ModConfig;
import com.reflectshield.common.network.NetworkHandler;
import com.reflectshield.common.network.PacketShieldActivate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 反弹盾激活/冷却/超时的纯状态管理。
 * 不关心激活来源（按键 / EF / 其他），只负责状态生命周期。
 */
public final class ShieldManager {

    /** 激活中的玩家：UUID → 激活开始时间（ms） */
    private static final Map<UUID, Long> activations = new ConcurrentHashMap<>();

    /** 冷却记录：UUID → 冷却结束时间（ms） */
    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private ShieldManager() {}

    // -----------------------------------------------------------------------
    // 激活 / 停用
    // -----------------------------------------------------------------------

    /**
     * 激活反弹盾（服务端）。若正在冷却中则忽略。
     *
     * @return true 如果成功激活
     */
    public static boolean activate(UUID playerId) {
        if (isOnCooldown(playerId)) return false;
        activations.put(playerId, System.currentTimeMillis());
        return true;
    }

    /**
     * 无条件激活反弹盾（跳过冷却检查）。
     * 供 EF 路径使用 — attacking() 期间每 tick 刷新激活时间。
     */
    public static void activateForced(UUID playerId) {
        activations.put(playerId, System.currentTimeMillis());
    }

    /**
     * 激活并同步通知客户端显示碰撞箱（服务端 → 客户端）。
     */
    public static boolean activateAndSync(ServerPlayer player) {
        if (!activate(player.getUUID())) return false;
        NetworkHandler.INSTANCE.sendTo(
                new PacketShieldActivate(),
                player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT
        );
        return true;
    }

    /**
     * 无条件激活并同步（跳过冷却检查）。仅在首次激活时发包。
     * 供 EF 路径使用。
     */
    public static void activateForcedAndSync(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean wasActive = activations.containsKey(id);
        activateForced(id);
        if (!wasActive) {
            NetworkHandler.INSTANCE.sendTo(
                    new PacketShieldActivate(),
                    player.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT
            );
        }
    }

    /**
     * 手动停用（按键释放时调用）。写入冷却。
     */
    public static void deactivate(UUID playerId) {
        if (activations.remove(playerId) != null) {
            applyCooldown(playerId);
        }
    }

    /**
     * 手动停用，不写入冷却（EF 路径下降沿使用）。
     */
    public static void deactivateNoCooldown(UUID playerId) {
        activations.remove(playerId);
    }

    // -----------------------------------------------------------------------
    // 查询
    // -----------------------------------------------------------------------

    public static boolean isActive(UUID playerId) {
        return activations.containsKey(playerId);
    }

    public static boolean isOnCooldown(UUID playerId) {
        Long end = cooldowns.get(playerId);
        if (end == null) return false;
        if (System.currentTimeMillis() >= end) {
            cooldowns.remove(playerId);
            return false;
        }
        return true;
    }

    public static long getCooldownRemaining(UUID playerId) {
        Long end = cooldowns.get(playerId);
        if (end == null) return 0L;
        return Math.max(0L, end - System.currentTimeMillis());
    }

    /** 返回激活中的玩家 UUID 快照（供 tick 遍历） */
    public static Set<UUID> getActivePlayerIds() {
        return activations.keySet();
    }

    /** 返回某玩家的激活开始时间，未激活返回 -1 */
    public static long getActivationTime(UUID playerId) {
        Long t = activations.get(playerId);
        return t != null ? t : -1L;
    }

    // -----------------------------------------------------------------------
    // 每 tick 处理
    // -----------------------------------------------------------------------

    /**
     * 每服务端 tick 调用。检查超时并移除过期的激活。
     *
     * @param onTimeout 超时时回调（传入 UUID），可用于额外清理
     * @param skipIds   跳过这些玩家的超时检查（如 EF 管理的玩家），可为 null
     */
    public static void tickTimeouts(Consumer<UUID> onTimeout, Set<UUID> skipIds) {
        long now = System.currentTimeMillis();
        long durationMs = ModConfig.SHIELD_DURATION_MS.get();

        Iterator<Map.Entry<UUID, Long>> iter = activations.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, Long> entry = iter.next();
            if (skipIds != null && skipIds.contains(entry.getKey())) continue;
            if (now - entry.getValue() > durationMs) {
                iter.remove();
                applyCooldown(entry.getKey());
                if (onTimeout != null) {
                    onTimeout.accept(entry.getKey());
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // 内部
    // -----------------------------------------------------------------------

    private static void applyCooldown(UUID playerId) {
        long cooldownMs = ModConfig.SHIELD_COOLDOWN_MS.get();
        if (cooldownMs > 0) {
            cooldowns.put(playerId, System.currentTimeMillis() + cooldownMs);
        }
    }
}
