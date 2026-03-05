package com.rawr.automation.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rawr.automation.modules.ModuleManager;
import com.rawr.automation.modules.PathWalker;
import com.rawr.automation.util.AStarPathfinder.MoveType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders the A* computed path in the world with an all-purple aesthetic.
 */
public class PathRenderer {

    private final ModuleManager moduleManager;

    // ---- Purple palette ----
    private static final float CUR_R = 0.95f, CUR_G = 0.15f, CUR_B = 1.0f;
    private static final float UP_R = 0.65f, UP_G = 0.25f, UP_B = 0.90f;
    private static final float SPEC_R = 0.80f, SPEC_G = 0.35f, SPEC_B = 1.0f;
    private static final float TRAV_R = 0.30f, TRAV_G = 0.10f, TRAV_B = 0.40f;
    private static final float LINE_R = 0.70f, LINE_G = 0.20f, LINE_B = 0.95f;
    private static final float TRACE_R = 0.75f, TRACE_G = 0.45f, TRACE_B = 1.0f;
    private static final float DEST_R = 0.90f, DEST_G = 0.10f, DEST_B = 0.85f;

    public PathRenderer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        PathWalker walker = (PathWalker) moduleManager.getModule("pathwalker");
        if (walker == null || !walker.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<BlockPos> path = walker.getPlannedPath();
        List<MoveType> moveTypes = walker.getMoveTypes();
        BlockPos target = walker.getTarget();
        int pathIndex = walker.getPathIndex();
        if (path.isEmpty() && target == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);

        Matrix4f matrix = poseStack.last().pose();

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
                drawBlockOutline(matrix, pos, CUR_R, CUR_G, CUR_B, pulse * 0.95f);
                drawBlockFill(matrix, pos, CUR_R, CUR_G, CUR_B, pulse * 0.22f);
            } else if (isTraversed) {
                drawBlockOutline(matrix, pos, TRAV_R, TRAV_G, TRAV_B, 0.25f);
            } else {
                float distFade = Math.max(0.12f, 1.0f - ((i - pathIndex) * 0.035f));
                boolean isSpecialMove = (move == MoveType.ASCEND || move == MoveType.FALL
                        || move == MoveType.PARKOUR || move == MoveType.LADDER
                        || move == MoveType.DESCEND);
                float r = isSpecialMove ? SPEC_R : UP_R;
                float g = isSpecialMove ? SPEC_G : UP_G;
                float b = isSpecialMove ? SPEC_B : UP_B;
                drawBlockOutline(matrix, pos, r, g, b, distFade * 0.75f);
                if (isSpecialMove) {
                    drawBlockFill(matrix, pos, r, g, b, distFade * 0.12f);
                }
            }
        }

        // ---- Draw connecting path line (purple gradient) ----
        if (path.size() >= 2 && pathIndex < path.size()) {
            drawPurplePathLine(matrix, path, pathIndex, fastPulse);
        }

        // ---- Draw tracer line from player to current waypoint ----
        if (pathIndex < path.size()) {
            BlockPos first = path.get(pathIndex);
            RenderSystem.lineWidth(2.5f);
            drawLine(matrix,
                    (float) mc.player.getX(), (float) (mc.player.getY() + 0.1), (float) mc.player.getZ(),
                    first.getX() + 0.5f, first.getY() + 0.1f, first.getZ() + 0.5f,
                    TRACE_R, TRACE_G, TRACE_B, 0.65f);
            RenderSystem.lineWidth(2.0f);
        }

        // ---- Destination marker (purple beacon) ----
        if (target != null) {
            drawBlockOutline(matrix, target, DEST_R, DEST_G, DEST_B, pulse * 0.95f);
            drawBlockFill(matrix, target, DEST_R, DEST_G, DEST_B, pulse * 0.25f);
            RenderSystem.lineWidth(4.0f);
            drawLine(matrix,
                    target.getX() + 0.5f, target.getY(), target.getZ() + 0.5f,
                    target.getX() + 0.5f, target.getY() + 10, target.getZ() + 0.5f,
                    DEST_R, DEST_G, DEST_B, pulse * 0.4f);
            RenderSystem.lineWidth(1.5f);
            drawLine(matrix,
                    target.getX() + 0.5f, target.getY(), target.getZ() + 0.5f,
                    target.getX() + 0.5f, target.getY() + 10, target.getZ() + 0.5f,
                    1.0f, 0.6f, 1.0f, pulse * 0.25f);
            RenderSystem.lineWidth(2.0f);
        }

        // Restore
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private void drawPurplePathLine(Matrix4f matrix, List<BlockPos> path, int startIdx, float pulse) {
        RenderSystem.lineWidth(3.0f);

        for (int i = Math.max(startIdx, 1); i < path.size(); i++) {
            BlockPos prev = path.get(i - 1);
            BlockPos curr = path.get(i);
            float fade = Math.max(0.15f, 1.0f - ((i - startIdx) * 0.02f));
            float brightnessShift = (float) (0.9 + 0.1 * Math.sin(i * 0.4 + pulse * 3.0));
            float r = LINE_R * brightnessShift;
            float g = LINE_G * brightnessShift;
            float b = LINE_B * brightnessShift;

            drawLine(matrix,
                    prev.getX() + 0.5f, prev.getY() + 0.1f, prev.getZ() + 0.5f,
                    curr.getX() + 0.5f, curr.getY() + 0.1f, curr.getZ() + 0.5f,
                    r, g, b, fade * 0.75f);
        }

        RenderSystem.lineWidth(2.0f);
    }

    private void drawBlockOutline(Matrix4f matrix, BlockPos pos, float r, float g, float b, float a) {
        int ri = (int)(r*255), gi = (int)(g*255), bi = (int)(b*255), ai = (int)(a*255);
        float x0 = pos.getX() + 0.002f, y0 = pos.getY() + 0.002f, z0 = pos.getZ() + 0.002f;
        float x1 = pos.getX() + 0.998f, y1 = pos.getY() + 0.998f, z1 = pos.getZ() + 0.998f;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        // Bottom
        buf.addVertex(matrix,x0,y0,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y0,z0).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y0,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y0,z1).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y0,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y0,z1).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x0,y0,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y0,z0).setColor(ri,gi,bi,ai);
        // Top
        buf.addVertex(matrix,x0,y1,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y1,z0).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y1,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y1,z1).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y1,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y1,z1).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x0,y1,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y1,z0).setColor(ri,gi,bi,ai);
        // Verticals
        buf.addVertex(matrix,x0,y0,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y1,z0).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y0,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y1,z0).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y0,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y1,z1).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x0,y0,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y1,z1).setColor(ri,gi,bi,ai);

        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private void drawBlockFill(Matrix4f matrix, BlockPos pos, float r, float g, float b, float a) {
        int ri = (int)(r*255), gi = (int)(g*255), bi = (int)(b*255), ai = (int)(a*255);
        float x0 = pos.getX() + 0.01f, y0 = pos.getY() + 0.01f, z0 = pos.getZ() + 0.01f;
        float x1 = pos.getX() + 0.99f, y1 = pos.getY() + 0.99f, z1 = pos.getZ() + 0.99f;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Bottom
        buf.addVertex(matrix,x0,y0,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y0,z0).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y0,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y0,z1).setColor(ri,gi,bi,ai);
        // Top
        buf.addVertex(matrix,x0,y1,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y1,z1).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y1,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y1,z0).setColor(ri,gi,bi,ai);
        // North
        buf.addVertex(matrix,x0,y0,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y1,z0).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y1,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y0,z0).setColor(ri,gi,bi,ai);
        // South
        buf.addVertex(matrix,x0,y0,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y0,z1).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y1,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y1,z1).setColor(ri,gi,bi,ai);
        // West
        buf.addVertex(matrix,x0,y0,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y0,z1).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x0,y1,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x0,y1,z0).setColor(ri,gi,bi,ai);
        // East
        buf.addVertex(matrix,x1,y0,z0).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y1,z0).setColor(ri,gi,bi,ai);
        buf.addVertex(matrix,x1,y1,z1).setColor(ri,gi,bi,ai); buf.addVertex(matrix,x1,y0,z1).setColor(ri,gi,bi,ai);

        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private void drawLine(Matrix4f matrix, float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float r, float g, float b, float a) {
        int ri = (int)(r*255), gi = (int)(g*255), bi = (int)(b*255), ai = (int)(a*255);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buf.addVertex(matrix, x1, y1, z1).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, x2, y2, z2).setColor(ri, gi, bi, ai);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}
