package com.rawr.automation;

import com.rawr.automation.command.AutomationCommandHandler;
import com.rawr.automation.config.ModConfig;
import com.rawr.automation.gui.HudRenderer;
import com.rawr.automation.modules.ModuleManager;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = RawrAutomation.MODID, name = RawrAutomation.NAME, version = RawrAutomation.VERSION, clientSideOnly = true)
public class RawrAutomation {

    public static final String MODID = "rawrautomation";
    public static final String NAME = "Rawr Automation";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MODID)
    public static RawrAutomation instance;

    private ModuleManager moduleManager;
    private ModConfig config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new ModConfig(event.getSuggestedConfigurationFile());
        config.load();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        moduleManager = new ModuleManager();
        moduleManager.loadSettings(config);

        MinecraftForge.EVENT_BUS.register(moduleManager);
        MinecraftForge.EVENT_BUS.register(new HudRenderer(moduleManager));

        ClientCommandHandler.instance.registerCommand(new AutomationCommandHandler(moduleManager, config));
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ModConfig getConfig() {
        return config;
    }
}
