package com.rawr.automation.modules;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * PathWalker - Walks the player toward a target with smooth rotation,
 * block-level collision detection, predictive jumping, and gap/drop handling.
 * Exposes the planned path so a renderer can draw it in-world.
 */
public class PathWalker extends Module {

    private BlockPos target = null;

    // Path data exposed for rendering
    private final List<BlockPos> plannedPath = new ArrayList<>();
    private BlockPos currentWaypoint = null;
    private String currentAction = "Idle";

    // Smooth rotation state
    private float currentYaw = Float.NaN;
    private float currentPitch = Float.NaN;
    private static final float YAW_SPEED = 6.0f;     // degrees per tick (smooth)
    private static final float PITCH_SPEED = 3.0f;

    // Arrival
    private static final double ARRIVAL_DISTANCE = 1.8;

    // Stuck detection
    private int stuckTicks = 0;
    private int totalStuckTicks = 0;
    private double lastX, lastY, lastZ;

    // Jump prediction
    private int jumpCooldown = 0;
    private boolean wasJumping = false;

    // Scan range for path planning
    private static final int LOOKAHEAD = 8;

    public PathWalker() {
        super("PathWalker", "Auto-walks to specified coordinates");
    }

    public void setTarget(int x, int y, int z) {
        this.target = new BlockPos(x, y, z);
        this.stuckTicks = 0;
        this.totalStuckTicks = 0;
        this.currentYaw = Float.NaN;
        this.currentPitch = Float.NaN;
        this.plannedPath.clear();
        this.currentWaypoint = null;
        this.currentAction = "Starting";
    }

    public BlockPos getTarget() {
        return target;
    }

    public List<BlockPos> getPlannedPath() {
        return plannedPath;
    }

    public BlockPos getCurrentWaypoint() {
        return currentWaypoint;
    }

    public String getCurrentAction() {
        return currentAction;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        releaseMovementKeys(mc);
        target = null;
        plannedPath.clear();
        currentWaypoint = null;
        currentAction = "Idle";
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || target == null) return;

        // Initialize smooth rotation from player's current look
        if (Float.isNaN(currentYaw)) {
            currentYaw = player.rotationYaw;
            currentPitch = player.rotationPitch;
        }

        double dx = target.getX() + 0.5 - player.posX;
        double dz = target.getZ() + 0.5 - player.posZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Arrival check
        if (horizontalDist < ARRIVAL_DISTANCE) {
            sendChat("\u00a7a[Rawr] \u00a7fArrived at destination!");
            releaseMovementKeys(mc);
            setEnabled(false);
            return;
        }

        // Plan path (simple lookahead scan toward target)
        planPath(mc, player);

        // Pick next waypoint from path
        if (!plannedPath.isEmpty()) {
            currentWaypoint = plannedPath.get(0);
            // Remove waypoints we've already reached
            while (!plannedPath.isEmpty()) {
                BlockPos wp = plannedPath.get(0);
                double wpDx = wp.getX() + 0.5 - player.posX;
                double wpDz = wp.getZ() + 0.5 - player.posZ;
                double wpDist = Math.sqrt(wpDx * wpDx + wpDz * wpDz);
                if (wpDist < 1.2) {
                    plannedPath.remove(0);
                } else {
                    currentWaypoint = wp;
                    break;
                }
            }
        }

        // Determine steering target
        double steerX, steerY, steerZ;
        if (currentWaypoint != null) {
            steerX = currentWaypoint.getX() + 0.5;
            steerY = currentWaypoint.getY();
            steerZ = currentWaypoint.getZ() + 0.5;
        } else {
            steerX = target.getX() + 0.5;
            steerY = target.getY();
            steerZ = target.getZ() + 0.5;
        }

        double sdx = steerX - player.posX;
        double sdz = steerZ - player.posZ;
        double sdy = steerY - player.posY;
        double sDist = Math.sqrt(sdx * sdx + sdz * sdz);

