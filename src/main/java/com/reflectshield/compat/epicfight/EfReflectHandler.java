package com.reflectshield.compat.epicfight;

import com.reflectshield.common.network.NetworkHandler;
import com.reflectshield.common.network.PacketEfManaged;
import com.reflectshield.common.reflect.ShieldManager;
import com.reflectshield.common.util.ItemMatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EpicFight 反弹路径的服务端状态管理。
 * <p>
 * 所有 EF 专用状态（玩家注册、attacking() 轮询）都隔离在此处，
 * 核心 ReflectHandler 不再引用任何 EF 类型。
 * <p>
 * 策略：在 attacking() 为 true 期间持续保持盾激活，
 * attacking() 变为 false 时立即关闭盾，无冷却。
 */
public final class EfReflectHandler {

    /** 已装备 EF 反弹被动技能的玩家 UUID 集合 */
    private static final Set<UUID> managedPlayers =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** UUID → ServerPlayerPatch，持续轮询 attacking() 状态 */
    private static final Map<UUID, ServerPlayerPatch> playerPatches = new ConcurrentHashMap<>();

    private EfReflectHandler() {}

    // -----------------------------------------------------------------------
    // 注册 / 注销
    // -----------------------------------------------------------------------

    /**
     * 由 ReflectSkill.onInitiate 调用。注册玩家并通知客户端。
     * 仅在 connection 已就绪时发包；加载存档时 connection 为 null，
     * 届时由 onPlayerLoggedIn 补发。
     */
    public static void register(ServerPlayer player, ServerPlayerPatch patch) {
        UUID id = player.getUUID();
        managedPlayers.add(id);
        playerPatches.put(id, patch);
        if (player.connection != null) {
            sendManagedPacket(player, true);
        }
    }

    /**
     * 由 ReflectSkill.onRemoved 调用。注销并通知客户端。
     */
    public static void unregister(ServerPlayer player) {
        UUID id = player.getUUID();
        managedPlayers.remove(id);
        playerPatches.remove(id);
        // 注销时关闭盾（如果正在激活）
        ShieldManager.deactivateNoCooldown(id);
        if (player.connection != null) {
            sendManagedPacket(player, false);
        }
    }

    /**
     * 玩家完全登录后，补发 EF 管理状态包。
     * 由 ReflectHandler.onPlayerLoggedIn 调用。
     */
    public static void onPlayerLoggedIn(ServerPlayer player) {
        if (managedPlayers.contains(player.getUUID())) {
            sendManagedPacket(player, true);
        }
    }

    // -----------------------------------------------------------------------
    // 查询
    // -----------------------------------------------------------------------

    /** 查询某玩家是否由 EF 技能管理 */
    public static boolean isManaged(UUID id) {
        return managedPlayers.contains(id);
    }

    /**
     * 立即激活盾。供 ACTION_EVENT_SERVER 补丁使用，
     * 处理 preDelay 极小导致 tick 轮询来不及检测的情况。
     */
    public static void activateDirect(ServerPlayer player) {
        ShieldManager.activateForcedAndSync(player);
    }

    /** 返回所有 EF 管理玩家的 UUID 集合（供 tickTimeouts 跳过用） */
    public static Set<UUID> getManagedPlayerIds() {
        return managedPlayers;
    }

    // -----------------------------------------------------------------------
    // 每 tick 轮询
    // -----------------------------------------------------------------------

    /**
     * 每服务端 tick 调用。轮询所有 EF 玩家的 attacking() 状态，
     * attacking=true 时保持盾激活，false 时立即关闭（无冷却）。
     */
    public static void tick() {
        for (Map.Entry<UUID, ServerPlayerPatch> entry : playerPatches.entrySet()) {
            UUID id = entry.getKey();
            ServerPlayerPatch patch = entry.getValue();
            boolean attacking = patch.getEntityState().attacking();
            boolean shieldActive = ShieldManager.isActive(id);

            if (attacking && !shieldActive) {
                ServerPlayer player = (ServerPlayer) patch.getOriginal();
                if (isAllowedWeapon(player)) {
                    ShieldManager.activateForcedAndSync(player);
                }
            } else if (!attacking && shieldActive) {
                ShieldManager.deactivateNoCooldown(id);
            }
        }
    }

    // -----------------------------------------------------------------------
    // 内部
    // -----------------------------------------------------------------------

    /** 检查玩家主手武器是否允许触发反弹（EF武器类型 或 白名单匹配） */
    static boolean isAllowedWeapon(ServerPlayer player) {
        CapabilityItem cap = EpicFightCapabilities.getItemStackCapability(player.getMainHandItem());
        return ReflectSkill.ALLOWED_CATEGORIES.contains(cap.getWeaponCategory())
                || ItemMatcher.matches(player.getMainHandItem());
    }

    private static void sendManagedPacket(ServerPlayer player, boolean managed) {
        NetworkHandler.INSTANCE.sendTo(
                new PacketEfManaged(managed),
                player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT
        );
    }
}
