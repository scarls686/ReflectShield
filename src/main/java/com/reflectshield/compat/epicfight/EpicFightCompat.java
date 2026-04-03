package com.reflectshield.compat.epicfight;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Epic Fight 兼容入口。
 * 仅在 ModList.get().isLoaded("epicfight") 为 true 时由 ReflectShieldMod 调用。
 * 所有 yesman.epicfight.* 引用都隔离在此包内，保证无 EF 时不会触发 ClassLoader 错误。
 */
public class EpicFightCompat {

    public static void init(IEventBus modBus) {
        EpicFightItems.register(modBus);
        EpicFightSkills.register(modBus);
    }
}
