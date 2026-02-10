package com.rawr.automation.gui;

import com.rawr.automation.config.ModConfig;
import com.rawr.automation.modules.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class AutomationKeybindHandler {

    private static final String KEY_CATEGORY = "Rawr Automation";

    private final ModuleManager moduleManager;
    private final ModConfig config;

    private final KeyBinding openGuiKey = new KeyBinding("key.rawr.open_gui", Keyboard.KEY_L, KEY_CATEGORY);
    private final KeyBinding stopAllKey = new KeyBinding("key.rawr.stop_all", Keyboard.KEY_NONE, KEY_CATEGORY);

    public AutomationKeybindHandler(ModuleManager moduleManager, ModConfig config) {
        this.moduleManager = moduleManager;
        this.config = config;

        ClientRegistry.registerKeyBinding(openGuiKey);
        ClientRegistry.registerKeyBinding(stopAllKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openGuiKey.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new AutomationGuiScreen(moduleManager, config));
        }

        if (stopAllKey.isPressed()) {
            moduleManager.disableAllModules();
            moduleManager.saveSettings(config);
        }
    }
}
