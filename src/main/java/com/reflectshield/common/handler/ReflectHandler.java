package com.reflectshield.common.handler;

import com.reflectshield.ReflectShieldMod;
import com.reflectshield.common.config.ModConfig;
import com.reflectshield.common.util.ItemMatcher;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.reflectshield.common.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ReflectShieldMod.MOD_ID)
public class ReflectHandler {

    /** 激活中的玩家：UUID → 激活开始时间（ms） */
    private static final Map<UUID, Long> playerShieldActivations = new ConcurrentHashMap<>();

    /** 冷却记录：UUID → 冷却结束时间（ms） */
    private static final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    /** 防重复反弹的 NBT 键 */
    private static final String REFLECTED_TAG = "rs_reflected";

    // -----------------------------------------------------------------------
    // 网络包回调
    // -----------------------------------------------------------------------

    /**
     * 由 PacketReflectKey.handle() 在服务端主线程调用。
     */
    public static void setPlayerPressing(ServerPlayer player, boolean pressing) {
        UUID id = player.getUUID();
        if (pressing) {
            if (!isOnCooldown(id)) {
                playerShieldActivations.put(id, System.currentTimeMillis());
            }
        } else {
            if (playerShieldActivations.containsKey(id)) {
                playerShieldActivations.remove(id);
                // 记录冷却结束时间
                long cooldownMs = ModConfig.SHIELD_COOLDOWN_MS.get();
                if (cooldownMs > 0) {
                    playerCooldowns.put(id, System.currentTimeMillis() + cooldownMs);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // 每 tick 检测
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iter = playerShieldActivations.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<UUID, Long> entry = iter.next();
            UUID id = entry.getKey();
            long startTime = entry.getValue();

            // 超时：移除激活，记录冷却
            long durationMs = ModConfig.SHIELD_DURATION_MS.get();
            if (now - startTime > durationMs) {
                iter.remove();
                long cooldownMs = ModConfig.SHIELD_COOLDOWN_MS.get();
                if (cooldownMs > 0) {
                    playerCooldowns.put(id, now + cooldownMs);
                }
                continue;
            }

            // 寻找对应的 ServerPlayer
            // 注意：在服务端 tick 中需要从所有在线玩家里找
            // ServerTickEvent 可通过 event.getServer() 获取服务端，但 Forge 1.20.1 中此字段需用 server tick 上下文
            // 使用静态缓存的服务端引用（由主类在 ServerStartedEvent 中设置）
            ServerPlayer player = getServerPlayer(id);
            if (player == null || !player.isAlive()) {
                iter.remove();
                continue;
            }

            // 检查物品白名单
            if (!ItemMatcher.matches(player.getMainHandItem())) {
                continue;
            }

            // 计算碰撞盒（玩家自身模式 or 专属碰撞盒）
            AABB shieldAABB = ModConfig.SHIELD_USE_PLAYER_HITBOX.get()
                    ? player.getBoundingBox()
                    : computeShieldAABB(player);

            // 查询碰撞盒内的投掷物
            List<Projectile> projectiles = player.level().getEntitiesOfClass(
                    Projectile.class, shieldAABB
            );

            for (Projectile proj : projectiles) {
                // 跳过已反弹过的投掷物
                CompoundTag data = proj.getPersistentData();
                if (data.getBoolean(REFLECTED_TAG)) continue;
                // 跳过归属于该玩家自己发射的（可选，避免自伤）
                // 若要允许反弹自己的投掷物，注释掉下面两行
                if (proj.getOwner() != null && proj.getOwner().getUUID().equals(id)) continue;

                // 大火球处理模式 0：忽略，交由原版处理（拳打反弹）
                if (proj instanceof LargeFireball && ModConfig.FIREBALL_MODE.get() == 0) continue;
                // 大火球处理模式 2（接管）：已被 onAttackEntity 阻止原版拳击，本模组负责全部反弹

                doReflect(player, proj);
            }
        }
    }

    // -----------------------------------------------------------------------
    // 核心算法
    // -----------------------------------------------------------------------

    /**
     * 计算玩家面前与视线垂直的虚拟碰撞盒（服务端）。
     * 以视线方向为法线，在法平面内展开宽×高，沿法线方向厚度为 shieldDepth。
     */
    private static AABB computeShieldAABB(ServerPlayer player) {
        double W = ModConfig.SHIELD_WIDTH.get();
        double H = ModConfig.SHIELD_HEIGHT.get();
        double D = ModConfig.SHIELD_DEPTH.get();
        double dist = ModConfig.SHIELD_DISTANCE.get();

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        // 碰撞盒中心点
        Vec3 center = eye.add(look.scale(dist));

        // 计算法平面内的 right 和 up 向量
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();
        // 当玩家垂直向上/下看时，right 接近零向量，退化处理
        if (right.lengthSqr() < 1e-6) {
            right = new Vec3(1, 0, 0);
        }
        Vec3 up = right.cross(look).normalize();

        // 半范围
        double hw = W / 2.0;
        double hh = H / 2.0;
        double hd = D / 2.0;

        // 8 个顶点 = center ± hw*right ± hh*up ± hd*look
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

    /**
     * 执行反弹操作，修改投掷物速度方向，并播放粒子和音效。
     */
    private static void doReflect(ServerPlayer player, Projectile projectile) {
        // 若是箭矢，先清除插地状态（AT 已将 inGround 设为 public）
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setNoPhysics(false);
            arrow.inGround = false;
        }

        Vec3 velocity = projectile.getDeltaMovement();
        double multiplier = ModConfig.REFLECT_SPEED_MULTIPLIER.get();
        int mode = ModConfig.REFLECT_MODE.get();

        // 确保速度不为零：若原速度过小则使用默认值 1.6（与骷髅射箭速度相当）
        double originalSpeed = velocity.length();
        if (originalSpeed < 0.05) originalSpeed = 1.6;

        Vec3 newVelocity;
        if (mode == 0) {
            // 准星方向：投掷物沿玩家瞄准方向飞出
            newVelocity = player.getLookAngle().scale(originalSpeed * multiplier);
        } else {
            // 物理反射：以玩家视线为法线计算镜面反射
            Vec3 normal = player.getLookAngle().normalize();
            double dot = velocity.dot(normal);
            newVelocity = velocity.subtract(normal.scale(2.0 * dot)).scale(multiplier);
        }

        // 应用新速度，并通知客户端速度已改变
        projectile.setDeltaMovement(newVelocity);
        projectile.hasImpulse = true;

        // 大火球：将 xPower/yPower/zPower 设为反弹方向 × 0.1
        // 模仿原版 AbstractHurtingProjectile.hurt() 的做法：
        //   speed = lookAngle; xPower = lookAngle.x * 0.1（使火球持续加速向目标方向）
        // 清零会导致每 tick 速度 × 0.95 衰减，火球悬停。
        if (projectile instanceof AbstractHurtingProjectile hurtingProj) {
            Vec3 dir = newVelocity.normalize();
            hurtingProj.xPower = dir.x * 0.1;
            hurtingProj.yPower = dir.y * 0.1;
            hurtingProj.zPower = dir.z * 0.1;
        }

        // 将伤害归属转移给反弹玩家
        projectile.setOwner(player);
        // 标记防止重复反弹
        projectile.getPersistentData().putBoolean(REFLECTED_TAG, true);

        // 服务端广播粒子效果
        if (player.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = projectile.position();
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    pos.x, pos.y, pos.z, 10, 0.2, 0.2, 0.2, 0.1);
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    pos.x, pos.y, pos.z, 6, 0.1, 0.1, 0.1, 0.05);
        }

        // 播放音效（null 表示对所有玩家可见，SoundSource.BLOCKS 确保不受玩家静音设置影响）
        player.level().playSound(
                null,
                projectile.getX(), projectile.getY(), projectile.getZ(),
                ModSounds.REFLECT_CLASH.get(),
                SoundSource.BLOCKS,
                1.0f,
                // 随机音调 0.9~1.1，避免每次完全相同
                0.9f + player.level().random.nextFloat() * 0.2f
        );
    }

