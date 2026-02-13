package com.rawr.automation.modules;

import com.rawr.automation.util.AStarPathfinder;
import com.rawr.automation.util.AStarPathfinder.MoveType;
import com.rawr.automation.util.AStarPathfinder.PathResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PathWalker - A* based pathfinding module inspired by Baritone.
 *
 * Computes an A* path to the target, then executes movement along it
 * with smooth rotation, context-aware jumping, sprint management,
 * and automatic recalculation on failure.
 *
 * Exposes the full computed path + movement types for the renderer.
 */
public class PathWalker extends Module {

    private BlockPos target = null;

    // ---- A* path state ----
    private List<BlockPos> computedPath = new ArrayList<>();
    private List<MoveType> computedMoveTypes = new ArrayList<>();
    private int pathIndex = 0;
    private boolean pathComplete = false;

    // ---- Validation delay ----
    // After path computation, wait VALIDATION_DELAY ticks, then re-validate
    // before following. If invalid, recompute instead of walking into trouble.
    private static final int VALIDATION_DELAY = 40;   // 2 seconds
    private int validationTicksRemaining = 0;
    private boolean pathValidated = false;
    private int validationRetries = 0;
    private static final int MAX_VALIDATION_RETRIES = 3;

    // ---- Movement state ----
    private String currentAction = "Idle";
    private BlockPos currentWaypoint = null;
    private MoveType currentMoveType = MoveType.WALK;

    // ---- Smooth rotation ----
    private float currentYaw = Float.NaN;
    private float currentPitch = Float.NaN;
    private static final float YAW_SPEED = 8.0f;
    private static final float PITCH_SPEED = 4.0f;

    // ---- Timing ----
    private static final double WAYPOINT_REACH = 1.0;
    private static final double ARRIVAL_DISTANCE = 1.8;
    private int recalcCooldown = 0;
    private static final int RECALC_INTERVAL = 40;    // Min ticks between recalcs
    private static final int SEGMENT_RECALC = 200;    // Recalc for incomplete paths after N ticks
    private int ticksSinceRecalc = 0;

    // ---- Stuck detection ----
    private int stuckTicks = 0;
    private int totalStuckTicks = 0;
    private double lastX, lastY, lastZ;
    private int stuckRecalcCount = 0;
    private static final int MAX_STUCK_RECALCS = 5;

    // ---- Jump state ----
    private int jumpCooldown = 0;

    public PathWalker() {
        super("PathWalker", "A* pathfinding to coordinates");
    }

    public void setTarget(int x, int y, int z) {
        this.target = new BlockPos(x, y, z);
        resetState();
        computePath();
    }

    public BlockPos getTarget() {
        return target;
    }

    public List<BlockPos> getPlannedPath() {
        return computedPath;
    }

    public List<MoveType> getMoveTypes() {
        return computedMoveTypes;
    }

    public int getPathIndex() {
        return pathIndex;
    }

    public BlockPos getCurrentWaypoint() {
        return currentWaypoint;
    }

    public String getCurrentAction() {
        return currentAction;
    }

    public boolean isPathComplete() {
        return pathComplete;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        releaseAllKeys(mc);
        target = null;
        computedPath.clear();
        computedMoveTypes.clear();
        currentWaypoint = null;
        currentAction = "Idle";
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || target == null) return;

        // Initialize rotation
        if (Float.isNaN(currentYaw)) {
            currentYaw = player.rotationYaw;
            currentPitch = player.rotationPitch;
        }

        if (recalcCooldown > 0) recalcCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
        ticksSinceRecalc++;

        // Check arrival at final target
        double distToTarget = horizontalDist(player, target);
        if (distToTarget < ARRIVAL_DISTANCE) {
            sendChat("\u00a7a[Rawr] \u00a7fArrived at destination!");
            releaseAllKeys(mc);
            setEnabled(false);
            return;
        }

        // Need path?
        if (computedPath.isEmpty()) {
            if (recalcCooldown <= 0) {
                computePath();
            } else {
                currentAction = "Waiting to recompute...";
            }
            return;
        }

