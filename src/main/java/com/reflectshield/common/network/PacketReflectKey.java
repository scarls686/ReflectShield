package com.reflectshield.common.network;

import com.reflectshield.common.handler.ReflectHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketReflectKey {

    /** true = 按下，false = 释放 */
    private final boolean pressing;

    public PacketReflectKey(boolean pressing) {
        this.pressing = pressing;
    }

    public static void encode(PacketReflectKey msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.pressing);
    }

    public static PacketReflectKey decode(FriendlyByteBuf buf) {
        return new PacketReflectKey(buf.readBoolean());
    }

    public static void handle(PacketReflectKey msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                ReflectHandler.setPlayerPressing(sender, msg.pressing);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
