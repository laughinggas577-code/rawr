package com.rawr.automation.gui;

import com.rawr.automation.modules.Module;
import com.rawr.automation.modules.ModuleManager;
import com.rawr.automation.modules.PathWalker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Map;

/**
 * HUD overlay that displays active automation modules on screen.
 * Shows in the top-right corner with a compact list of enabled modules.
 */
public class HudRenderer {

    private final ModuleManager moduleManager;

    public HudRenderer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getDebugOverlay().showDebugScreen()) return;

        boolean anyActive = false;
        for (Module module : moduleManager.getModules().values()) {
            if (module.isEnabled()) {
                anyActive = true;
                break;
            }
        }
        if (!anyActive) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        int x = mc.getWindow().getGuiScaledWidth() - 4;
        int y = 4;
        int lineHeight = font.lineHeight + 2;

        // Title (purple themed)
        String title = "\u00a7dRawr Auto";
        int titleWidth = font.width(title);
        guiGraphics.drawString(font, title, x - titleWidth, y, 0xFFFFFF);
        y += lineHeight + 2;

        // Draw separator line (purple)
        String sep = "\u00a75---------";
        int sepWidth = font.width(sep);
        guiGraphics.drawString(font, sep, x - sepWidth, y, 0xFFFFFF);
        y += lineHeight;

        // List active modules
        for (Map.Entry<String, Module> entry : moduleManager.getModules().entrySet()) {
            Module module = entry.getValue();
            if (!module.isEnabled()) continue;

            String label = "\u00a7d\u25B6 \u00a7f" + module.getName();

            if (module instanceof PathWalker) {
                PathWalker walker = (PathWalker) module;
                BlockPos target = walker.getTarget();
                if (target != null) {
                    label += " \u00a77-> \u00a7d" + target.getX()
                            + ", " + target.getY()
                            + ", " + target.getZ();
                }

                int labelWidth = font.width(label);
                guiGraphics.drawString(font, label, x - labelWidth, y, 0xFFFFFF);
                y += lineHeight;

                if (target != null) {
                    LocalPlayer player = mc.player;
                    double ddx = target.getX() + 0.5 - player.getX();
                    double ddz = target.getZ() + 0.5 - player.getZ();
                    double dist = Math.sqrt(ddx * ddx + ddz * ddz);

                    String distStr = String.format("\u00a77  Dist: \u00a7f%.1f blocks", dist);
                    int dw = font.width(distStr);
                    guiGraphics.drawString(font, distStr, x - dw, y, 0xFFFFFF);
                    y += lineHeight;

                    String actionStr = "\u00a77  Action: \u00a7d" + walker.getCurrentAction();
                    int aw = font.width(actionStr);
                    guiGraphics.drawString(font, actionStr, x - aw, y, 0xFFFFFF);
                    y += lineHeight;

                    int wpCount = walker.getPlannedPath().size();
                    int wpIdx = walker.getPathIndex();
                    String wpStr = "\u00a77  Waypoints: \u00a7f" + wpIdx + "/" + wpCount;
                    if (!walker.isPathComplete()) {
                        wpStr += " \u00a7e(partial)";
                    }
                    int ww = font.width(wpStr);
                    guiGraphics.drawString(font, wpStr, x - ww, y, 0xFFFFFF);
                    y += lineHeight;
                }
                continue;
            }

            int labelWidth = font.width(label);
            guiGraphics.drawString(font, label, x - labelWidth, y, 0xFFFFFF);
            y += lineHeight;
        }
    }
}
