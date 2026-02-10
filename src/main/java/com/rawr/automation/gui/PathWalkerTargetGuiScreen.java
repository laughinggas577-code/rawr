package com.rawr.automation.gui;

import com.rawr.automation.config.ModConfig;
import com.rawr.automation.modules.PathWalker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;

public class PathWalkerTargetGuiScreen extends GuiScreen {

    private static final int START_BUTTON_ID = 0;
    private static final int CANCEL_BUTTON_ID = 1;

    private final AutomationGuiScreen parentScreen;
    private final PathWalker pathWalker;
    private final ModConfig config;

    private GuiTextField xField;
    private GuiTextField yField;
    private GuiTextField zField;
    private String errorMessage = "";

    public PathWalkerTargetGuiScreen(AutomationGuiScreen parentScreen, PathWalker pathWalker, ModConfig config) {
        this.parentScreen = parentScreen;
        this.pathWalker = pathWalker;
        this.config = config;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 35;

        xField = new GuiTextField(10, this.fontRendererObj, centerX - 100, startY, 60, 20);
        yField = new GuiTextField(11, this.fontRendererObj, centerX - 30, startY, 60, 20);
        zField = new GuiTextField(12, this.fontRendererObj, centerX + 40, startY, 60, 20);

        xField.setMaxStringLength(8);
        yField.setMaxStringLength(8);
        zField.setMaxStringLength(8);

        xField.setFocused(true);

        this.buttonList.add(new GuiButton(START_BUTTON_ID, centerX - 100, startY + 30, 98, 20, EnumChatFormatting.GREEN + "Start"));
        this.buttonList.add(new GuiButton(CANCEL_BUTTON_ID, centerX + 2, startY + 30, 98, 20, "Cancel"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == CANCEL_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(parentScreen);
            return;
        }

        if (button.id == START_BUTTON_ID) {
            Integer x = parseInt(xField.getText());
            Integer y = parseInt(yField.getText());
            Integer z = parseInt(zField.getText());

            if (x == null || y == null || z == null) {
                errorMessage = EnumChatFormatting.RED + "Please enter valid integer coordinates.";
                return;
            }

            pathWalker.setTarget(x, y, z);
            pathWalker.setEnabled(true);
            config.setBoolean(pathWalker.getName(), true);
            config.save();
            Minecraft.getMinecraft().displayGuiScreen(parentScreen);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (xField.textboxKeyTyped(typedChar, keyCode)
                || yField.textboxKeyTyped(typedChar, keyCode)
                || zField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }

        if (keyCode == 1) {
            Minecraft.getMinecraft().displayGuiScreen(parentScreen);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        xField.mouseClicked(mouseX, mouseY, mouseButton);
        yField.mouseClicked(mouseX, mouseY, mouseButton);
        zField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GOLD + "PathWalker Target", this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, "Enter X / Y / Z coordinates", this.width / 2, this.height / 2 - 48, 0xC0C0C0);

        drawString(this.fontRendererObj, "X", this.width / 2 - 108, this.height / 2 - 27, 0xFFFFFF);
        drawString(this.fontRendererObj, "Y", this.width / 2 - 38, this.height / 2 - 27, 0xFFFFFF);
        drawString(this.fontRendererObj, "Z", this.width / 2 + 32, this.height / 2 - 27, 0xFFFFFF);

        xField.drawTextBox();
        yField.drawTextBox();
        zField.drawTextBox();

        if (!errorMessage.isEmpty()) {
            drawCenteredString(this.fontRendererObj, errorMessage, this.width / 2, this.height / 2 + 58, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private Integer parseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
