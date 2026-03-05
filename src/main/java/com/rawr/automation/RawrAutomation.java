package com.rawr.automation;

import com.rawr.automation.command.AutomationCommandHandler;
import com.rawr.automation.config.ModConfig;
import com.rawr.automation.gui.HudRenderer;
import com.rawr.automation.gui.PathRenderer;
import com.rawr.automation.modules.ModuleManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.File;

@Mod(RawrAutomation.MODID)
public class RawrAutomation {

    public static final String MODID = "rawrautomation";
    public static final String NAME = "Rawr Automation";
    public static final String VERSION = "2.0.0";

    private static ModuleManager moduleManager;
    private static ModConfig config;

    public RawrAutomation(IEventBus modBus) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("rawrautomation.properties").toFile();
        config = new ModConfig(configFile);
        config.load();

        moduleManager = new ModuleManager();
        moduleManager.loadSettings(config);

        NeoForge.EVENT_BUS.register(moduleManager);
        NeoForge.EVENT_BUS.register(new HudRenderer(moduleManager));
        NeoForge.EVENT_BUS.register(new PathRenderer(moduleManager));
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        AutomationCommandHandler.register(event.getDispatcher(), moduleManager, config);
    }

    public static ModuleManager getModuleManager() {
        return moduleManager;
    }

    public static ModConfig getConfig() {
        return config;
    }
}
