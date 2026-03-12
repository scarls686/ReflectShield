package com.reflectshield.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

public class KeyBindings {

    public static final KeyMapping REFLECT_KEY = new KeyMapping(
            "key.reflectshield.reflect",
            InputConstants.Type.MOUSE,
            InputConstants.MOUSE_BUTTON_LEFT,
            "key.categories.reflectshield"
    );

    /** 打开配置界面的按键，默认 R 键 */
    public static final KeyMapping CONFIG_KEY = new KeyMapping(
            "key.reflectshield.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.reflectshield"
    );

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(REFLECT_KEY);
        event.register(CONFIG_KEY);
    }
}
