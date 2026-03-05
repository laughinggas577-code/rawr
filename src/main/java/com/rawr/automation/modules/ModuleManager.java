package com.rawr.automation.modules;

import com.rawr.automation.config.ModConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ClientTickEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleManager {

    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager() {
        registerModule(new AutoMine());
        registerModule(new AutoEat());
        registerModule(new AutoFish());
        registerModule(new AutoFarm());
        registerModule(new AutoAttack());
        registerModule(new PathWalker());
    }

    private void registerModule(Module module) {
        modules.put(module.getName().toLowerCase(), module);
    }

    public Module getModule(String name) {
        return modules.get(name.toLowerCase());
    }

    public Map<String, Module> getModules() {
        return modules;
    }

    public void loadSettings(ModConfig config) {
        for (Module module : modules.values()) {
            boolean wasEnabled = config.getBoolean(module.getName(), false);
            if (wasEnabled) {
                module.setEnabled(true);
            }
        }
    }

    public void saveSettings(ModConfig config) {
        for (Module module : modules.values()) {
            config.setBoolean(module.getName(), module.isEnabled());
        }
        config.save();
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        for (Module module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.onTick();
                } catch (Exception e) {
                    // Prevent one module crash from breaking others
                }
            }
        }
    }
}
