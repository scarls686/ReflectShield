package com.reflectshield.common.registry;

import com.reflectshield.ReflectShieldMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ReflectShieldMod.MOD_ID);

    /** 随机播放 clash0/1/2 中的一个（由 sounds.json 控制随机权重） */
    public static final RegistryObject<SoundEvent> REFLECT_CLASH = SOUND_EVENTS.register(
            "reflect_clash",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ReflectShieldMod.MOD_ID, "reflect_clash")
            )
    );
}
