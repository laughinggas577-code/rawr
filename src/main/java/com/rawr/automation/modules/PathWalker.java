package com.rawr.automation.modules;

import com.rawr.automation.util.AStarPathfinder;
import com.rawr.automation.util.AStarPathfinder.MoveType;
import com.rawr.automation.util.AStarPathfinder.PathResult;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * PathWalker - Ultra-smooth A* pathfinding module.
 *
 * Smoothness features:
 *   - Look-ahead carrot steering: aims at a blended point several waypoints ahead
 *   - Cubic ease-in-out rotation interpolation for natural head movement
 *   - Curvature-aware sprint: only sprints on straight segments, decelerates for turns
 *   - Precise edge-of-block jump timing for optimal jump arcs
 *   - Collinear waypoint skipping to avoid unnecessary stops
 *   - Smooth velocity blending between movement states
 *   - Strafe correction for lateral alignment without rotation snapping
 */
public class PathWalker extends Module {

    private BlockPos target = null;

    // ---- A* path state ----
    private List<BlockPos> computedPath = new ArrayList<>();
    private List<MoveType> computedMoveTypes = new ArrayList<>();
    private int pathIndex = 0;
    private boolean pathComplete = false;

    // ---- Validation delay ----
    private static final int VALIDATION_DELAY = 40;
    private int validationTicksRemaining = 0;
    private boolean pathValidated = false;
    private int validationRetries = 0;
    private static final int MAX_VALIDATION_RETRIES = 3;

    // ---- Movement state ----
    private String currentAction = "Idle";
    private BlockPos currentWaypoint = null;
    private MoveType currentMoveType = MoveType.WALK;

    // ---- Smooth rotation state ----
    private float currentYaw = Float.NaN;
    private float currentPitch = Float.NaN;
    private float yawVelocity = 0.0f;
    private float pitchVelocity = 0.0f;
    private static final float MAX_YAW_SPEED = 12.0f;
    private static final float MAX_PITCH_SPEED = 6.0f;
    private static final float ROTATION_SMOOTHING = 0.14f;

    // ---- Look-ahead carrot ----
    private static final int LOOK_AHEAD_NODES = 4;
    private static final double CARROT_LEAD_DISTANCE = 3.0;

    // ---- Timing ----
    private static final double WAYPOINT_REACH = 0.8;
    private static final double ARRIVAL_DISTANCE = 1.8;
    private int recalcCooldown = 0;
    private static final int RECALC_INTERVAL = 40;
    private static final int SEGMENT_RECALC = 200;
    private int ticksSinceRecalc = 0;

    // ---- Stuck detection ----
    private int stuckTicks = 0;
    private int totalStuckTicks = 0;
    private double lastX, lastY, lastZ;
    private int stuckRecalcCount = 0;
    private static final int MAX_STUCK_RECALCS = 5;

    // ---- Jump state ----
    private int jumpCooldown = 0;

    // ---- Sprint smoothing ----
    private float sprintBlend = 0.0f;

    public PathWalker() {
        super("PathWalker", "Ultra-smooth A* pathfinding to coordinates");
    }

    public void setTarget(int x, int y, int z) {
        this.target = new BlockPos(x, y, z);
        resetState();
        computePath();
    }

