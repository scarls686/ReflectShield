package com.reflectshield.compat.epicfight;

import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.passive.PassiveSkill;
import yesman.epicfight.world.capabilities.item.CapabilityItem.WeaponCategories;
import yesman.epicfight.world.capabilities.item.WeaponCategory;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;

/**
 * 反弹被动技能。
 * <p>
 * 主路径：EfReflectHandler 每 tick 轮询 attacking() 状态，
 * 在攻击判定帧期间自动激活反弹盾，判定帧结束时立即关闭。
 * <p>
 * 补丁：对 preDelay ≤ transitionTime 的攻击动画（如拔刀斩），
 * LinkAnimation 过渡会吃掉 attacking 窗口，导致 tick 轮询来不及检测。
 * 通过 ACTION_EVENT_SERVER 在动画开始时立即激活来补偿。
 */
public class ReflectSkill extends PassiveSkill {

    private static final UUID EVENT_UUID =
            UUID.fromString("a3f2c1d0-4b5e-6f7a-8b9c-0d1e2f3a4b5c");

    /** LinkAnimation 过渡的最大耗时（秒），preDelay 小于等于此值时需要立即激活 */
    private static final float TRANSITION_THRESHOLD = 0.05F;

    static final Set<WeaponCategory> ALLOWED_CATEGORIES = Set.of(
            WeaponCategories.SWORD,
            WeaponCategories.UCHIGATANA,
            WeaponCategories.TACHI,
            WeaponCategories.LONGSWORD
    );

    public ReflectSkill(SkillBuilder<ReflectSkill> builder) {
        super(builder);
    }

    @SuppressWarnings("unchecked")
    public static SkillBuilder<ReflectSkill> newBuilder() {
        return (SkillBuilder<ReflectSkill>) (SkillBuilder<?>) PassiveSkill.createPassiveBuilder();
    }

    @Override
    public Set<WeaponCategory> getAvailableWeaponCategories() {
        return ALLOWED_CATEGORIES;
    }

    @Override
    public void onInitiate(SkillContainer container) {
        super.onInitiate(container);

        if (container.getExecutor().isLogicalClient()) return;

        ServerPlayerPatch serverPatch = container.getServerExecutor();
        EfReflectHandler.register(serverPatch.getOriginal(), serverPatch);

        // 补丁：对 preDelay 极小的攻击动画，tick 轮询来不及捕捉 attacking 上升沿，
        // 在动画开始时直接激活盾。
        serverPatch.getEventListener()
                .addEventListener(EventType.ACTION_EVENT_SERVER, EVENT_UUID, (event) -> {
                    if (!(event.getAnimation().get() instanceof AttackAnimation attackAnim)) return;

                    // 检查武器类型（EF允许类型 或 白名单）
                    ServerPlayer player = (ServerPlayer) event.getPlayerPatch().getOriginal();
                    if (!EfReflectHandler.isAllowedWeapon(player)) return;

                    // 只对 preDelay 极小的动画立即激活（普通攻击由 tick 轮询处理）
                    if (attackAnim.phases.length > 0
                            && attackAnim.phases[0].preDelay <= TRANSITION_THRESHOLD) {
                        EfReflectHandler.activateDirect(player);
                    }
                });
    }

    @Override
    public void onRemoved(SkillContainer container) {
        super.onRemoved(container);

        if (container.getExecutor().isLogicalClient()) return;

        ServerPlayerPatch serverPatch = container.getServerExecutor();
        EfReflectHandler.unregister(serverPatch.getOriginal());
        serverPatch.getEventListener().removeListener(EventType.ACTION_EVENT_SERVER, EVENT_UUID);
    }
}
