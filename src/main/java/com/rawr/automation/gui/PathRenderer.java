package com.rawr.automation.gui;

import com.rawr.automation.modules.ModuleManager;
import com.rawr.automation.modules.PathWalker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Renders the PathWalker's planned path as 3D overlays in the world.
 * - Waypoint blocks are drawn as colored translucent outlines
 * - A connecting line links each waypoint
 * - The current waypoint pulses brighter
 * - The final destination gets a distinct beacon-style marker
 */
public class PathRenderer {

    private final ModuleManager moduleManager;

    public PathRenderer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        PathWalker walker = (PathWalker) moduleManager.getModule("pathwalker");
        if (walker == null || !walker.isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;

        List<BlockPos> path = walker.getPlannedPath();
        BlockPos target = walker.getTarget();
        BlockPos currentWP = walker.getCurrentWaypoint();
        if (path.isEmpty() && target == null) return;

        float partialTicks = event.partialTicks;

        // Camera offset (render relative to player eye position)
        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);

        // GL state for translucent drawing
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GL11.glLineWidth(2.5f);

        long time = System.currentTimeMillis();
        float pulse = (float) (0.6 + 0.4 * Math.sin(time * 0.005));

        // Draw path waypoint blocks
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            boolean isCurrent = pos.equals(currentWP);

            if (isCurrent) {
                drawBlockOutline(pos, 0.8f, 0.2f, 1.0f, pulse * 0.9f);
                drawBlockFill(pos, 0.8f, 0.2f, 1.0f, pulse * 0.15f);
            } else {
                float alpha = Math.max(0.25f, 1.0f - (i * 0.10f));
                drawBlockOutline(pos, 0.7f, 0.3f, 1.0f, alpha * 0.85f);
                drawBlockFill(pos, 0.65f, 0.2f, 1.0f, alpha * 0.18f);
            }
        }

        // Draw connecting line between waypoints
        if (path.size() >= 2) {
            drawPathLine(path, 0.7f, 0.3f, 1.0f, 0.8f);
        }

        // Draw line from player to first waypoint
        if (!path.isEmpty()) {
            BlockPos first = path.get(0);
            drawLine(
                    player.posX, player.posY + 0.1, player.posZ,
                    first.getX() + 0.5, first.getY() + 0.1, first.getZ() + 0.5,
                    0.7f, 0.3f, 1.0f, 0.5f
            );
        }


        // Always draw a direct guidance line to target immediately (even before path points are populated)
        if (target != null) {
            drawLine(
                    player.posX, player.posY + 0.1, player.posZ,
                    target.getX() + 0.5, target.getY() + 0.1, target.getZ() + 0.5,
                    0.8f, 0.2f, 1.0f, 0.35f
            );
        }

        // Draw target destination marker
        if (target != null) {
            drawBlockOutline(target, 1.0f, 0.3f, 0.1f, pulse * 0.9f);
            drawBlockFill(target, 1.0f, 0.4f, 0.1f, pulse * 0.2f);
            // Vertical beacon line above target
            drawLine(
                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                    target.getX() + 0.5, target.getY() + 6, target.getZ() + 0.5,
                    1.0f, 0.5f, 0.1f, pulse * 0.4f
            );
        }

        // Restore GL state
        GL11.glLineWidth(1.0f);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void drawBlockOutline(BlockPos pos, float r, float g, float b, float a) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        double x0 = pos.getX();
        double y0 = pos.getY();
        double z0 = pos.getZ();
        double x1 = x0 + 1;
        double y1 = y0 + 1;
        double z1 = z0 + 1;

        // Slight inset so lines don't z-fight with block faces
        double inset = 0.002;
        x0 += inset; y0 += inset; z0 += inset;
        x1 -= inset; y1 -= inset; z1 -= inset;

        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        // Bottom face
        wr.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z1).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y0, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y0, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y0, z0).color(r, g, b, a).endVertex();

        // Top face
        wr.pos(x0, y1, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y1, z0).color(r, g, b, a).endVertex();

        // Vertical edges
        wr.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        wr.pos(x0, y1, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z1).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y0, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y1, z1).color(r, g, b, a).endVertex();

        tess.draw();
    }

    private void drawBlockFill(BlockPos pos, float r, float g, float b, float a) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        double x0 = pos.getX() + 0.01;
        double y0 = pos.getY() + 0.01;
        double z0 = pos.getZ() + 0.01;
        double x1 = pos.getX() + 0.99;
        double y1 = pos.getY() + 0.99;
        double z1 = pos.getZ() + 0.99;

        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        // Bottom
        wr.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y0, z1).color(r, g, b, a).endVertex();

        // Top
        wr.pos(x0, y1, z0).color(r, g, b, a).endVertex();
        wr.pos(x0, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z0).color(r, g, b, a).endVertex();

        // North
        wr.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        wr.pos(x0, y1, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z0).color(r, g, b, a).endVertex();

        // South
        wr.pos(x0, y0, z1).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z1).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y1, z1).color(r, g, b, a).endVertex();

        // West
        wr.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        wr.pos(x0, y0, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x0, y1, z0).color(r, g, b, a).endVertex();

        // East
        wr.pos(x1, y0, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z0).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x1, y0, z1).color(r, g, b, a).endVertex();

        tess.draw();
    }

    private void drawPathLine(List<BlockPos> path, float r, float g, float b, float a) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GL11.glLineWidth(3.0f);

        wr.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (BlockPos pos : path) {
            wr.pos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5)
              .color(r, g, b, a).endVertex();
        }
        tess.draw();

        GL11.glLineWidth(2.5f);
    }

    private void drawLine(double x1, double y1, double z1,
                           double x2, double y2, double z2,
                           float r, float g, float b, float a) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        tess.draw();
    }
}
