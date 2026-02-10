package com.rawr.automation.gui;

import com.rawr.automation.config.ModConfig;
import com.rawr.automation.modules.PathWalker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PathWalkerTargetGuiScreen extends GuiScreen {

    private static final int START_BUTTON_ID = 0;
    private static final int CANCEL_BUTTON_ID = 1;
    private static final int HISTORY_BASE_ID = 100;
    private static final int ROTATION_MINUS_ID = 300;
    private static final int ROTATION_PLUS_ID = 301;
    private static final int MAX_HISTORY = 8;
    private static final String HISTORY_KEY = "PathWalkerHistory";
    private static final String ROTATION_SCALE_KEY = "PathWalkerHeadRotationScale";

    private final AutomationGuiScreen parentScreen;
    private final PathWalker pathWalker;
    private final ModConfig config;

    private final List<CoordEntry> historyEntries = new ArrayList<CoordEntry>();

    private GuiTextField xField;
    private GuiTextField yField;
    private GuiTextField zField;
    private String errorMessage = "";
    private double rotationScale = 10.0;

    public PathWalkerTargetGuiScreen(AutomationGuiScreen parentScreen, PathWalker pathWalker, ModConfig config) {
        this.parentScreen = parentScreen;
        this.pathWalker = pathWalker;
        this.config = config;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        loadHistory();
        rotationScale = config.getInt(ROTATION_SCALE_KEY, 20) / 2.0;
        pathWalker.setHeadRotationScale(rotationScale);

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

        int historyX = centerX - 210;
        int historyY = startY - 10;
        for (int i = 0; i < historyEntries.size(); i++) {
            CoordEntry entry = historyEntries.get(i);
            this.buttonList.add(new GuiButton(HISTORY_BASE_ID + i, historyX, historyY + (i * 22), 100, 20, entry.asLabel()));
        }

        this.buttonList.add(new GuiButton(ROTATION_MINUS_ID, centerX + 110, startY, 20, 20, "-"));
        this.buttonList.add(new GuiButton(ROTATION_PLUS_ID, centerX + 190, startY, 20, 20, "+"));
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

            runPath(x, y, z);
            return;
        }

        if (button.id >= HISTORY_BASE_ID && button.id < HISTORY_BASE_ID + historyEntries.size()) {
            CoordEntry entry = historyEntries.get(button.id - HISTORY_BASE_ID);
            runPath(entry.x, entry.y, entry.z);
            return;
        }

        if (button.id == ROTATION_MINUS_ID) {
            setRotationScale(rotationScale - 0.5);
            return;
        }

        if (button.id == ROTATION_PLUS_ID) {
            setRotationScale(rotationScale + 0.5);
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
        drawString(this.fontRendererObj, EnumChatFormatting.LIGHT_PURPLE + "History", this.width / 2 - 210, this.height / 2 - 45, 0xFFFFFF);
        drawString(this.fontRendererObj, "Head Rot", this.width / 2 + 135, this.height / 2 - 45, 0xFFFFFF);
        drawString(this.fontRendererObj, EnumChatFormatting.AQUA + String.format("%.1f", rotationScale), this.width / 2 + 157, this.height / 2 - 27, 0xFFFFFF);

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

    private void runPath(int x, int y, int z) {
        pathWalker.setHeadRotationScale(rotationScale);
        pathWalker.setTarget(x, y, z);
        pathWalker.setEnabled(true);
        addHistory(x, y, z);
        config.setBoolean(pathWalker.getName(), true);
        config.setInt(ROTATION_SCALE_KEY, (int) Math.round(rotationScale * 2.0));
        config.save();
        Minecraft.getMinecraft().displayGuiScreen(parentScreen);
    }


    private void setRotationScale(double value) {
        if (value < 1.0) value = 1.0;
        if (value > 20.0) value = 20.0;
        rotationScale = Math.round(value * 2.0) / 2.0;
        pathWalker.setHeadRotationScale(rotationScale);
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

    private void loadHistory() {
        historyEntries.clear();
        String raw = config.getString(HISTORY_KEY, "");
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }

        String[] entries = raw.split(";");
        for (String entry : entries) {
            String[] parts = entry.split(",");
            if (parts.length != 3) continue;
            try {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());
                historyEntries.add(new CoordEntry(x, y, z));
                if (historyEntries.size() >= MAX_HISTORY) {
                    break;
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void addHistory(int x, int y, int z) {
        List<CoordEntry> newHistory = new ArrayList<CoordEntry>();
        newHistory.add(new CoordEntry(x, y, z));

        for (CoordEntry existing : historyEntries) {
            if (existing.x == x && existing.y == y && existing.z == z) {
                continue;
            }
            newHistory.add(existing);
            if (newHistory.size() >= MAX_HISTORY) {
                break;
            }
        }

        historyEntries.clear();
        historyEntries.addAll(newHistory);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < historyEntries.size(); i++) {
            if (i > 0) sb.append(';');
            CoordEntry e = historyEntries.get(i);
            sb.append(e.x).append(',').append(e.y).append(',').append(e.z);
        }
        config.setString(HISTORY_KEY, sb.toString());
    }

    private static class CoordEntry {
        private final int x;
        private final int y;
        private final int z;

        private CoordEntry(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private String asLabel() {
            return x + ", " + y + ", " + z;
        }
    }
}
