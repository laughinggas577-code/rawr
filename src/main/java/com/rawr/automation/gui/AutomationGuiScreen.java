package com.rawr.automation.gui;

import com.rawr.automation.config.ModConfig;
import com.rawr.automation.modules.Module;
import com.rawr.automation.modules.ModuleManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AutomationGuiScreen extends GuiScreen {

    private static final int STOP_ALL_ID = 5000;

    private final ModuleManager moduleManager;
    private final ModConfig config;
    private final List<Module> orderedModules = new ArrayList<Module>();

    public AutomationGuiScreen(ModuleManager moduleManager, ModConfig config) {
        this.moduleManager = moduleManager;
        this.config = config;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.orderedModules.clear();

        int x = this.width / 2 - 100;
        int y = this.height / 2 - 80;
        int id = 0;

        for (Module module : moduleManager.getModules().values()) {
            orderedModules.add(module);
            buttonList.add(new GuiButton(id++, x, y, 200, 20, getModuleButtonText(module)));
            y += 24;
        }

        y += 8;
        buttonList.add(new GuiButton(STOP_ALL_ID, x, y, 200, 20, EnumChatFormatting.RED + "Stop All"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == STOP_ALL_ID) {
            moduleManager.disableAllModules();
            moduleManager.saveSettings(config);
            initGui();
            return;
        }

        if (button.id >= 0 && button.id < orderedModules.size()) {
            Module module = orderedModules.get(button.id);
            module.toggle();
            moduleManager.saveSettings(config);
            button.displayString = getModuleButtonText(module);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GOLD + "Rawr Automation", this.width / 2, this.height / 2 - 102, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, "Toggle modules", this.width / 2, this.height / 2 - 90, 0xC0C0C0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private String getModuleButtonText(Module module) {
        String status = module.isEnabled() ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF";
        return module.getName() + " : " + status;
    }
}
