package com.reflectshield.common.network;

import com.reflectshield.client.ClientEventHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：通知本地玩家盾已被激活（由 EF 攻击事件触发）。
 * 客户端收到后设置 isReflecting = true，使 debug 碰撞箱可见。
 */
public class PacketShieldActivate {

    public PacketShieldActivate() {}

    public static void encode(PacketShieldActivate msg, FriendlyByteBuf buf) {}

    public static PacketShieldActivate decode(FriendlyByteBuf buf) {
        return new PacketShieldActivate();
    }

    public static void handle(PacketShieldActivate msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientEventHandler::onShieldActivatedByServer)
        );
        ctx.get().setPacketHandled(true);
    }
}
