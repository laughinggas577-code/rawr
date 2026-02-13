package com.rawr.automation.gui;

import com.rawr.automation.modules.Module;
import com.rawr.automation.modules.ModuleManager;
import com.rawr.automation.modules.PathWalker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

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
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;

        // Check if any module is active
        boolean anyActive = false;
        for (Module module : moduleManager.getModules().values()) {
            if (module.isEnabled()) {
                anyActive = true;
                break;
            }
        }
        if (!anyActive) return;

        FontRenderer font = mc.fontRendererObj;
        ScaledResolution res = new ScaledResolution(mc);

        int x = res.getScaledWidth() - 4;
        int y = 4;
        int lineHeight = font.FONT_HEIGHT + 2;

        // Title
        String title = "\u00a76Rawr Auto";
        int titleWidth = font.getStringWidth(title);
        font.drawStringWithShadow(title, x - titleWidth, y, 0xFFFFFF);
        y += lineHeight + 2;

        // Draw separator line
        String sep = "\u00a78---------";
        int sepWidth = font.getStringWidth(sep);
        font.drawStringWithShadow(sep, x - sepWidth, y, 0xFFFFFF);
        y += lineHeight;

        // List active modules
        for (Map.Entry<String, Module> entry : moduleManager.getModules().entrySet()) {
            Module module = entry.getValue();
            if (!module.isEnabled()) continue;

            String label = "\u00a7a\u25B6 \u00a7f" + module.getName();

            // PathWalker gets expanded info with distance, action, waypoints
            if (module instanceof PathWalker) {
                PathWalker walker = (PathWalker) module;
                BlockPos target = walker.getTarget();
                if (target != null) {
                    label += " \u00a77-> \u00a7e" + target.getX()
                            + ", " + target.getY()
                            + ", " + target.getZ();
                }

                int labelWidth = font.getStringWidth(label);
                font.drawStringWithShadow(label, x - labelWidth, y, 0xFFFFFF);
                y += lineHeight;

                if (target != null) {
                    EntityPlayerSP player = mc.thePlayer;
                    double ddx = target.getX() + 0.5 - player.posX;
                    double ddz = target.getZ() + 0.5 - player.posZ;
                    double dist = Math.sqrt(ddx * ddx + ddz * ddz);

                    String distStr = String.format("\u00a77  Dist: \u00a7f%.1f blocks", dist);
                    int dw = font.getStringWidth(distStr);
                    font.drawStringWithShadow(distStr, x - dw, y, 0xFFFFFF);
                    y += lineHeight;

                    String actionStr = "\u00a77  Action: \u00a7b" + walker.getCurrentAction();
                    int aw = font.getStringWidth(actionStr);
                    font.drawStringWithShadow(actionStr, x - aw, y, 0xFFFFFF);
                    y += lineHeight;

                    int wpCount = walker.getPlannedPath().size();
                    int wpIdx = walker.getPathIndex();
                    String wpStr = "\u00a77  Waypoints: \u00a7f" + wpIdx + "/" + wpCount;
                    if (!walker.isPathComplete()) {
                        wpStr += " \u00a7e(partial)";
                    }
                    int ww = font.getStringWidth(wpStr);
                    font.drawStringWithShadow(wpStr, x - ww, y, 0xFFFFFF);
                    y += lineHeight;
                }
                continue;
            }

            int labelWidth = font.getStringWidth(label);
            font.drawStringWithShadow(label, x - labelWidth, y, 0xFFFFFF);
            y += lineHeight;
        }
    }
}
