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
 * Renders the A* computed path in the world.
 *
 * Color coding by movement type:
 *   GREEN  = walk / diagonal
 *   CYAN   = current waypoint (pulsing)
 *   YELLOW = ascend (jump up)
 *   BLUE   = descend / fall
 *   MAGENTA= parkour jump
 *   WHITE  = ladder / climb
 *   DARK   = already traversed
 *   ORANGE = destination beacon
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

        // ---- Draw path nodes ----
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            MoveType move = (i < moveTypes.size()) ? moveTypes.get(i) : MoveType.WALK;
            boolean isCurrent = (i == pathIndex);
            boolean isTraversed = (i < pathIndex);

            if (isCurrent) {
                // Current waypoint: bright cyan pulsing
                drawBlockOutline(pos, 0.0f, 1.0f, 1.0f, pulse * 0.95f);
                drawBlockFill(pos, 0.0f, 1.0f, 1.0f, pulse * 0.18f);
            } else if (isTraversed) {
                // Already passed: dim gray
                drawBlockOutline(pos, 0.4f, 0.4f, 0.4f, 0.2f);
            } else {
                // Upcoming: color by move type, fade with distance from current
                float distFade = Math.max(0.15f, 1.0f - ((i - pathIndex) * 0.03f));
                float[] color = getMoveColor(move);
                drawBlockOutline(pos, color[0], color[1], color[2], distFade * 0.7f);

                // Fill for ascend/descend/parkour to make them more visible
                if (move == MoveType.ASCEND || move == MoveType.FALL || move == MoveType.PARKOUR) {
                    drawBlockFill(pos, color[0], color[1], color[2], distFade * 0.1f);
                }
            }
        }

        // ---- Draw connecting path line ----
        if (path.size() >= 2 && pathIndex < path.size()) {
            drawPathLine(path, pathIndex, moveTypes);
        }

        // ---- Draw line from player to current waypoint ----
        if (pathIndex < path.size()) {
            BlockPos first = path.get(pathIndex);
            drawLine(
                    player.posX, player.posY + 0.1, player.posZ,
                    first.getX() + 0.5, first.getY() + 0.1, first.getZ() + 0.5,
                    0.0f, 0.8f, 1.0f, 0.6f
            );
        }

        // ---- Destination marker ----
        if (target != null) {
            drawBlockOutline(target, 1.0f, 0.3f, 0.1f, pulse * 0.95f);
            drawBlockFill(target, 1.0f, 0.4f, 0.1f, pulse * 0.2f);
            // Beacon beam
            GL11.glLineWidth(3.0f);
            drawLine(
                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                    target.getX() + 0.5, target.getY() + 8, target.getZ() + 0.5,
                    1.0f, 0.5f, 0.1f, pulse * 0.5f
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
     * Returns RGB color based on movement type.
     */
    private float[] getMoveColor(MoveType move) {
        switch (move) {
            case WALK:
            case WALK_DIAGONAL:
                return new float[]{0.3f, 1.0f, 0.3f};     // Green
            case ASCEND:
                return new float[]{1.0f, 1.0f, 0.2f};     // Yellow
            case DESCEND:
                return new float[]{0.3f, 0.6f, 1.0f};     // Blue
            case FALL:
                return new float[]{0.2f, 0.4f, 0.9f};     // Darker blue
            case PARKOUR:
                return new float[]{1.0f, 0.3f, 1.0f};     // Magenta
            case LADDER:
                return new float[]{0.9f, 0.9f, 0.9f};     // White
            default:
                return new float[]{0.5f, 0.5f, 0.5f};     // Gray
        }
    }

    private void drawPathLine(List<BlockPos> path, int startIdx, List<MoveType> moveTypes) {
        GL11.glLineWidth(2.5f);

        // Draw segments individually so each segment can have its own color
        for (int i = Math.max(startIdx, 1); i < path.size(); i++) {
            BlockPos prev = path.get(i - 1);
            BlockPos curr = path.get(i);
            MoveType move = (i < moveTypes.size()) ? moveTypes.get(i) : MoveType.WALK;
            float[] color = getMoveColor(move);
            float fade = Math.max(0.2f, 1.0f - ((i - startIdx) * 0.025f));

            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(prev.getX() + 0.5, prev.getY() + 0.1, prev.getZ() + 0.5)
              .color(color[0], color[1], color[2], fade * 0.7f).endVertex();
            wr.pos(curr.getX() + 0.5, curr.getY() + 0.1, curr.getZ() + 0.5)
              .color(color[0], color[1], color[2], fade * 0.7f).endVertex();
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
