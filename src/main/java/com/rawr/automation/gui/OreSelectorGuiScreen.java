package com.rawr.automation.gui;

import com.rawr.automation.config.ModConfig;
import com.rawr.automation.modules.AutoMine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class OreSelectorGuiScreen extends GuiScreen {

    private static final int BACK_ID = 999;

    private final AutomationGuiScreen parent;
    private final AutoMine autoMine;
    private final ModConfig config;
    private final Map<Integer, String> buttonTargets = new LinkedHashMap<Integer, String>();

    public OreSelectorGuiScreen(AutomationGuiScreen parent, AutoMine autoMine, ModConfig config) {
        this.parent = parent;
        this.autoMine = autoMine;
        this.config = config;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonTargets.clear();

        int x = this.width / 2 - 90;
        int y = this.height / 2 - 70;

        String[] targets = new String[]{"diamond_ore", "emerald_ore", "gold_ore", "iron_ore", "coal_ore", "redstone_ore", "lapis_ore", "obsidian"};
        for (int i = 0; i < targets.length; i++) {
            int id = i;
            buttonTargets.put(id, targets[i]);
            buttonList.add(new GuiButton(id, x, y + (i * 22), 180, 20, prettyName(targets[i])));
        }

        buttonList.add(new GuiButton(BACK_ID, x, y + (targets.length * 22) + 8, 180, 20, EnumChatFormatting.GRAY + "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BACK_ID) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }

        String target = buttonTargets.get(button.id);
        if (target != null) {
            autoMine.setTargetBlock(target);
            config.setString("AutoMine.TargetBlock", target);
            config.save();
            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = this.width / 2 - 110;
        int top = this.height / 2 - 86;
        drawRect(left, top, left + 220, top + 240, 0xC0101010);
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GOLD + "AutoMine Block Menu", this.width / 2, top + 8, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, "Select ore target", this.width / 2, top + 20, 0xB0B0B0);

        int iconX = this.width / 2 - 102;
        int iconY = this.height / 2 - 64;
        for (String target : buttonTargets.values()) {
            drawOreIcon(target, iconX, iconY + 3);
            iconY += 22;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawOreIcon(String target, int x, int y) {
        ItemStack stack = null;
        if ("diamond_ore".equals(target)) stack = new ItemStack(Blocks.diamond_ore);
        else if ("emerald_ore".equals(target)) stack = new ItemStack(Blocks.emerald_ore);
        else if ("gold_ore".equals(target)) stack = new ItemStack(Blocks.gold_ore);
        else if ("iron_ore".equals(target)) stack = new ItemStack(Blocks.iron_ore);
        else if ("coal_ore".equals(target)) stack = new ItemStack(Blocks.coal_ore);
        else if ("redstone_ore".equals(target)) stack = new ItemStack(Blocks.redstone_ore);
        else if ("lapis_ore".equals(target)) stack = new ItemStack(Blocks.lapis_ore);
        else if ("obsidian".equals(target)) stack = new ItemStack(Blocks.obsidian);

        if (stack != null) {
            this.itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        }
    }

    private String prettyName(String target) {
        return target.replace('_', ' ');
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