    public BlockPos getTarget() { return target; }
    public List<BlockPos> getPlannedPath() { return computedPath; }
    public List<MoveType> getMoveTypes() { return computedMoveTypes; }
    public int getPathIndex() { return pathIndex; }
    public BlockPos getCurrentWaypoint() { return currentWaypoint; }
    public String getCurrentAction() { return currentAction; }
    public boolean isPathComplete() { return pathComplete; }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        releaseAllKeys(mc);
        target = null;
        computedPath.clear();
        computedMoveTypes.clear();
        currentWaypoint = null;
        currentAction = "Idle";
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || target == null) return;

        if (Float.isNaN(currentYaw)) {
            currentYaw = player.getYRot();
            currentPitch = player.getXRot();
        }

        if (recalcCooldown > 0) recalcCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
        ticksSinceRecalc++;

        double distToTarget = horizontalDist(player, target);
        if (distToTarget < ARRIVAL_DISTANCE) {
            sendChat("\u00a7d[Rawr] \u00a7fArrived at destination!");
            releaseAllKeys(mc);
            setEnabled(false);
            return;
        }

        if (computedPath.isEmpty()) {
            if (recalcCooldown <= 0) {
                computePath();
            } else {
                currentAction = "Waiting to recompute...";
            }
            return;
        }

        // ---- Validation delay phase ----
        if (!pathValidated) {
            if (validationTicksRemaining > 0) {
                validationTicksRemaining--;
                float progress = 1.0f - ((float) validationTicksRemaining / VALIDATION_DELAY);
                int pct = (int) (progress * 100);
                currentAction = "Validating path... " + pct + "%";
                releaseAllKeys(mc);
                return;
            }

            int invalidIdx = AStarPathfinder.validatePath(computedPath, computedMoveTypes);
            if (invalidIdx == -1) {
                pathValidated = true;
                validationRetries = 0;
                sendChat("\u00a7d[Rawr] \u00a77Path validated, moving!");
            } else {
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
                computePath();
                return;
            }
        }

        // ---- Normal movement phase ----
        skipCollinearWaypoints();
        advanceWaypoints(player);

        if (pathIndex >= computedPath.size()) {
            if (!pathComplete) {
                if (recalcCooldown <= 0) {
                    computePath();
                    currentAction = "Extending path...";
                }
            } else {
                currentAction = "Approaching target...";
                walkToward(mc, player, target, MoveType.WALK);
            }
            return;
        }

        if (!pathComplete && ticksSinceRecalc > SEGMENT_RECALC && recalcCooldown <= 0) {
            computePath();
            return;
        }

        currentWaypoint = computedPath.get(pathIndex);
        currentMoveType = computedMoveTypes.get(pathIndex);

        walkToward(mc, player, currentWaypoint, currentMoveType);
        detectStuck(mc, player);

        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
    }

    // ---- Path computation ----

    private void computePath() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || target == null) return;

        BlockPos start = player.blockPosition();

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
        yawVelocity = 0.0f;
        pitchVelocity = 0.0f;
        sprintBlend = 0.0f;
        currentWaypoint = null;
        currentAction = "Computing path...";
        recalcCooldown = 0;
        ticksSinceRecalc = 0;
    }

    private void skipCollinearWaypoints() {
        if (pathIndex + 2 >= computedPath.size()) return;
        BlockPos a = computedPath.get(pathIndex);
        for (int i = pathIndex + 1; i < computedPath.size() - 1; i++) {
            BlockPos b = computedPath.get(i);
            BlockPos c = computedPath.get(i + 1);
            int abx = b.getX() - a.getX(), abz = b.getZ() - a.getZ();
            int bcx = c.getX() - b.getX(), bcz = c.getZ() - b.getZ();
            if (a.getY() == b.getY() && b.getY() == c.getY()
                    && abx * bcz == abz * bcx
                    && (abx * bcx + abz * bcz) > 0
                    && computedMoveTypes.get(i) == MoveType.WALK
                    && computedMoveTypes.get(i + 1) == MoveType.WALK) {
                continue;
            } else {
                break;
            }
        }
    }

    private void advanceWaypoints(LocalPlayer player) {
        while (pathIndex < computedPath.size()) {
            BlockPos wp = computedPath.get(pathIndex);
            double dist = horizontalDist(player, wp);
            double yDist = Math.abs(player.getY() - wp.getY());
            if (dist < WAYPOINT_REACH && yDist < 1.5) {
                pathIndex++;
            } else {
                break;
            }
        }
    }

    private double[] computeCarrotPoint(LocalPlayer player) {
        if (pathIndex >= computedPath.size()) {
            return new double[]{target.getX() + 0.5, target.getY(), target.getZ() + 0.5};
        }
        double totalWeight = 0, cx = 0, cy = 0, cz = 0;
        int nodesUsed = 0;
        for (int i = pathIndex; i < computedPath.size() && nodesUsed < LOOK_AHEAD_NODES; i++) {
            BlockPos wp = computedPath.get(i);
            MoveType mt = (i < computedMoveTypes.size()) ? computedMoveTypes.get(i) : MoveType.WALK;
            if (nodesUsed > 0 && mt != MoveType.WALK && mt != MoveType.WALK_DIAGONAL) break;
            double weight = 1.0 / (1.0 + (i - pathIndex) * 0.7);
            cx += (wp.getX() + 0.5) * weight;
            cy += wp.getY() * weight;
            cz += (wp.getZ() + 0.5) * weight;
            totalWeight += weight;
            nodesUsed++;
        }
        if (totalWeight > 0) { cx /= totalWeight; cy /= totalWeight; cz /= totalWeight; }
        double dx = cx - player.getX(), dz = cz - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.1) {
            double scale = Math.min(CARROT_LEAD_DISTANCE, dist) / dist;
            cx = player.getX() + dx * scale * 1.2;
            cz = player.getZ() + dz * scale * 1.2;
        }
        return new double[]{cx, cy, cz};
    }

    private float measureCurvature() {
        if (pathIndex + 2 >= computedPath.size()) return 0.0f;
        BlockPos a = computedPath.get(pathIndex);
        BlockPos b = computedPath.get(Math.min(pathIndex + 2, computedPath.size() - 1));
        int lookAhead = Math.min(pathIndex + 4, computedPath.size() - 1);
        if (lookAhead <= pathIndex + 2) return 0.0f;
        BlockPos c = computedPath.get(lookAhead);
        double abx = b.getX() - a.getX(), abz = b.getZ() - a.getZ();
        double bcx = c.getX() - b.getX(), bcz = c.getZ() - b.getZ();
        double magAB = Math.sqrt(abx * abx + abz * abz);
        double magBC = Math.sqrt(bcx * bcx + bcz * bcz);
        if (magAB < 0.01 || magBC < 0.01) return 0.0f;
        double dot = Mth.clamp((abx * bcx + abz * bcz) / (magAB * magBC), -1.0, 1.0);
        return (float) ((1.0 - dot) / 2.0);
    }

    // ---- Movement execution ----

    private void walkToward(Minecraft mc, LocalPlayer player, BlockPos wp, MoveType move) {
        double wpX = wp.getX() + 0.5, wpY = wp.getY(), wpZ = wp.getZ() + 0.5;
        double dx = wpX - player.getX(), dy = wpY - player.getY(), dz = wpZ - player.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);

        double lookX, lookY, lookZ;
        if (move == MoveType.WALK || move == MoveType.WALK_DIAGONAL) {
            double[] carrot = computeCarrotPoint(player);
            lookX = carrot[0]; lookY = carrot[1]; lookZ = carrot[2];
        } else {
            lookX = wpX; lookY = wpY; lookZ = wpZ;
        }

        double lookDx = lookX - player.getX(), lookDy = lookY - player.getY(), lookDz = lookZ - player.getZ();
        double lookHDist = Math.sqrt(lookDx * lookDx + lookDz * lookDz);

        float targetYaw = (float) (Math.atan2(-lookDx, lookDz) * 180.0 / Math.PI);
        float targetPitch = Mth.clamp((float) (-Math.atan2(lookDy, Math.max(lookHDist, 0.1)) * 180.0 / Math.PI), -55.0f, 55.0f);

        currentYaw = springSmooth(currentYaw, targetYaw, true);
        currentPitch = springSmooth(currentPitch, targetPitch, false);
        player.setYRot(currentYaw);
        player.setXRot(currentPitch);

        float curvature = measureCurvature();
        float yawDelta = Math.abs(Mth.wrapDegrees(targetYaw - currentYaw));

        boolean forward = true, jump = false, sprint = false, strafe = false, strafeRight = false, sneak = false;

        switch (move) {
            case WALK: case WALK_DIAGONAL:
                currentAction = "Walking";
                boolean canSprint = hDist > 3.0 && yawDelta < 12.0 && curvature < 0.25f;
                sprintBlend += ((canSprint ? 1.0f : 0.0f) - sprintBlend) * 0.15f;
                sprint = sprintBlend > 0.5f;
                if (sprint) currentAction = "Sprinting";
                if (pathIndex + 1 < computedPath.size()) {
                    BlockPos nextWP = computedPath.get(pathIndex);
                    double pathDx = nextWP.getX() + 0.5 - player.getX();
                    double pathDz = nextWP.getZ() + 0.5 - player.getZ();
                    double fwdX = Math.sin(-player.getYRot() * Math.PI / 180.0);
                    double fwdZ = Math.cos(-player.getYRot() * Math.PI / 180.0);
                    double cross = fwdX * pathDz - fwdZ * pathDx;
                    if (Math.abs(cross) > 0.3 && hDist > 1.5) {
                        strafe = true; strafeRight = cross < 0;
                        currentAction = sprint ? "Sprinting (correcting)" : "Walking (correcting)";
                    }
                }
                break;
            case ASCEND:
                currentAction = "Ascending"; sprintBlend *= 0.8f;
                jump = player.onGround() && jumpCooldown <= 0; break;
            case DESCEND: currentAction = "Descending"; sprintBlend *= 0.9f; break;
            case FALL: currentAction = "Falling safely"; sprintBlend = 0.0f; break;
            case PARKOUR:
                currentAction = "Parkour jump"; sprint = true; sprintBlend = 1.0f;
                if (player.onGround() && jumpCooldown <= 0) {
                    double pbx = player.getX() - Math.floor(player.getX());
                    double pbz = player.getZ() - Math.floor(player.getZ());
                    double ndx = dx / Math.max(hDist, 0.01), ndz = dz / Math.max(hDist, 0.01);
                    double edx = ndx > 0 ? (1.0 - pbx) : pbx, edz = ndz > 0 ? (1.0 - pbz) : pbz;
                    double edgeDist = Math.min(
                            Math.abs(ndx) > 0.1 ? edx / Math.abs(ndx) : 999,
                            Math.abs(ndz) > 0.1 ? edz / Math.abs(ndz) : 999);
                    if (edgeDist < 0.6 && edgeDist > 0.05) jump = true;
                    if (hDist < 2.5 && !jump) jump = true;
                }
                break;
            case LADDER:
                currentAction = "Climbing"; sprintBlend = 0.0f;
                if (dy > 0.3) forward = true;
                else if (dy < -0.3) sneak = true;
                break;
        }

        KeyMapping.set(mc.options.keyUp.getKey(), forward);
        KeyMapping.set(mc.options.keySprint.getKey(), sprint);
        KeyMapping.set(mc.options.keyShift.getKey(), sneak);

        if (strafe) {
            KeyMapping.set(mc.options.keyLeft.getKey(), !strafeRight);
            KeyMapping.set(mc.options.keyRight.getKey(), strafeRight);
        } else {
            KeyMapping.set(mc.options.keyLeft.getKey(), false);
            KeyMapping.set(mc.options.keyRight.getKey(), false);
        }

        if (jump) {
            KeyMapping.set(mc.options.keyJump.getKey(), true);
            jumpCooldown = 8;
        } else if (player.onGround()) {
            KeyMapping.set(mc.options.keyJump.getKey(), false);
        }
    }

    private void detectStuck(Minecraft mc, LocalPlayer player) {
        double moved = Math.sqrt(Math.pow(player.getX() - lastX, 2) + Math.pow(player.getY() - lastY, 2) + Math.pow(player.getZ() - lastZ, 2));
        if (moved < 0.03 && player.onGround()) {
            stuckTicks++; totalStuckTicks++;
            if (stuckTicks > 8 && jumpCooldown <= 0) {
                KeyMapping.set(mc.options.keyJump.getKey(), true);
                jumpCooldown = 8; currentAction = "Unsticking (jump)";
            }
            if (stuckTicks > 20) { KeyMapping.set(mc.options.keyLeft.getKey(), true); currentAction = "Unsticking (strafe)"; }
            if (stuckTicks > 35) {
                KeyMapping.set(mc.options.keyLeft.getKey(), false);
                stuckTicks = 0; stuckRecalcCount++;
                if (stuckRecalcCount >= MAX_STUCK_RECALCS) {
                    sendChat("\u00a7c[Rawr] \u00a7fPath blocked after " + MAX_STUCK_RECALCS + " retries. Stopping.");
                    releaseAllKeys(mc); setEnabled(false); return;
                }
                sendChat("\u00a7e[Rawr] \u00a77Recalculating path (stuck)..."); computePath();
            }
        } else {
            if (stuckTicks > 0) KeyMapping.set(mc.options.keyLeft.getKey(), false);
            stuckTicks = 0;
            if (moved > 0.1) stuckRecalcCount = 0;
        }
    }

    private float springSmooth(float current, float target, boolean isYaw) {
        float delta = Mth.wrapDegrees(target - current);
        float maxSpeed = isYaw ? MAX_YAW_SPEED : MAX_PITCH_SPEED;
        float springK = ROTATION_SMOOTHING;
        float damping = 2.0f * (float) Math.sqrt(springK);
        if (isYaw) {
            yawVelocity += delta * springK - yawVelocity * damping;
            yawVelocity = Mth.clamp(yawVelocity, -maxSpeed, maxSpeed);
            if (Math.abs(delta) < 3.0f) yawVelocity *= 0.85f;
            return current + yawVelocity;
        } else {
            pitchVelocity += delta * springK - pitchVelocity * damping;
            pitchVelocity = Mth.clamp(pitchVelocity, -maxSpeed, maxSpeed);
            if (Math.abs(delta) < 2.0f) pitchVelocity *= 0.85f;
            return current + pitchVelocity;
        }
    }

    private double horizontalDist(LocalPlayer player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void releaseAllKeys(Minecraft mc) {
        if (mc.options == null) return;
        KeyMapping.set(mc.options.keyUp.getKey(), false);
        KeyMapping.set(mc.options.keySprint.getKey(), false);
        KeyMapping.set(mc.options.keyJump.getKey(), false);
        KeyMapping.set(mc.options.keyLeft.getKey(), false);
        KeyMapping.set(mc.options.keyRight.getKey(), false);
        KeyMapping.set(mc.options.keyShift.getKey(), false);
    }

    private void sendChat(String message) {
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal(message));
    }
}
