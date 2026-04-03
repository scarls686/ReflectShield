package com.reflectshield.compat.epicfight;

import java.util.Set;
import java.util.UUID;

import com.reflectshield.common.handler.ReflectHandler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.passive.PassiveSkill;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.CapabilityItem.WeaponCategories;
import yesman.epicfight.world.capabilities.item.WeaponCategory;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;

/**
 * 反弹被动技能。
 * 学习后，玩家在战斗模式发动攻击的判定帧（PHASE_LEVEL 从预备变为 attacking）时自动激活反弹盾。
 * 要求手持武器为剑类（SWORD / UCHIGATANA / TACHI / LONGSWORD）。
 */
public class ReflectSkill extends PassiveSkill {

    /** ACTION_EVENT_SERVER 监听器 UUID：检测攻击动画开始，标记武器已验证 */
    private static final UUID ACTION_UUID = UUID.fromString("a3f2c1d0-4b5e-6f7a-8b9c-0d1e2f3a4b5c");

    private static final Set<WeaponCategory> ALLOWED_CATEGORIES = Set.of(
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
    public void onInitiate(SkillContainer container) {
        super.onInitiate(container);

        if (container.getExecutor().isLogicalClient()) return;

        ServerPlayerPatch serverPatch = container.getServerExecutor();

        // 向 ReflectHandler 注册：持续轮询此玩家的 attacking() 状态
        ReflectHandler.registerEfPlayer(serverPatch.getOriginal(), serverPatch);

        // ACTION_EVENT_SERVER：攻击动画开始时验证武器类型
        // 拔刀斩（动画名含 "sheath"）：动画开始即出招，直接激活反弹
        // 其他攻击：设置 armed 标志，由 onServerTick 轮询 attacking() 上升沿时激活
        serverPatch.getEventListener()
                .addEventListener(EventType.ACTION_EVENT_SERVER, ACTION_UUID, (event) -> {
                    if (!(event.getAnimation().get() instanceof AttackAnimation)) return;

                    ServerPlayer player = (ServerPlayer) event.getPlayerPatch().getOriginal();
                    ItemStack mainHand = player.getMainHandItem();
                    CapabilityItem cap = EpicFightCapabilities.getItemStackCapability(mainHand);
                    WeaponCategory category = cap.getWeaponCategory();
                    if (!ALLOWED_CATEGORIES.contains(category)) return;

                    net.minecraft.resources.ResourceLocation animId = event.getAnimation().registryName();
                    String animPath = animId != null ? animId.getPath() : "";

                    if (animPath.contains("sheath")) {
                        ReflectHandler.activateFromEf(player);
                    } else {
                        ReflectHandler.setEfArmed(player.getUUID(), true);
                    }
                });
    }

    @Override
    public void onRemoved(SkillContainer container) {
        super.onRemoved(container);

        if (container.getExecutor().isLogicalClient()) return;

        ServerPlayerPatch serverPatch = container.getServerExecutor();

        ReflectHandler.unregisterEfPlayer(serverPatch.getOriginal());
        serverPatch.getEventListener().removeListener(EventType.ACTION_EVENT_SERVER, ACTION_UUID);
    }
}
