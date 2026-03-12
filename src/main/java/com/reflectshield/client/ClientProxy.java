package com.reflectshield.client;

import com.reflectshield.client.gui.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public class ClientProxy {

    public static void register(IEventBus modBus) {
        modBus.addListener(KeyBindings::onRegisterKeyMappings);
        modBus.addListener(ClientProxy::clientSetup);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        // 注册 Forge Mod 菜单中的"配置"按钮
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new ConfigScreen(parent)
                )
        );
    }

    public static void openConfigScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new ConfigScreen(mc.screen)));
    }
}