        // ---- Smooth Rotation ----
        float targetYaw = (float) (Math.atan2(-sdx, sdz) * 180.0 / Math.PI);
        float targetPitch = (float) (-Math.atan2(sdy, sDist) * 180.0 / Math.PI);
        targetPitch = MathHelper.clamp_float(targetPitch, -50.0f, 50.0f);

        currentYaw = smoothAngle(currentYaw, targetYaw, YAW_SPEED);
        currentPitch = smoothAngle(currentPitch, targetPitch, PITCH_SPEED);
        player.rotationYaw = currentYaw;
        player.rotationPitch = currentPitch;

        // ---- Block Detection & Jump Prediction ----
        BlockPos playerFeet = new BlockPos(player.posX, player.posY, player.posZ);
        boolean shouldJump = false;
        String action = "Walking";

        if (jumpCooldown > 0) jumpCooldown--;

        // Look 1-2 blocks ahead in the movement direction
        double lookDirX = -MathHelper.sin(currentYaw * (float) Math.PI / 180.0f);
        double lookDirZ = MathHelper.cos(currentYaw * (float) Math.PI / 180.0f);

        // Check blocks ahead at feet level and head level
        for (int step = 1; step <= 2; step++) {
            BlockPos aheadFeet = new BlockPos(
                    player.posX + lookDirX * step,
                    player.posY,
                    player.posZ + lookDirZ * step
            );
            BlockPos aheadAbove = aheadFeet.up();
            BlockPos aheadBelow = aheadFeet.down();

            boolean feetSolid = isSolidBlock(mc, aheadFeet);
            boolean aboveSolid = isSolidBlock(mc, aheadAbove);
            boolean groundBelow = isSolidBlock(mc, aheadBelow);

            if (step == 1) {
                // Wall ahead at feet level -> need to jump
                if (feetSolid && !aboveSolid && !isSolidBlock(mc, aheadFeet.up(2))) {
                    shouldJump = true;
                    action = "Jumping over block";
                    break;
                }

                // Fence or 1.5-height block
                if (feetSolid) {
                    shouldJump = true;
                    action = "Jumping obstacle";
                    break;
                }

                // Gap detection: no ground below and no ground at feet -> gap
                if (!feetSolid && !groundBelow) {
                    // Check if there's a 2-deep drop
                    BlockPos twoDown = aheadFeet.down(2);
                    if (!isSolidBlock(mc, twoDown)) {
                        // Deep gap - jump over it
                        shouldJump = true;
                        action = "Jumping gap";
                    } else {
                        action = "Descending";
                    }
                }
            }

            if (step == 2 && !shouldJump) {
                // Predict upcoming wall 2 blocks ahead -> pre-jump
                if (feetSolid && !aboveSolid) {
                    shouldJump = true;
                    action = "Pre-jumping wall";
                }
            }
        }

        // Stair detection: block at feet+1 ahead with air at feet+2
        BlockPos stairCheck = new BlockPos(
                player.posX + lookDirX,
                player.posY + 1,
                player.posZ + lookDirZ
        );
        if (isSolidBlock(mc, stairCheck) && !isSolidBlock(mc, stairCheck.up())) {
            shouldJump = true;
            action = "Climbing stairs";
        }

        currentAction = action;

