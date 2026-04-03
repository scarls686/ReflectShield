package com.reflectshield.compat.epicfight;

import com.reflectshield.ReflectShieldMod;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.world.item.SkillBookItem;

/**
 * 反弹技能书：继承 Epic Fight 的 SkillBookItem，
 * 默认实例自动写入 NBT skill=reflectshield:reflect_projectile。
 */
public class ReflectSkillBookItem extends SkillBookItem {

    private static final String SKILL_ID =
            ReflectShieldMod.MOD_ID + ":reflect_projectile";

    public ReflectSkillBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        SkillBookItem.setContainingSkill(SKILL_ID, stack);
        return stack;
    }
}
