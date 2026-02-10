package com.rawr.automation.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

/**
 * PathWalker - Automatically walks the player toward a target coordinate.
 * Handles basic pathfinding: looks toward target, walks forward, and jumps over obstacles.
 */
public class PathWalker extends Module {

    private BlockPos target = null;
    private static final double ARRIVAL_DISTANCE = 2.0;
    private int stuckTicks = 0;
    private double lastX, lastZ;

    public PathWalker() {
        super("PathWalker", "Auto-walks to specified coordinates");
    }

    public void setTarget(int x, int y, int z) {
        this.target = new BlockPos(x, y, z);
        this.stuckTicks = 0;
    }

    public BlockPos getTarget() {
        return target;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        releaseMovementKeys(mc);
        target = null;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || target == null) return;

        double dx = target.getX() + 0.5 - player.posX;
        double dz = target.getZ() + 0.5 - player.posZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Check if arrived
        if (horizontalDist < ARRIVAL_DISTANCE) {
            sendChatMessage(player, "\u00a7a[Rawr] \u00a7fArrived at destination!");
            releaseMovementKeys(mc);
            setEnabled(false);
            return;
        }

        // Calculate yaw to face target
        float targetYaw = (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI);
        player.rotationYaw = smoothRotation(player.rotationYaw, targetYaw, 10.0f);

        // Look slightly down for walking
        double dy = target.getY() - player.posY;
        float targetPitch = (float) (-Math.atan2(dy, horizontalDist) * 180.0 / Math.PI);
        targetPitch = MathHelper.clamp_float(targetPitch, -45.0f, 45.0f);
        player.rotationPitch = smoothRotation(player.rotationPitch, targetPitch, 5.0f);

        // Walk forward
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);

        // Sprint if far away
        if (horizontalDist > 10.0) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }

        // Detect stuck and jump
        double moved = Math.sqrt(
                Math.pow(player.posX - lastX, 2) + Math.pow(player.posZ - lastZ, 2)
        );
        if (moved < 0.05 && player.onGround) {
            stuckTicks++;
            if (stuckTicks > 5) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            }
        } else {
            stuckTicks = 0;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

        lastX = player.posX;
        lastZ = player.posZ;
    }

    private float smoothRotation(float current, float target, float speed) {
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        if (delta > speed) delta = speed;
        if (delta < -speed) delta = -speed;
        return current + delta;
    }

    private void releaseMovementKeys(Minecraft mc) {
        if (mc.gameSettings == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    }

    private void sendChatMessage(EntityPlayerSP player, String message) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(
                new net.minecraft.util.ChatComponentText(message)
        );
    }
}
