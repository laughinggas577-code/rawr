package com.rawr.automation.gui;

import com.rawr.automation.modules.ModuleManager;
import com.rawr.automation.modules.PathWalker;
import com.rawr.automation.util.AStarPathfinder.MoveType;
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
 * Renders the A* computed path in the world with an all-purple aesthetic.
 *
 * Color scheme (all purple shades):
 *   BRIGHT MAGENTA  = current waypoint (pulsing glow)
 *   MEDIUM PURPLE   = upcoming walk nodes (fading with distance)
 *   LIGHT VIOLET    = upcoming ascend/descend/parkour (brighter for visibility)
 *   DARK PURPLE     = already traversed nodes
 *   HOT PINK-PURPLE = destination beacon
 *   PURPLE GRADIENT = connecting path line
 *   LAVENDER        = player-to-waypoint tracer
 */
public class PathRenderer {

    private final ModuleManager moduleManager;

    // ---- Purple palette ----
    // Current waypoint (hot magenta-purple, pulsing)
    private static final float CUR_R = 0.95f, CUR_G = 0.15f, CUR_B = 1.0f;
    // Upcoming walk nodes (medium purple)
    private static final float UP_R = 0.65f, UP_G = 0.25f, UP_B = 0.90f;
    // Upcoming special moves (lighter violet for visibility)
    private static final float SPEC_R = 0.80f, SPEC_G = 0.35f, SPEC_B = 1.0f;
    // Traversed nodes (dark muted purple)
    private static final float TRAV_R = 0.30f, TRAV_G = 0.10f, TRAV_B = 0.40f;
    // Path connecting line (rich purple)
    private static final float LINE_R = 0.70f, LINE_G = 0.20f, LINE_B = 0.95f;
    // Player-to-waypoint tracer (lavender)
    private static final float TRACE_R = 0.75f, TRACE_G = 0.45f, TRACE_B = 1.0f;
    // Destination beacon (hot pink-purple)
    private static final float DEST_R = 0.90f, DEST_G = 0.10f, DEST_B = 0.85f;

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
        List<MoveType> moveTypes = walker.getMoveTypes();
        BlockPos target = walker.getTarget();
        BlockPos currentWP = walker.getCurrentWaypoint();
        int pathIndex = walker.getPathIndex();
        if (path.isEmpty() && target == null) return;

        float partialTicks = event.partialTicks;
        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GL11.glLineWidth(2.0f);

        long time = System.currentTimeMillis();
        float pulse = (float) (0.6 + 0.4 * Math.sin(time * 0.006));
        float fastPulse = (float) (0.7 + 0.3 * Math.sin(time * 0.012));

        // ---- Draw path nodes ----
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            MoveType move = (i < moveTypes.size()) ? moveTypes.get(i) : MoveType.WALK;
            boolean isCurrent = (i == pathIndex);
            boolean isTraversed = (i < pathIndex);

