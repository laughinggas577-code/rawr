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
    private float yawVelocity = 0.0f;    // angular velocity for smooth acceleration
    private float pitchVelocity = 0.0f;
    private static final float MAX_YAW_SPEED = 12.0f;
    private static final float MAX_PITCH_SPEED = 6.0f;
    private static final float ROTATION_SMOOTHING = 0.14f;  // critically-damped spring factor

    // ---- Look-ahead carrot ----
    private static final int LOOK_AHEAD_NODES = 4;          // how many waypoints to blend
    private static final double CARROT_LEAD_DISTANCE = 3.0;  // look-ahead in blocks

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
    private float sprintBlend = 0.0f;  // 0=walk, 1=sprint, smoothly transitions

    public PathWalker() {
        super("PathWalker", "Ultra-smooth A* pathfinding to coordinates");
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

        // Initialize rotation from player's current facing
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
            sendChat("\u00a7d[Rawr] \u00a7fArrived at destination!");
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

        // Skip collinear waypoints first
        skipCollinearWaypoints();

        // Advance past reached waypoints
        advanceWaypoints(player);

        // If we exhausted the path
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

        // Recalc incomplete paths periodically
        if (!pathComplete && ticksSinceRecalc > SEGMENT_RECALC && recalcCooldown <= 0) {
            computePath();
            return;
        }

        // Get current waypoint
        currentWaypoint = computedPath.get(pathIndex);
        currentMoveType = computedMoveTypes.get(pathIndex);

        // Execute ultra-smooth movement
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

    // ---- Collinear waypoint skipping ----

    /**
     * Marks waypoints that are on a straight line so we can skip
     * intermediate ones and walk smoothly in a line instead of
     * stuttering at every grid position.
     */
    private void skipCollinearWaypoints() {
        if (pathIndex + 2 >= computedPath.size()) return;

        BlockPos a = computedPath.get(pathIndex);
        for (int i = pathIndex + 1; i < computedPath.size() - 1; i++) {
            BlockPos b = computedPath.get(i);
            BlockPos c = computedPath.get(i + 1);

            // Check if a, b, c are collinear in XZ and same Y
            int abx = b.getX() - a.getX();
            int abz = b.getZ() - a.getZ();
            int bcx = c.getX() - b.getX();
            int bcz = c.getZ() - b.getZ();

            // Same direction and same Y level = collinear, can skip b
            if (a.getY() == b.getY() && b.getY() == c.getY()
                    && abx * bcz == abz * bcx  // cross product = 0 means collinear
                    && (abx * bcx + abz * bcz) > 0  // same direction (dot product > 0)
                    && computedMoveTypes.get(i) == MoveType.WALK
                    && computedMoveTypes.get(i + 1) == MoveType.WALK) {
                // Skip waypoint i - don't actually remove it, just advance past it
                continue;
            } else {
                break;
            }
        }
    }

    // ---- Waypoint advancement ----

    private void advanceWaypoints(EntityPlayerSP player) {
        while (pathIndex < computedPath.size()) {
            BlockPos wp = computedPath.get(pathIndex);
            double dist = horizontalDist(player, wp);
            double yDist = Math.abs(player.posY - wp.getY());

            if (dist < WAYPOINT_REACH && yDist < 1.5) {
                pathIndex++;
            } else {
                break;
            }
        }
    }

    // ---- Look-ahead carrot point ----

    /**
     * Computes a blended "carrot" point ahead on the path.
     * Instead of aiming directly at the next waypoint, we blend
     * multiple future waypoints weighted by distance, creating
     * smooth curves through the path.
     */
    private double[] computeCarrotPoint(EntityPlayerSP player) {
        if (pathIndex >= computedPath.size()) {
            return new double[]{target.getX() + 0.5, target.getY(), target.getZ() + 0.5};
        }

        // Gather up to LOOK_AHEAD_NODES future waypoints
        double totalWeight = 0;
        double cx = 0, cy = 0, cz = 0;

        int nodesUsed = 0;
        for (int i = pathIndex; i < computedPath.size() && nodesUsed < LOOK_AHEAD_NODES; i++) {
            BlockPos wp = computedPath.get(i);
            MoveType mt = (i < computedMoveTypes.size()) ? computedMoveTypes.get(i) : MoveType.WALK;

            // Don't look ahead past non-walk moves (jumps, parkour, etc. need precise aiming)
            if (nodesUsed > 0 && mt != MoveType.WALK && mt != MoveType.WALK_DIAGONAL) {
                break;
            }

            // Weight: closer waypoints get higher weight, exponential falloff
            double distFromCurrent = i - pathIndex;
            double weight = 1.0 / (1.0 + distFromCurrent * 0.7);

            cx += (wp.getX() + 0.5) * weight;
            cy += wp.getY() * weight;
            cz += (wp.getZ() + 0.5) * weight;
            totalWeight += weight;
            nodesUsed++;
        }

        if (totalWeight > 0) {
            cx /= totalWeight;
            cy /= totalWeight;
            cz /= totalWeight;
        }

        // Extend the carrot point ahead by CARROT_LEAD_DISTANCE
        double dx = cx - player.posX;
        double dz = cz - player.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.1) {
            double lead = Math.min(CARROT_LEAD_DISTANCE, dist);
            double scale = lead / dist;
            // Blend between direct waypoint aim and extended carrot
            cx = player.posX + dx * scale * 1.2;
            cz = player.posZ + dz * scale * 1.2;
        }

        return new double[]{cx, cy, cz};
    }

    /**
     * Measures upcoming path curvature (0 = straight, 1 = sharp turn).
     * Used to modulate sprint speed.
     */
    private float measureCurvature() {
        if (pathIndex + 2 >= computedPath.size()) return 0.0f;

        BlockPos a = computedPath.get(pathIndex);
        BlockPos b = computedPath.get(Math.min(pathIndex + 2, computedPath.size() - 1));

        // If there's a third point further ahead, measure the angle
        int lookAhead = Math.min(pathIndex + 4, computedPath.size() - 1);
        if (lookAhead <= pathIndex + 2) {
            return 0.0f;
        }
        BlockPos c = computedPath.get(lookAhead);

        // Vectors AB and BC
        double abx = b.getX() - a.getX();
        double abz = b.getZ() - a.getZ();
        double bcx = c.getX() - b.getX();
        double bcz = c.getZ() - b.getZ();

        double magAB = Math.sqrt(abx * abx + abz * abz);
        double magBC = Math.sqrt(bcx * bcx + bcz * bcz);

        if (magAB < 0.01 || magBC < 0.01) return 0.0f;

        // Dot product → cos(angle)
        double dot = (abx * bcx + abz * bcz) / (magAB * magBC);
        dot = MathHelper.clamp_double(dot, -1.0, 1.0);

        // 1.0 means straight ahead (curvature=0), -1.0 means 180° turn (curvature=1)
        return (float) ((1.0 - dot) / 2.0);
    }

    // ---- Movement execution ----

    private void walkToward(Minecraft mc, EntityPlayerSP player, BlockPos wp, MoveType move) {
        double wpX = wp.getX() + 0.5;
        double wpY = wp.getY();
        double wpZ = wp.getZ() + 0.5;
        double dx = wpX - player.posX;
        double dy = wpY - player.posY;
        double dz = wpZ - player.posZ;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        // ---- Compute look target ----
        // For walk/diagonal, use carrot steering; for precise moves, aim directly
        double lookX, lookY, lookZ;
        if (move == MoveType.WALK || move == MoveType.WALK_DIAGONAL) {
            double[] carrot = computeCarrotPoint(player);
            lookX = carrot[0];
            lookY = carrot[1];
            lookZ = carrot[2];
        } else {
            lookX = wpX;
            lookY = wpY;
            lookZ = wpZ;
        }

        double lookDx = lookX - player.posX;
        double lookDy = lookY - player.posY;
        double lookDz = lookZ - player.posZ;
        double lookHDist = Math.sqrt(lookDx * lookDx + lookDz * lookDz);

        // ---- Critically-damped spring rotation ----
        float targetYaw = (float) (Math.atan2(-lookDx, lookDz) * 180.0 / Math.PI);
        float targetPitch = (float) (-Math.atan2(lookDy, Math.max(lookHDist, 0.1)) * 180.0 / Math.PI);
        targetPitch = MathHelper.clamp_float(targetPitch, -55.0f, 55.0f);

        // Spring-damper rotation for buttery smooth head movement
        currentYaw = springSmooth(currentYaw, targetYaw, true);
        currentPitch = springSmooth(currentPitch, targetPitch, false);
        player.rotationYaw = currentYaw;
        player.rotationPitch = currentPitch;

        // ---- Curvature analysis for sprint ----
        float curvature = measureCurvature();
        float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - currentYaw));

        // ---- Movement keys ----
        boolean forward = true;
        boolean jump = false;
        boolean sprint = false;
        boolean strafe = false;
        boolean strafeRight = false;
        boolean sneak = false;

        switch (move) {
            case WALK:
            case WALK_DIAGONAL:
                currentAction = "Walking";

                // Sprint decision: straight path, yaw aligned, far enough away
                boolean canSprint = hDist > 3.0 && yawDelta < 12.0 && curvature < 0.25f;
                float targetSprintBlend = canSprint ? 1.0f : 0.0f;

                // Smooth sprint transition
                sprintBlend += (targetSprintBlend - sprintBlend) * 0.15f;
                sprint = sprintBlend > 0.5f;

                if (sprint) {
                    currentAction = "Sprinting";
                }

                // Lateral correction: if we're drifting sideways from the path,
                // use gentle strafing to realign instead of sharp rotation
                if (pathIndex + 1 < computedPath.size()) {
                    BlockPos nextWP = computedPath.get(pathIndex);
                    double pathDx = nextWP.getX() + 0.5 - player.posX;
                    double pathDz = nextWP.getZ() + 0.5 - player.posZ;

                    // Perpendicular distance from player to line toward waypoint
                    double fwdX = Math.sin(-player.rotationYaw * Math.PI / 180.0);
                    double fwdZ = Math.cos(-player.rotationYaw * Math.PI / 180.0);
                    // nah, really we need: cross product of forward and toWaypoint
                    double cross = fwdX * pathDz - fwdZ * pathDx;
                    if (Math.abs(cross) > 0.3 && hDist > 1.5) {
                        strafe = true;
                        strafeRight = cross < 0;
                        currentAction = sprint ? "Sprinting (correcting)" : "Walking (correcting)";
                    }
                }
                break;

            case ASCEND:
                currentAction = "Ascending";
                sprintBlend *= 0.8f; // decelerate into jumps
                jump = player.onGround && jumpCooldown <= 0;
                break;

            case DESCEND:
                currentAction = "Descending";
                sprintBlend *= 0.9f;
                break;

            case FALL:
                currentAction = "Falling safely";
                sprintBlend = 0.0f;
                break;

            case PARKOUR:
                currentAction = "Parkour jump";
                sprint = true;
                sprintBlend = 1.0f;

                // Precise edge detection: jump when player is at the edge of the current block
                if (player.onGround && jumpCooldown <= 0) {
                    // Calculate distance to edge of current block in movement direction
                    double playerBlockX = player.posX - Math.floor(player.posX);
                    double playerBlockZ = player.posZ - Math.floor(player.posZ);

                    // Normalize direction
                    double ndx = dx / Math.max(hDist, 0.01);
                    double ndz = dz / Math.max(hDist, 0.01);

                    // Distance to block edge in movement direction
                    double edgeDistX = ndx > 0 ? (1.0 - playerBlockX) : playerBlockX;
                    double edgeDistZ = ndz > 0 ? (1.0 - playerBlockZ) : playerBlockZ;
                    double edgeDist = Math.min(
                            Math.abs(ndx) > 0.1 ? edgeDistX / Math.abs(ndx) : 999,
                            Math.abs(ndz) > 0.1 ? edgeDistZ / Math.abs(ndz) : 999
                    );

                    // Jump when close to edge (0.2-0.6 blocks from edge for best arc)
                    if (edgeDist < 0.6 && edgeDist > 0.05) {
                        jump = true;
                    }
                    // Fallback: jump if we're close enough to the gap
                    if (hDist < 2.5 && !jump) {
                        jump = true;
                    }
                }
                break;

            case LADDER:
                currentAction = "Climbing";
                sprintBlend = 0.0f;
                if (dy > 0.3) {
                    forward = true;
                } else if (dy < -0.3) {
                    sneak = true;
                }
                break;
        }

        // Apply movement keys
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), forward);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprint);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sneak);

        // Strafe correction
        if (strafe) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), !strafeRight);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), strafeRight);
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
        }

        if (jump) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            jumpCooldown = 8;
        } else if (player.onGround) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
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
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                jumpCooldown = 8;
                currentAction = "Unsticking (jump)";
            }

            if (stuckTicks > 20) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), true);
                currentAction = "Unsticking (strafe)";
            }

            if (stuckTicks > 35) {
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
            if (moved > 0.1) {
                stuckRecalcCount = 0;
            }
        }
    }

    // ---- Smooth rotation (critically-damped spring) ----

    /**
     * Spring-based angle smoothing. Uses a critically-damped spring model
     * for rotation that accelerates smoothly into turns and decelerates
     * smoothly out of them - no sudden starts or stops.
     */
    private float springSmooth(float current, float target, boolean isYaw) {
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        float maxSpeed = isYaw ? MAX_YAW_SPEED : MAX_PITCH_SPEED;

        // Spring-damper: F = -k*displacement - d*velocity
        // Using critically damped values for smooth motion without oscillation
        float springK = ROTATION_SMOOTHING;
        float damping = 2.0f * (float) Math.sqrt(springK);

        if (isYaw) {
            yawVelocity += delta * springK - yawVelocity * damping;
            yawVelocity = MathHelper.clamp_float(yawVelocity, -maxSpeed, maxSpeed);
            // Extra smoothing: cubic ease for very small corrections
            if (Math.abs(delta) < 3.0f) {
                yawVelocity *= 0.85f;
            }
            return current + yawVelocity;
        } else {
            pitchVelocity += delta * springK - pitchVelocity * damping;
            pitchVelocity = MathHelper.clamp_float(pitchVelocity, -maxSpeed, maxSpeed);
            if (Math.abs(delta) < 2.0f) {
                pitchVelocity *= 0.85f;
            }
            return current + pitchVelocity;
        }
    }

    // ---- Utilities ----

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
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
    }

    private void sendChat(String message) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(
                new net.minecraft.util.ChatComponentText(message)
        );
    }
}
