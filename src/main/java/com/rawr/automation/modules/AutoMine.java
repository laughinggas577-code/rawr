package com.rawr.automation.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * AutoMine - Automatically mines the block the player is looking at.
 */
public class AutoMine extends Module {

    private boolean wasBreaking = false;

    public AutoMine() {
        super("AutoMine", "Automatically mines blocks you look at");
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && wasBreaking) {
            MultiPlayerGameMode gameMode = mc.gameMode;
            if (gameMode != null) {
                gameMode.stopDestroyBlock();
            }
            wasBreaking = false;
        }
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) return;

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            if (wasBreaking) {
                mc.gameMode.stopDestroyBlock();
                wasBreaking = false;
            }
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();
        BlockState state = mc.level.getBlockState(pos);

        if (state.liquid()) {
            return;
        }

        if (mc.gameMode.continueDestroyBlock(pos, face)) {
            player.swing(InteractionHand.MAIN_HAND);
            wasBreaking = true;
        }

        if (!wasBreaking) {
            mc.gameMode.startDestroyBlock(pos, face);
            wasBreaking = true;
        }
    }
}