        // ---- Movement Keys ----
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);

        // Sprint when far
        boolean sprint = horizontalDist > 8.0 && !shouldJump;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprint);
        if (sprint) {
            currentAction = "Sprinting";
        }

        // ---- Jump Execution ----
        if (shouldJump && player.onGround && jumpCooldown <= 0) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            wasJumping = true;
            jumpCooldown = 6; // Prevent jump spam
        } else if (wasJumping && player.onGround) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            wasJumping = false;
        }

        // ---- Stuck Detection ----
        double moved = Math.sqrt(
                Math.pow(player.posX - lastX, 2) +
                Math.pow(player.posY - lastY, 2) +
                Math.pow(player.posZ - lastZ, 2)
        );

        if (moved < 0.03 && player.onGround) {
            stuckTicks++;
            totalStuckTicks++;
            if (stuckTicks > 8) {
                // Force jump when stuck
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                currentAction = "Unsticking (jump)";
            }
            if (stuckTicks > 20) {
                // Try strafing to get around obstacle
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), true);
                currentAction = "Unsticking (strafe)";
            }
            if (stuckTicks > 40) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                stuckTicks = 0;
            }
            if (totalStuckTicks > 200) {
                sendChat("\u00a7c[Rawr] \u00a7fPath blocked! Stopping.");
                releaseMovementKeys(mc);
                setEnabled(false);
                return;
            }
        } else {
            stuckTicks = 0;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
        }

        lastX = player.posX;
        lastY = player.posY;
        lastZ = player.posZ;
    }

    /**
     * Plans a simple path toward the target by scanning blocks in a line.
     * Produces waypoints that account for elevation changes.
     */
    private void planPath(Minecraft mc, EntityPlayerSP player) {
        plannedPath.clear();

        double dx = target.getX() + 0.5 - player.posX;
        double dz = target.getZ() + 0.5 - player.posZ;
        double totalDist = Math.sqrt(dx * dx + dz * dz);
        if (totalDist < 1.0) return;

        double stepX = dx / totalDist;
        double stepZ = dz / totalDist;

        int steps = Math.min(LOOKAHEAD, (int) Math.ceil(totalDist));
        double curX = player.posX;
        double curZ = player.posZ;
        int curY = (int) Math.floor(player.posY);

        for (int i = 1; i <= steps; i++) {
            curX += stepX;
            curZ += stepZ;

            BlockPos checkPos = new BlockPos(curX, curY, curZ);

            // Scan vertically to find walkable ground
            int bestY = findWalkableY(mc, checkPos, curY);
            if (bestY == Integer.MIN_VALUE) {
                // No walkable position found, stop planning
                break;
            }

            curY = bestY;
            plannedPath.add(new BlockPos(checkPos.getX(), curY, checkPos.getZ()));
        }
    }

    /**
     * Finds the best walkable Y level near a position, searching up and down from currentY.
     * A walkable position has solid ground below, air at feet and head level.
     */
    private int findWalkableY(Minecraft mc, BlockPos pos, int currentY) {
        // Search up to 3 blocks above and 4 blocks below
        for (int dy = 0; dy <= 3; dy++) {
            // Check above
            int upY = currentY + dy;
            if (isWalkable(mc, pos.getX(), upY, pos.getZ())) {
                return upY;
            }
            // Check below
            if (dy > 0) {
                int downY = currentY - dy;
                if (downY > 0 && isWalkable(mc, pos.getX(), downY, pos.getZ())) {
                    return downY;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean isWalkable(Minecraft mc, int x, int y, int z) {
        BlockPos ground = new BlockPos(x, y - 1, z);
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos head = new BlockPos(x, y + 1, z);

        return isSolidBlock(mc, ground) && !isSolidBlock(mc, feet) && !isSolidBlock(mc, head);
    }

    private boolean isSolidBlock(Minecraft mc, BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.air) return false;
        Material mat = block.getMaterial();
        return mat.isSolid() && mat.blocksMovement();
    }

    private float smoothAngle(float current, float target, float maxStep) {
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        // Ease-out: faster when far, slower when close
        float dynamicSpeed = Math.max(maxStep * 0.3f, Math.abs(delta) * 0.15f);
        dynamicSpeed = Math.min(dynamicSpeed, maxStep);
        if (delta > dynamicSpeed) delta = dynamicSpeed;
        if (delta < -dynamicSpeed) delta = -dynamicSpeed;
        return current + delta;
    }

    private void releaseMovementKeys(Minecraft mc) {
        if (mc.gameSettings == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
    }

    private void sendChat(String message) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(
                new net.minecraft.util.ChatComponentText(message)
        );
    }
}
