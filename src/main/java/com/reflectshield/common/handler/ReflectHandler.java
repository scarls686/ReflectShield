package com.reflectshield.common.handler;

import com.reflectshield.ReflectShieldMod;
import com.reflectshield.common.config.ModConfig;
import com.reflectshield.common.reflect.ReflectEngine;
import com.reflectshield.common.reflect.ShieldManager;
import com.reflectshield.common.util.ItemMatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Forge 事件监听器 — 反弹盾的核心入口。
 * <p>
 * 职责：
 * <ul>
 *   <li>每 tick 调用 ShieldManager 处理超时，对激活玩家执行碰撞检测</li>
 *   <li>按键路径的激活/停用（setPlayerPressing）</li>
 *   <li>投掷物命中拦截（ProjectileImpactEvent）</li>
 *   <li>大火球接管（AttackEntityEvent）</li>
 *   <li>玩家登录时补发 EF 管理包</li>
 * </ul>
 * <p>
 * 不含任何 EF 类型引用 — EF 相关逻辑全部在 compat.epicfight.EfReflectHandler 中。
 */
@Mod.EventBusSubscriber(modid = ReflectShieldMod.MOD_ID)
public class ReflectHandler {

    // -----------------------------------------------------------------------
    // 按键路径
    // -----------------------------------------------------------------------

    /**
     * 由 PacketReflectKey.handle() 在服务端主线程调用。
     * 若玩家已由 EF 被动技能管理，忽略按键。
     */
    public static void setPlayerPressing(ServerPlayer player, boolean pressing) {
        UUID id = player.getUUID();

        // EF 路径优先：已装备被动技能的玩家，按键不触发
        if (pressing && isEfManaged(id)) return;

        if (pressing) {
            ShieldManager.activate(id);
        } else {
            ShieldManager.deactivate(id);
        }
    }

    // -----------------------------------------------------------------------
    // 每 tick
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // EF 轮询（仅在 EF 加载时）
        if (ModList.get().isLoaded("epicfight")) {
            com.reflectshield.compat.epicfight.EfReflectHandler.tick();
        }

        // 超时处理（EF 管理的玩家跳过，其持续时间由 attacking() 控制）
        java.util.Set<java.util.UUID> efSkipIds = ModList.get().isLoaded("epicfight")
                ? com.reflectshield.compat.epicfight.EfReflectHandler.getManagedPlayerIds()
                : null;
        ShieldManager.tickTimeouts(null, efSkipIds);

        // 对每个激活中的玩家执行碰撞检测
        for (UUID id : ShieldManager.getActivePlayerIds()) {
            ServerPlayer player = getServerPlayer(id);
            if (player == null || !player.isAlive()) {
                ShieldManager.deactivateNoCooldown(id);
                continue;
            }

            boolean skipWhitelist = isEfManaged(id);
            ReflectEngine.tickPlayer(player, skipWhitelist);
        }
    }

    // -----------------------------------------------------------------------
    // 投掷物命中拦截
    // -----------------------------------------------------------------------

    /**
     * 当 SHIELD_USE_PLAYER_HITBOX=true 时，投掷物飞向玩家会在 tick 检测之前命中。
     * 此处拦截并执行反弹。
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!ModConfig.SHIELD_USE_PLAYER_HITBOX.get()) return;

        Projectile proj = event.getProjectile();
        if (ReflectEngine.isReflected(proj)) return;

        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hitResult)) return;
        if (!(hitResult.getEntity() instanceof ServerPlayer player)) return;

        UUID id = player.getUUID();
        if (!ShieldManager.isActive(id)) return;
        if (!player.isAlive()) return;

        if (!isEfManaged(id) && !ItemMatcher.matches(player.getMainHandItem())) return;

        net.minecraft.world.entity.Entity owner = proj.getOwner();
        if (owner != null && owner.getUUID().equals(id)) return;

        if (proj instanceof LargeFireball && ModConfig.FIREBALL_MODE.get() == 0) return;

        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        ReflectEngine.doReflect(player, proj);
    }

    // -----------------------------------------------------------------------
    // 大火球接管
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (ModConfig.FIREBALL_MODE.get() != 2) return;
        if (!(event.getTarget() instanceof LargeFireball)) return;
        event.setCanceled(true);
    }

    // -----------------------------------------------------------------------
    // 玩家登录
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ModList.get().isLoaded("epicfight")) {
            com.reflectshield.compat.epicfight.EfReflectHandler.onPlayerLoggedIn(player);
        }
    }

    // -----------------------------------------------------------------------
    // 工具方法
    // -----------------------------------------------------------------------

    /**
     * 查询某玩家是否由 EF 技能管理。
     * 通过 ModList 保护，没装 EF 时始终返回 false。
     */
    private static boolean isEfManaged(UUID id) {
        if (!ModList.get().isLoaded("epicfight")) return false;
        return com.reflectshield.compat.epicfight.EfReflectHandler.isManaged(id);
    }

    private static ServerPlayer getServerPlayer(UUID id) {
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(id);
    }
}
