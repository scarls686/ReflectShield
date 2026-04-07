package com.reflectshield.common.network;

import com.reflectshield.compat.epicfight.EfClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步 EF 被动技能管理状态。
 * 玩家学习/移除技能时发送，客户端据此决定按键路径是否生效。
 */
public class PacketEfManaged {

    private final boolean managed;

    public PacketEfManaged(boolean managed) {
        this.managed = managed;
    }

    public static void encode(PacketEfManaged msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.managed);
    }

    public static PacketEfManaged decode(FriendlyByteBuf buf) {
        return new PacketEfManaged(buf.readBoolean());
    }

    public static void handle(PacketEfManaged msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                EfClientHandler.setManaged(msg.managed)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
