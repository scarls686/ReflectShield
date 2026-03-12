package com.reflectshield;

import com.reflectshield.client.ClientProxy;
import com.reflectshield.common.config.ModConfig;
import com.reflectshield.common.network.NetworkHandler;
import com.reflectshield.common.registry.ModSounds;
import com.reflectshield.common.util.ItemMatcher;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ReflectShieldMod.MOD_ID)
public class ReflectShieldMod {

    public static final String MOD_ID = "reflectshield";
    public static final Logger LOGGER = LogManager.getLogger();

    public ReflectShieldMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册音效
        ModSounds.SOUND_EVENTS.register(modBus);

        // 注册配置
        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.SPEC);

        // 公共初始化（网络注册）
        modBus.addListener(this::commonSetup);

        // 客户端专属初始化（所有客户端类引用都隔离在 ClientProxy 中）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientProxy.register(modBus));

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ItemMatcher.recompile(ModConfig.ITEM_WHITELIST.get());
        LOGGER.info("ReflectShield: Item whitelist compiled with {} entries.",
                ModConfig.ITEM_WHITELIST.get().size());
    }

    /**
     * 打开配置界面（仅客户端调用）。
     */
    public static void openConfigScreen() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientProxy::openConfigScreen);
    }
}
