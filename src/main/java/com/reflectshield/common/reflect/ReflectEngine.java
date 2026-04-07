package com.reflectshield.common.reflect;

import com.reflectshield.common.config.ModConfig;
import com.reflectshield.common.registry.ModSounds;
import com.reflectshield.common.util.ItemMatcher;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 碰撞检测与反弹物理执行。
 * 从 ReflectHandler 中提取的纯逻辑，不含事件监听。
 */
public final class ReflectEngine {

    private static final String REFLECTED_TAG = "rs_reflected";

    private ReflectEngine() {}

    /**
     * 对一个激活中的玩家执行碰撞检测和反弹。
     *
     * @param player       服务端玩家
     * @param skipWhitelist true 时跳过物品白名单检查（EF 路径已在技能中验证）
     */
    public static void tickPlayer(ServerPlayer player, boolean skipWhitelist) {
        if (!player.isAlive()) return;
        if (!skipWhitelist && !ItemMatcher.matches(player.getMainHandItem())) return;

        AABB shieldAABB = ShieldAABB.compute(player, player.getEyePosition(), player.getLookAngle().normalize());

        List<Projectile> projectiles = player.level().getEntitiesOfClass(Projectile.class, shieldAABB);
        UUID id = player.getUUID();

        for (Projectile proj : projectiles) {
            CompoundTag data = proj.getPersistentData();
            if (data.getBoolean(REFLECTED_TAG)) continue;
            if (proj.getOwner() != null && proj.getOwner().getUUID().equals(id)) continue;
            if (proj instanceof LargeFireball && ModConfig.FIREBALL_MODE.get() == 0) continue;

            doReflect(player, proj);
        }
    }

    /**
     * 执行反弹操作：修改投掷物速度方向，播放粒子和音效。
     */
    public static void doReflect(ServerPlayer player, Projectile projectile) {
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setNoPhysics(false);
            arrow.inGround = false;
        }

        Vec3 velocity = projectile.getDeltaMovement();
        double multiplier = ModConfig.REFLECT_SPEED_MULTIPLIER.get();
        int mode = ModConfig.REFLECT_MODE.get();

        double originalSpeed = velocity.length();
        if (originalSpeed < 0.05) originalSpeed = 1.6;

        Vec3 newVelocity;
        if (mode == 0) {
            newVelocity = player.getLookAngle().scale(originalSpeed * multiplier);
        } else {
            Vec3 normal = player.getLookAngle().normalize();
            double dot = velocity.dot(normal);
            newVelocity = velocity.subtract(normal.scale(2.0 * dot)).scale(multiplier);
        }

        projectile.setDeltaMovement(newVelocity);
        projectile.hasImpulse = true;

        if (projectile instanceof AbstractHurtingProjectile hurtingProj) {
            Vec3 dir = newVelocity.normalize();
            hurtingProj.xPower = dir.x * 0.1;
            hurtingProj.yPower = dir.y * 0.1;
            hurtingProj.zPower = dir.z * 0.1;
        }

        projectile.setOwner(player);
        projectile.getPersistentData().putBoolean(REFLECTED_TAG, true);

        if (player.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = projectile.position();
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    pos.x, pos.y, pos.z, 10, 0.2, 0.2, 0.2, 0.1);
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    pos.x, pos.y, pos.z, 6, 0.1, 0.1, 0.1, 0.05);
        }

        player.level().playSound(
                null,
                projectile.getX(), projectile.getY(), projectile.getZ(),
                ModSounds.REFLECT_CLASH.get(),
                SoundSource.BLOCKS,
                1.0f,
                0.9f + player.level().random.nextFloat() * 0.2f
        );
    }

    /** 检查投掷物是否已被反弹过 */
    public static boolean isReflected(Projectile projectile) {
        return projectile.getPersistentData().getBoolean(REFLECTED_TAG);
    }
}
