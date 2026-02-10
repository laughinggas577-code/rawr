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
    private static final int AUTO_MINE_MENU_ID = 5001;
    private static final String WELCOME_KEY = "AutomationGuiWelcomed";

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

        if (!config.getBoolean(WELCOME_KEY, false) && Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("\u00a76[Rawr] \u00a7fWelcome! Open modules and get moving."));
            config.setBoolean(WELCOME_KEY, true);
            config.save();
        }

        AutoMine autoMine = (AutoMine) moduleManager.getModule("automine");
        if (autoMine != null) {
            autoMine.setTargetBlock(config.getString("AutoMine.TargetBlock", autoMine.getTargetBlock()));
        }

        for (Module module : moduleManager.getModules().values()) {
            orderedModules.add(module);
            buttonList.add(new GuiButton(id++, x, y, 200, 20, getModuleButtonText(module)));
            if (module instanceof AutoMine) {
                buttonList.add(new GuiButton(AUTO_MINE_MENU_ID, x + 206, y, 120, 20, "Block Menu"));
            }
            y += 24;
        }

        y += 8;
        buttonList.add(new GuiButton(STOP_ALL_ID, x, y, 200, 20, EnumChatFormatting.RED + "Stop All"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == AUTO_MINE_MENU_ID) {
            AutoMine autoMine = (AutoMine) moduleManager.getModule("automine");
            if (autoMine != null) {
                Minecraft.getMinecraft().displayGuiScreen(new OreSelectorGuiScreen(this, autoMine, config));
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
        int left = this.width / 2 - 130;
        int top = this.height / 2 - 110;
        drawRect(left, top, left + 360, top + 260, 0xC0101010);
        drawRect(left, top, left + 360, top + 20, 0xC0202020);
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GOLD + "Rawr Automation", this.width / 2, top + 6, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + "Modules / Tools", this.width / 2, top + 24, 0xC0C0C0);
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