            if (isCurrent) {
                // Current waypoint: bright magenta-purple pulsing glow
                drawBlockOutline(pos, CUR_R, CUR_G, CUR_B, pulse * 0.95f);
                drawBlockFill(pos, CUR_R, CUR_G, CUR_B, pulse * 0.22f);
            } else if (isTraversed) {
                // Already passed: dark muted purple
                drawBlockOutline(pos, TRAV_R, TRAV_G, TRAV_B, 0.25f);
            } else {
                // Upcoming: purple shades with distance fade
                float distFade = Math.max(0.12f, 1.0f - ((i - pathIndex) * 0.035f));

                boolean isSpecialMove = (move == MoveType.ASCEND || move == MoveType.FALL
                        || move == MoveType.PARKOUR || move == MoveType.LADDER
                        || move == MoveType.DESCEND);

                float r = isSpecialMove ? SPEC_R : UP_R;
                float g = isSpecialMove ? SPEC_G : UP_G;
                float b = isSpecialMove ? SPEC_B : UP_B;

                drawBlockOutline(pos, r, g, b, distFade * 0.75f);

                // Fill for special moves to make them more visible
                if (isSpecialMove) {
                    drawBlockFill(pos, r, g, b, distFade * 0.12f);
                }
            }
        }

        // ---- Draw connecting path line (purple gradient) ----
        if (path.size() >= 2 && pathIndex < path.size()) {
            drawPurplePathLine(path, pathIndex, fastPulse);
        }

        // ---- Draw tracer line from player to current waypoint ----
        if (pathIndex < path.size()) {
            BlockPos first = path.get(pathIndex);
            GL11.glLineWidth(2.5f);
            drawLine(
                    player.posX, player.posY + 0.1, player.posZ,
                    first.getX() + 0.5, first.getY() + 0.1, first.getZ() + 0.5,
                    TRACE_R, TRACE_G, TRACE_B, 0.65f
            );
            GL11.glLineWidth(2.0f);
        }

        // ---- Destination marker (purple beacon) ----
        if (target != null) {
            drawBlockOutline(target, DEST_R, DEST_G, DEST_B, pulse * 0.95f);
            drawBlockFill(target, DEST_R, DEST_G, DEST_B, pulse * 0.25f);
            // Beacon beam - dual layered for glow effect
            GL11.glLineWidth(4.0f);
            drawLine(
                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                    target.getX() + 0.5, target.getY() + 10, target.getZ() + 0.5,
                    DEST_R, DEST_G, DEST_B, pulse * 0.4f
            );
            GL11.glLineWidth(1.5f);
            drawLine(
                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                    target.getX() + 0.5, target.getY() + 10, target.getZ() + 0.5,
                    1.0f, 0.6f, 1.0f, pulse * 0.25f
            );
            GL11.glLineWidth(2.0f);
        }

        // Restore
        GL11.glLineWidth(1.0f);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /**
     * Draws connecting line segments in purple with a subtle
     * brightness gradient - brighter near the player, fading into the distance.
     */
    private void drawPurplePathLine(List<BlockPos> path, int startIdx, float pulse) {
        GL11.glLineWidth(3.0f);

        for (int i = Math.max(startIdx, 1); i < path.size(); i++) {
            BlockPos prev = path.get(i - 1);
            BlockPos curr = path.get(i);
            float fade = Math.max(0.15f, 1.0f - ((i - startIdx) * 0.02f));

            // Subtle purple brightness variation along the path
            float brightnessShift = (float) (0.9 + 0.1 * Math.sin(i * 0.4 + pulse * 3.0));
            float r = LINE_R * brightnessShift;
            float g = LINE_G * brightnessShift;
            float b = LINE_B * brightnessShift;

            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(prev.getX() + 0.5, prev.getY() + 0.1, prev.getZ() + 0.5)
              .color(r, g, b, fade * 0.75f).endVertex();
            wr.pos(curr.getX() + 0.5, curr.getY() + 0.1, curr.getZ() + 0.5)
              .color(r, g, b, fade * 0.75f).endVertex();
            tess.draw();
        }

        GL11.glLineWidth(2.0f);
    }

    private void drawBlockOutline(BlockPos pos, float r, float g, float b, float a) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        double x0 = pos.getX() + 0.002;
        double y0 = pos.getY() + 0.002;
        double z0 = pos.getZ() + 0.002;
        double x1 = pos.getX() + 0.998;
        double y1 = pos.getY() + 0.998;
        double z1 = pos.getZ() + 0.998;

        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        // Bottom
        wr.pos(x0,y0,z0).color(r,g,b,a).endVertex(); wr.pos(x1,y0,z0).color(r,g,b,a).endVertex();
        wr.pos(x1,y0,z0).color(r,g,b,a).endVertex(); wr.pos(x1,y0,z1).color(r,g,b,a).endVertex();
        wr.pos(x1,y0,z1).color(r,g,b,a).endVertex(); wr.pos(x0,y0,z1).color(r,g,b,a).endVertex();
        wr.pos(x0,y0,z1).color(r,g,b,a).endVertex(); wr.pos(x0,y0,z0).color(r,g,b,a).endVertex();
        // Top
        wr.pos(x0,y1,z0).color(r,g,b,a).endVertex(); wr.pos(x1,y1,z0).color(r,g,b,a).endVertex();
        wr.pos(x1,y1,z0).color(r,g,b,a).endVertex(); wr.pos(x1,y1,z1).color(r,g,b,a).endVertex();
        wr.pos(x1,y1,z1).color(r,g,b,a).endVertex(); wr.pos(x0,y1,z1).color(r,g,b,a).endVertex();
        wr.pos(x0,y1,z1).color(r,g,b,a).endVertex(); wr.pos(x0,y1,z0).color(r,g,b,a).endVertex();
        // Verticals
        wr.pos(x0,y0,z0).color(r,g,b,a).endVertex(); wr.pos(x0,y1,z0).color(r,g,b,a).endVertex();
        wr.pos(x1,y0,z0).color(r,g,b,a).endVertex(); wr.pos(x1,y1,z0).color(r,g,b,a).endVertex();
        wr.pos(x1,y0,z1).color(r,g,b,a).endVertex(); wr.pos(x1,y1,z1).color(r,g,b,a).endVertex();
        wr.pos(x0,y0,z1).color(r,g,b,a).endVertex(); wr.pos(x0,y1,z1).color(r,g,b,a).endVertex();

        tess.draw();
    }

    private void drawBlockFill(BlockPos pos, float r, float g, float b, float a) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        double x0 = pos.getX() + 0.01, y0 = pos.getY() + 0.01, z0 = pos.getZ() + 0.01;
        double x1 = pos.getX() + 0.99, y1 = pos.getY() + 0.99, z1 = pos.getZ() + 0.99;

        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        // Bottom
        wr.pos(x0,y0,z0).color(r,g,b,a).endVertex(); wr.pos(x1,y0,z0).color(r,g,b,a).endVertex();
        wr.pos(x1,y0,z1).color(r,g,b,a).endVertex(); wr.pos(x0,y0,z1).color(r,g,b,a).endVertex();
        // Top
        wr.pos(x0,y1,z0).color(r,g,b,a).endVertex(); wr.pos(x0,y1,z1).color(r,g,b,a).endVertex();
        wr.pos(x1,y1,z1).color(r,g,b,a).endVertex(); wr.pos(x1,y1,z0).color(r,g,b,a).endVertex();
        // North
        wr.pos(x0,y0,z0).color(r,g,b,a).endVertex(); wr.pos(x0,y1,z0).color(r,g,b,a).endVertex();
        wr.pos(x1,y1,z0).color(r,g,b,a).endVertex(); wr.pos(x1,y0,z0).color(r,g,b,a).endVertex();
        // South
        wr.pos(x0,y0,z1).color(r,g,b,a).endVertex(); wr.pos(x1,y0,z1).color(r,g,b,a).endVertex();
        wr.pos(x1,y1,z1).color(r,g,b,a).endVertex(); wr.pos(x0,y1,z1).color(r,g,b,a).endVertex();
        // West
        wr.pos(x0,y0,z0).color(r,g,b,a).endVertex(); wr.pos(x0,y0,z1).color(r,g,b,a).endVertex();
        wr.pos(x0,y1,z1).color(r,g,b,a).endVertex(); wr.pos(x0,y1,z0).color(r,g,b,a).endVertex();
        // East
        wr.pos(x1,y0,z0).color(r,g,b,a).endVertex(); wr.pos(x1,y1,z0).color(r,g,b,a).endVertex();
        wr.pos(x1,y1,z1).color(r,g,b,a).endVertex(); wr.pos(x1,y0,z1).color(r,g,b,a).endVertex();
        tess.draw();
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