    /**
     * Mode 2（接管）：阻止玩家用拳击反弹大火球，由本模组碰撞盒统一处理。
     * AttackEntityEvent 在 Player.attack() 最开头触发，取消后 hurt() 不会被调用。
     * 条件：该玩家的盾当前处于激活状态（碰撞盒存在），确保只在有效反弹期间拦截。
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (ModConfig.FIREBALL_MODE.get() != 2) return;
        if (!(event.getTarget() instanceof LargeFireball)) return;
        // Mode 2：完全接管，无论盾是否激活，永远阻止原版拳击反弹大火球
        event.setCanceled(true);
    }

    /**
     * 当 SHIELD_USE_PLAYER_HITBOX=true 时，投掷物飞向玩家会触发 onHit(EntityHitResult)
     * 导致伤害在 tick 检测之前已经生效。此处拦截 ProjectileImpactEvent，
     * 若玩家盾处于激活状态，取消命中并执行反弹。
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!ModConfig.SHIELD_USE_PLAYER_HITBOX.get()) return;

        Projectile proj = event.getProjectile();
        CompoundTag data = proj.getPersistentData();
        if (data.getBoolean(REFLECTED_TAG)) return;

        // 仅处理命中实体（EntityHitResult）
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hitResult)) return;
        if (!(hitResult.getEntity() instanceof ServerPlayer player)) return;

        UUID id = player.getUUID();
        if (!playerShieldActivations.containsKey(id)) return;
        if (!player.isAlive()) return;

        // 检查物品白名单
        if (!ItemMatcher.matches(player.getMainHandItem())) return;

        // 跳过归属于该玩家自身的投掷物
        net.minecraft.world.entity.Entity owner = proj.getOwner();
        if (owner != null && owner.getUUID().equals(id)) return;

        // 大火球 Mode 0：忽略
        if (proj instanceof LargeFireball && ModConfig.FIREBALL_MODE.get() == 0) return;

        // 跳过命中实体逻辑（不调用 onHit，投掷物继续飞行），然后改变其速度方向
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        doReflect(player, proj);
    }

    // -----------------------------------------------------------------------
    // 工具方法
    // -----------------------------------------------------------------------

    private static boolean isOnCooldown(UUID id) {
        Long cooldownEnd = playerCooldowns.get(id);
        if (cooldownEnd == null) return false;
        if (System.currentTimeMillis() >= cooldownEnd) {
            playerCooldowns.remove(id);
            return false;
        }
        return true;
    }

    /**
     * 通过 UUID 从所有在线玩家中查找 ServerPlayer。
     * 使用 ServerLifecycleHooks 获取服务端实例。
     */
    private static ServerPlayer getServerPlayer(UUID id) {
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(id);
    }

    /** 供外部查询某玩家是否仍在冷却中（用于客户端 GUI 显示） */
    public static boolean isPlayerOnCooldown(UUID id) {
        return isOnCooldown(id);
    }

    /** 供外部查询某玩家的冷却剩余毫秒数 */
    public static long getCooldownRemaining(UUID id) {
        Long cooldownEnd = playerCooldowns.get(id);
        if (cooldownEnd == null) return 0L;
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

}
