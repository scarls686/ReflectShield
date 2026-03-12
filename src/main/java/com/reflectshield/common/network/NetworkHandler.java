package com.reflectshield.common.network;

import com.reflectshield.ReflectShieldMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static SimpleChannel INSTANCE;

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(ReflectShieldMod.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        INSTANCE.registerMessage(
                nextId(),
                PacketReflectKey.class,
                PacketReflectKey::encode,
                PacketReflectKey::decode,
                PacketReflectKey::handle
        );
    }
}
