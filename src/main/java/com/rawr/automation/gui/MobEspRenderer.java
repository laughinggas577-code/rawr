package com.rawr.automation.gui;

import com.rawr.automation.modules.MobESP;
import com.rawr.automation.modules.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public class MobEspRenderer {

    private final ModuleManager moduleManager;

    public MobEspRenderer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        MobESP esp = (MobESP) moduleManager.getModule("mobesp");
        if (esp == null || !esp.isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null) return;

        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();

        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityLivingBase) || obj == player) continue;
            Entity e = (Entity) obj;
            if (player.getDistanceToEntity(e) > 64) continue;

            boolean hostile = e instanceof IMob;
            float r = hostile ? 1.0f : 0.2f;
            float g = hostile ? 0.2f : 1.0f;
            float b = 0.7f;
            drawBox(e, r, g, b, 0.7f);
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void drawBox(Entity e, float r, float g, float b, float a) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        double hw = e.width * 0.55;
        double x0 = e.posX - hw;
        double y0 = e.posY;
        double z0 = e.posZ - hw;
        double x1 = e.posX + hw;
        double y1 = e.posY + e.height + 0.15;
        double z1 = e.posZ + hw;

        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        line(wr, x0,y0,z0, x1,y0,z0, r,g,b,a);
        line(wr, x1,y0,z0, x1,y0,z1, r,g,b,a);
        line(wr, x1,y0,z1, x0,y0,z1, r,g,b,a);
        line(wr, x0,y0,z1, x0,y0,z0, r,g,b,a);

        line(wr, x0,y1,z0, x1,y1,z0, r,g,b,a);
        line(wr, x1,y1,z0, x1,y1,z1, r,g,b,a);
        line(wr, x1,y1,z1, x0,y1,z1, r,g,b,a);
        line(wr, x0,y1,z1, x0,y1,z0, r,g,b,a);

        line(wr, x0,y0,z0, x0,y1,z0, r,g,b,a);
        line(wr, x1,y0,z0, x1,y1,z0, r,g,b,a);
        line(wr, x1,y0,z1, x1,y1,z1, r,g,b,a);
        line(wr, x0,y0,z1, x0,y1,z1, r,g,b,a);
        tess.draw();
    }

    private void line(WorldRenderer wr, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x2, y2, z2).color(r, g, b, a).endVertex();
    }
}
