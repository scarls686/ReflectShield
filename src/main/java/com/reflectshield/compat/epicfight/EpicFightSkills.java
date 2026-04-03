package com.reflectshield.compat.epicfight;

import com.reflectshield.ReflectShieldMod;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.skill.Skill;

/**
 * 在 Epic Fight 的 SkillBuildEvent 中注册反弹技能。
 */
public class EpicFightSkills {

    public static Skill REFLECT_PROJECTILE;

    public static void register(IEventBus modBus) {
        modBus.addListener(EpicFightSkills::onSkillBuild);
    }

    private static void onSkillBuild(SkillBuildEvent event) {
        SkillBuildEvent.ModRegistryWorker worker =
                event.createRegistryWorker(ReflectShieldMod.MOD_ID);
        REFLECT_PROJECTILE = worker.build(
                "reflect_projectile",
                ReflectSkill::new,
                ReflectSkill.newBuilder()
        );
    }
}
