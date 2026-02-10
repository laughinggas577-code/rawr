package com.rawr.automation.gui;

import com.rawr.automation.config.ModConfig;
import com.rawr.automation.modules.Module;
import com.rawr.automation.modules.ModuleManager;
import com.rawr.automation.modules.AutoMine;
import com.rawr.automation.modules.PathWalker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AutomationGuiScreen extends GuiScreen {

    private static final int STOP_ALL_ID = 5000;
    private static final int AUTO_MINE_TARGET_ID = 5001;

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

        AutoMine autoMine = (AutoMine) moduleManager.getModule("automine");
        if (autoMine != null) {
            autoMine.setTargetBlock(config.getString("AutoMine.TargetBlock", autoMine.getTargetBlock()));
        }

        for (Module module : moduleManager.getModules().values()) {
            orderedModules.add(module);
            buttonList.add(new GuiButton(id++, x, y, 200, 20, getModuleButtonText(module)));
            if (module instanceof AutoMine) {
                buttonList.add(new GuiButton(AUTO_MINE_TARGET_ID, x + 206, y, 120, 20, "Target: " + autoMine.getTargetBlock()));
            }
            y += 24;
        }

        y += 8;
        buttonList.add(new GuiButton(STOP_ALL_ID, x, y, 200, 20, EnumChatFormatting.RED + "Stop All"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == AUTO_MINE_TARGET_ID) {
            AutoMine autoMine = (AutoMine) moduleManager.getModule("automine");
            if (autoMine != null) {
                autoMine.cycleTargetBlock();
                config.setString("AutoMine.TargetBlock", autoMine.getTargetBlock());
                config.save();
                button.displayString = "Target: " + autoMine.getTargetBlock();
            }
            return;
        }

        if (button.id == STOP_ALL_ID) {
            moduleManager.disableAllModules();
            moduleManager.saveSettings(config);
            initGui();
            return;
        }

        if (button.id >= 0 && button.id < orderedModules.size()) {
            Module module = orderedModules.get(button.id);

            if (module instanceof PathWalker && !module.isEnabled()) {
                Minecraft.getMinecraft().displayGuiScreen(new PathWalkerTargetGuiScreen(this, (PathWalker) module, config));
                return;
            }

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