        // ---- Validation delay phase ----
        // After a path is computed, wait VALIDATION_DELAY ticks then re-check
        if (!pathValidated) {
            if (validationTicksRemaining > 0) {
                validationTicksRemaining--;
                float progress = 1.0f - ((float) validationTicksRemaining / VALIDATION_DELAY);
                int pct = (int) (progress * 100);
                currentAction = "Validating path... " + pct + "%";
                // Don't move during validation - just hold still
                releaseAllKeys(mc);
                return;
            }

            // Timer expired - validate the path now
            int invalidIdx = AStarPathfinder.validatePath(computedPath, computedMoveTypes);
            if (invalidIdx == -1) {
                // Path is valid, start following
                pathValidated = true;
                validationRetries = 0;
                sendChat("\u00a7a[Rawr] \u00a77Path validated, moving!");
            } else {
                // Path is blocked at node invalidIdx
                validationRetries++;
                if (validationRetries >= MAX_VALIDATION_RETRIES) {
                    sendChat("\u00a7c[Rawr] \u00a7fPath keeps getting blocked after "
                            + MAX_VALIDATION_RETRIES + " retries. Stopping.");
                    releaseAllKeys(mc);
                    setEnabled(false);
                    return;
                }
                sendChat("\u00a7e[Rawr] \u00a77Path blocked at node " + invalidIdx
                        + ", recalculating (" + validationRetries + "/" + MAX_VALIDATION_RETRIES + ")...");
                computePath(); // will reset timer and re-enter validation
                return;
            }
        }

        // ---- Normal movement phase ----

        // Advance past reached waypoints
        advanceWaypoints(player);

        // If we exhausted the path
        if (pathIndex >= computedPath.size()) {
            if (!pathComplete) {
                // Incomplete path exhausted, recalc for next segment
                if (recalcCooldown <= 0) {
                    computePath();
                    currentAction = "Extending path...";
                }
            } else {
                // Should be at target - let arrival check handle it
                currentAction = "Approaching target...";
                walkToward(mc, player, target, MoveType.WALK);
            }
            return;
        }

        // Recalc incomplete paths periodically
        if (!pathComplete && ticksSinceRecalc > SEGMENT_RECALC && recalcCooldown <= 0) {
            computePath();
            return;
        }

        // Get current waypoint
        currentWaypoint = computedPath.get(pathIndex);
        currentMoveType = computedMoveTypes.get(pathIndex);

        // Execute movement toward current waypoint
        walkToward(mc, player, currentWaypoint, currentMoveType);

        // Stuck detection
        detectStuck(mc, player);

