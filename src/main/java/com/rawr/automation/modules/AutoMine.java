package com.rawr.automation.modules;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;

/**
 * AutoMine - Automatically mines the block the player is looking at.
 * Continuously holds the attack button to break blocks without holding click.
 */
public class AutoMine extends Module {

    private boolean wasBreaking = false;

    public AutoMine() {
        super("AutoMine", "Automatically mines blocks you look at");
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && wasBreaking) {
            PlayerControllerMP controller = mc.playerController;
            if (controller != null) {
                controller.resetBlockRemoving();
            }
            wasBreaking = false;
        }
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.currentScreen != null) return;

        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            if (wasBreaking) {
                mc.playerController.resetBlockRemoving();
                wasBreaking = false;
            }
            return;
        }

        BlockPos pos = mop.getBlockPos();
        EnumFacing face = mop.sideHit;
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();

        if (block.getMaterial().isLiquid()) {
            return;
        }

        // Simulate holding left click to break
        if (mc.playerController.onPlayerDamageBlock(pos, face)) {
            mc.thePlayer.swingItem();
            wasBreaking = true;
        }

        // Start breaking if not already
        if (!wasBreaking) {
            mc.playerController.clickBlock(pos, face);
            wasBreaking = true;
        }
    }
}
