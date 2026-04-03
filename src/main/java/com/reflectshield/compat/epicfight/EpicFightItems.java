package com.reflectshield.compat.epicfight;

import com.reflectshield.ReflectShieldMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 注册反弹技能书物品（软依赖 Epic Fight，仅在 EF 存在时注册）。
 */
public class EpicFightItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ReflectShieldMod.MOD_ID);

    public static final RegistryObject<Item> REFLECT_SKILL_BOOK = ITEMS.register(
            "reflect_skill_book",
            () -> new ReflectSkillBookItem(new Item.Properties().stacksTo(1))
    );

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