        lastX = player.posX;
        lastY = player.posY;
        lastZ = player.posZ;
    }

    // ---- Path computation ----

    private void computePath() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || target == null) return;

        BlockPos start = new BlockPos(player.posX, player.posY, player.posZ);

        long t0 = System.currentTimeMillis();
        PathResult result = AStarPathfinder.findPath(start, target);
        long elapsed = System.currentTimeMillis() - t0;

        computedPath = new ArrayList<>(result.path);
        computedMoveTypes = new ArrayList<>(result.moveTypes);
        pathIndex = 0;
        pathComplete = result.complete;
        recalcCooldown = RECALC_INTERVAL;
        ticksSinceRecalc = 0;
        stuckTicks = 0;

        // Start validation delay - path will be re-checked after 40 ticks
        pathValidated = false;
        validationTicksRemaining = VALIDATION_DELAY;

        if (computedPath.isEmpty()) {
            sendChat("\u00a7c[Rawr] \u00a7fNo path found! (" + result.nodesExplored + " nodes explored)");
            releaseAllKeys(mc);
            setEnabled(false);
            return;
        }

        String status = pathComplete ? "\u00a7aComplete" : "\u00a7ePartial";
        currentAction = "Validating " + status.toLowerCase()
                + " \u00a77path (" + computedPath.size() + " nodes, "
                + elapsed + "ms, " + result.nodesExplored + " explored)";
    }

    private void resetState() {
        computedPath.clear();
        computedMoveTypes.clear();
        pathIndex = 0;
        pathComplete = false;
        pathValidated = false;
        validationTicksRemaining = 0;
        validationRetries = 0;
        stuckTicks = 0;
        totalStuckTicks = 0;
        stuckRecalcCount = 0;
        currentYaw = Float.NaN;
        currentPitch = Float.NaN;
        currentWaypoint = null;
        currentAction = "Computing path...";
        recalcCooldown = 0;
        ticksSinceRecalc = 0;
    }

    // ---- Waypoint advancement ----

    private void advanceWaypoints(EntityPlayerSP player) {
        while (pathIndex < computedPath.size()) {
            BlockPos wp = computedPath.get(pathIndex);
            double dist = horizontalDist(player, wp);
            double yDist = Math.abs(player.posY - wp.getY());

            // Reached this waypoint
            if (dist < WAYPOINT_REACH && yDist < 1.5) {
                pathIndex++;
            } else {
                break;
            }
        }
    }

    // ---- Movement execution ----

    private void walkToward(Minecraft mc, EntityPlayerSP player, BlockPos wp, MoveType move) {
        double dx = wp.getX() + 0.5 - player.posX;
        double dy = wp.getY() - player.posY;
        double dz = wp.getZ() + 0.5 - player.posZ;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        // ---- Smooth rotation ----
        float targetYaw = (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI);
        float targetPitch = (float) (-Math.atan2(dy, Math.max(hDist, 0.1)) * 180.0 / Math.PI);
        targetPitch = MathHelper.clamp_float(targetPitch, -60.0f, 60.0f);

        currentYaw = smoothAngle(currentYaw, targetYaw, YAW_SPEED);
        currentPitch = smoothAngle(currentPitch, targetPitch, PITCH_SPEED);
        player.rotationYaw = currentYaw;
        player.rotationPitch = currentPitch;

        // ---- Movement keys ----
        boolean forward = true;
        boolean jump = false;
        boolean sprint = false;

        switch (move) {
            case WALK:
            case WALK_DIAGONAL:
                currentAction = "Walking";
                // Sprint if more than 5 blocks and yaw is close to target
                float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - currentYaw));
                if (hDist > 5.0 && yawDelta < 15.0) {
                    sprint = true;
                    currentAction = "Sprinting";
                }
                break;

            case ASCEND:
                currentAction = "Ascending";
                jump = player.onGround && jumpCooldown <= 0;
                break;

            case DESCEND:
                currentAction = "Descending";
                break;

            case FALL:
                currentAction = "Falling safely";
                break;

            case PARKOUR:
                currentAction = "Parkour jump";
                sprint = true;
                // Jump at the edge - when close to the gap
                if (player.onGround && hDist < 2.5 && jumpCooldown <= 0) {
                    jump = true;
                }
                break;

            case LADDER:
                currentAction = "Climbing";
                // For ladders, look up/down based on direction
                if (dy > 0.3) {
                    forward = true; // walk into ladder to go up
                } else if (dy < -0.3) {
                    // Sneak while descending ladder
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
                }
                break;
        }

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), forward);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprint);

        if (jump) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            jumpCooldown = 8;
        } else if (player.onGround) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

        // Release sneak if not on ladder descent
        if (move != MoveType.LADDER || dy >= -0.3) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
    }

    // ---- Stuck detection ----

    private void detectStuck(Minecraft mc, EntityPlayerSP player) {
        double moved = Math.sqrt(
                Math.pow(player.posX - lastX, 2) +
                Math.pow(player.posY - lastY, 2) +
                Math.pow(player.posZ - lastZ, 2)
        );

        if (moved < 0.03 && player.onGround) {
            stuckTicks++;
            totalStuckTicks++;

            if (stuckTicks > 8 && jumpCooldown <= 0) {
                // Try jumping
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                jumpCooldown = 8;
                currentAction = "Unsticking (jump)";
            }

            if (stuckTicks > 20) {
                // Try strafing
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), true);
                currentAction = "Unsticking (strafe)";
            }

            if (stuckTicks > 35) {
                // Reset strafe and recalc path
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                stuckTicks = 0;
                stuckRecalcCount++;

                if (stuckRecalcCount >= MAX_STUCK_RECALCS) {
                    sendChat("\u00a7c[Rawr] \u00a7fPath blocked after " + MAX_STUCK_RECALCS + " retries. Stopping.");
                    releaseAllKeys(mc);
                    setEnabled(false);
                    return;
                }

                sendChat("\u00a7e[Rawr] \u00a77Recalculating path (stuck)...");
                computePath();
            }
        } else {
            if (stuckTicks > 0) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            }
            stuckTicks = 0;
            // Reset recalc count if making progress
            if (moved > 0.1) {
                stuckRecalcCount = 0;
            }
        }
    }

    // ---- Utilities ----

    private float smoothAngle(float current, float target, float maxStep) {
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        // Ease-out interpolation
        float dynamicSpeed = Math.max(maxStep * 0.25f, Math.abs(delta) * 0.18f);
        dynamicSpeed = Math.min(dynamicSpeed, maxStep);
        if (delta > dynamicSpeed) delta = dynamicSpeed;
        if (delta < -dynamicSpeed) delta = -dynamicSpeed;
        return current + delta;
    }

    private double horizontalDist(EntityPlayerSP player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.posX;
        double dz = pos.getZ() + 0.5 - player.posZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void releaseAllKeys(Minecraft mc) {
        if (mc.gameSettings == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
    }

    private void sendChat(String message) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(
                new net.minecraft.util.ChatComponentText(message)
        );
    }
}
