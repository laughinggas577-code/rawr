package com.rawr.automation.modules;

import com.rawr.automation.pathing.BaritoneBridge;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;

import java.util.*;

/**
 * PathWalker - Walks the player toward a target with smooth and human-like movement.
 */
public class PathWalker extends Module {

    private static final int PATH_RADIUS = 16;
    private static final int MAX_PATH_STEPS = 128;
    private static final double ARRIVAL_DISTANCE = 1.8;

    private final Random random = new Random();
    private final List<BlockPos> plannedPath = new ArrayList<>();

    private BlockPos target;
    private BlockPos currentWaypoint;
    private String currentAction = "Idle";

    private float currentYaw = Float.NaN;
    private float currentPitch = Float.NaN;

    private int stuckTicks;
    private int totalStuckTicks;
    private double lastX;
    private double lastY;
    private double lastZ;

    private int jumpCooldown;
    private boolean wasJumping;

    private long nextPauseAt;
    private long pauseUntil;

    private int planningTicksRemaining;
    private int repathCooldown;
    private int digCooldown;
    private int pathVariantSeed;
    private int unstuckMode = 0;
    private int obstacleCommitTicks = 0;

    private double headRotationScale = 10.0;

    private final BaritoneBridge baritoneBridge = new BaritoneBridge();
    private boolean usingBaritone;

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
        long now = System.currentTimeMillis();
        this.nextPauseAt = now + 3000 + random.nextInt(2000);
        this.pauseUntil = 0;
        this.planningTicksRemaining = 0;
        this.repathCooldown = 0;
        this.digCooldown = 0;
        this.obstacleCommitTicks = 0;

        usingBaritone = baritoneBridge.startPath(x, y, z);
        if (usingBaritone) {
            currentAction = "Baritone routing";
        } else {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null && mc.theWorld != null) {
                planPath(mc, mc.thePlayer);
            }
        }
    }

    public BlockPos getTarget() { return target; }

    public List<BlockPos> getPlannedPath() { return plannedPath; }

    public BlockPos getCurrentWaypoint() { return currentWaypoint; }

    public String getCurrentAction() { return currentAction; }

    public boolean isMovementActive() {
        return isEnabled() && target != null && planningTicksRemaining <= 0;
    }

    public double getHeadRotationScale() {
        return headRotationScale;
    }

    public void setHeadRotationScale(double scale) {
        if (scale < 1.0) scale = 1.0;
        if (scale > 20.0) scale = 20.0;
        this.headRotationScale = Math.round(scale * 2.0) / 2.0;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        releaseMovementKeys(mc);
        if (usingBaritone) {
            baritoneBridge.cancel();
        }
        usingBaritone = false;
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

        if (usingBaritone) {
            if (!baritoneBridge.isAvailable()) {
                usingBaritone = false;
            } else {
                currentAction = baritoneBridge.isPathing() ? "Baritone pathing" : "Baritone arrived";
                if (!baritoneBridge.isPathing()) {
                    releaseMovementKeys(mc);
                    setEnabled(false);
                }
                return;
            }
        }

        if (Float.isNaN(currentYaw)) {
            currentYaw = player.rotationYaw;
            currentPitch = player.rotationPitch;
        }

        double dx = target.getX() + 0.5 - player.posX;
        double dz = target.getZ() + 0.5 - player.posZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDist < ARRIVAL_DISTANCE) {
            sendChat("\u00a7a[Rawr] \u00a7fArrived at destination!");
            releaseMovementKeys(mc);
            setEnabled(false);
            return;
        }

        if (repathCooldown > 0) {
            repathCooldown--;
        }

        if (plannedPath.isEmpty() || repathCooldown <= 0) {
            planPath(mc, player);
            repathCooldown = 6;
        }
        chooseWaypoint(player);

        BlockPos steering = currentWaypoint != null ? currentWaypoint : target;
        applyRotation(player, steering);

        ObstacleInfo obstacle = scanObstacleAhead(mc, player, steering);
        boolean blockedAhead = obstacle.hasBlock;
        boolean shouldJump = obstacle.shouldJump;

        if (blockedAhead) {
            if (obstacle.shouldDig) {
                tryDigForward(mc, player, obstacle.hit);
            }
            if (obstacleCommitTicks < 4) {
                obstacleCommitTicks++;
            }
        } else {
            obstacleCommitTicks = 0;
        }

        boolean microPause = shouldMicroPause() && !blockedAhead && stuckTicks < 4;
        if (microPause) {
            releaseMovementKeys(mc);
            currentAction = "Micro pause";
        } else {
            applyMovement(mc, player, horizontalDist, shouldJump, blockedAhead);
        }

        handleStuck(mc, player);

        lastX = player.posX;
        lastY = player.posY;
        lastZ = player.posZ;
    }

    private void chooseWaypoint(EntityPlayerSP player) {
        if (plannedPath.isEmpty()) {
            currentWaypoint = null;
            return;
        }

        while (!plannedPath.isEmpty()) {
            BlockPos wp = plannedPath.get(0);
            double wpDx = wp.getX() + 0.5 - player.posX;
            double wpDz = wp.getZ() + 0.5 - player.posZ;
            if (Math.sqrt(wpDx * wpDx + wpDz * wpDz) < 1.2) {
                plannedPath.remove(0);
            } else {
                break;
            }
        }
        currentWaypoint = plannedPath.isEmpty() ? null : plannedPath.get(0);
    }

    private void applyRotation(EntityPlayerSP player, BlockPos steering) {
        double sx = steering.getX() + 0.5 - player.posX;
        double sy = steering.getY() - player.posY;
        double sz = steering.getZ() + 0.5 - player.posZ;
        double dist = Math.sqrt(sx * sx + sz * sz);

        // tiny wobble to avoid perfectly straight aim
        float wobble = (float) ((random.nextDouble() - 0.5) * 1.6);

        float targetYaw = (float) (Math.atan2(-sx, sz) * 180.0 / Math.PI) + wobble;
        float targetPitch = (float) (-Math.atan2(sy, dist) * 180.0 / Math.PI);
        targetPitch = MathHelper.clamp_float(targetPitch, -45.0f, 45.0f);

        float yawStep = getScaledRotationStep();
        float pitchStep = Math.max(1.0f, yawStep * 0.6f);

        currentYaw = smoothAngleEaseInOut(currentYaw, targetYaw, yawStep);
        currentPitch = smoothAngleEaseInOut(currentPitch, targetPitch, pitchStep);
        player.rotationYaw = currentYaw;
        player.rotationPitch = currentPitch;
    }

    private void applyMovement(Minecraft mc, EntityPlayerSP player, double horizontalDist, boolean shouldJump, boolean blockedAhead) {
        float moveVariance = 0.90f + random.nextFloat() * 0.2f; // ±10%
        boolean shouldMoveForward = moveVariance > 0.93f;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), shouldMoveForward);

        boolean sprint = horizontalDist > 8.0 && !shouldJump && moveVariance > 1.0f;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprint);

        if (jumpCooldown > 0) jumpCooldown--;
        if (shouldJump && player.onGround && jumpCooldown <= 0) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            wasJumping = true;
            jumpCooldown = 6;
            currentAction = "Jumping obstacle";
        } else if (wasJumping && player.onGround) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            wasJumping = false;
            currentAction = blockedAhead ? "Adjusting route" : (sprint ? "Sprinting" : "Walking");
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            currentAction = blockedAhead ? "Avoiding obstacle" : (sprint ? "Sprinting" : "Walking");
        }
    }

    private boolean shouldMicroPause() {
        long now = System.currentTimeMillis();
        if (pauseUntil > now) {
            return true;
        }
        if (now >= nextPauseAt) {
            pauseUntil = now + 50 + random.nextInt(51);
            nextPauseAt = now + 3000 + random.nextInt(2000);
            return true;
        }
        return false;
    }

    private void handleStuck(Minecraft mc, EntityPlayerSP player) {
        double moved = Math.sqrt(Math.pow(player.posX - lastX, 2) + Math.pow(player.posY - lastY, 2) + Math.pow(player.posZ - lastZ, 2));

        if (moved < 0.03 && player.onGround) {
            stuckTicks++;
            totalStuckTicks++;
            performUnstuckRoutine(mc);

            if (totalStuckTicks > 220) {
                sendChat("§c[Rawr] §fPath blocked! Stopping.");
                releaseMovementKeys(mc);
                setEnabled(false);
            }
        } else {
            stuckTicks = 0;
            unstuckMode = 0;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }
    }

    private void performUnstuckRoutine(Minecraft mc) {
        if (stuckTicks > 4) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            currentAction = "Unsticking (jump)";
            repathCooldown = 0;

            if (mc.thePlayer != null) {
                MovingObjectPosition hit = getBlockHit(mc, mc.thePlayer, currentWaypoint != null ? currentWaypoint : target);
                if (hit != null) {
                    tryDigForward(mc, mc.thePlayer, hit);
                }
            }
        }

        if (stuckTicks > 14 && unstuckMode == 0) {
            unstuckMode = (totalStuckTicks / 14) % 3;
        }

        if (stuckTicks > 14) {
            if (unstuckMode == 0) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), true);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
                currentAction = "Unsticking (strafe left)";
            } else if (unstuckMode == 1) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), true);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
                currentAction = "Unsticking (strafe right)";
            } else {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), true);
                currentAction = "Unsticking (backstep)";
            }
        }

        if (stuckTicks > 30) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            stuckTicks = 0;
            repathCooldown = 0;
        }
    }

    private void planPath(Minecraft mc, EntityPlayerSP player) {
        plannedPath.clear();

        BlockPos start = findClosestWalkable(mc, new BlockPos(player.posX, player.posY, player.posZ));
        BlockPos goal = findClosestWalkable(mc, target);
        if (start == null || goal == null) return;

        List<BlockPos> best = Collections.emptyList();
        double bestCost = Double.MAX_VALUE;

        for (int variant = 0; variant < 3; variant++) {
            pathVariantSeed = variant;
            List<BlockPos> candidate = findPathAStar(mc, start, goal);
            if (candidate.isEmpty()) continue;
            double cost = estimatePathCost(candidate);
            if (cost < bestCost) {
                bestCost = cost;
                best = candidate;
            }
        }

        if (best.isEmpty()) return;

        List<BlockPos> smoothed = smoothPath(best);
        for (int i = 1; i < smoothed.size() && i < MAX_PATH_STEPS; i++) {
            plannedPath.add(smoothed.get(i));
        }
    }

    private List<BlockPos> findPathAStar(Minecraft mc, BlockPos start, BlockPos goal) {
        PriorityQueue<Node> open = new PriorityQueue<Node>();
        Map<BlockPos, Node> allNodes = new HashMap<BlockPos, Node>();
        Set<BlockPos> closed = new HashSet<BlockPos>();

        Node startNode = new Node(start, null, 0, heuristic(start, goal));
        open.add(startNode);
        allNodes.put(start, startNode);

        while (!open.isEmpty()) {
            Node current = open.poll();
            if (current.pos.equals(goal)) {
                return reconstruct(current);
            }
            if (closed.contains(current.pos)) continue;
            closed.add(current.pos);

            if (Math.abs(current.pos.getX() - start.getX()) > PATH_RADIUS || Math.abs(current.pos.getZ() - start.getZ()) > PATH_RADIUS) {
                continue;
            }

            List<BlockPos> neighbors = getNeighbors(mc, current.pos);
            if (pathVariantSeed > 0) {
                Collections.shuffle(neighbors, new Random((long) current.pos.hashCode() + pathVariantSeed * 31L));
            }

            for (BlockPos neighbor : neighbors) {
                if (closed.contains(neighbor)) continue;

                double moveCost = current.pos.distanceSq(neighbor) + Math.abs(neighbor.getY() - current.pos.getY()) * 1.5;
                if (isSolidBlock(mc, neighbor)) {
                    moveCost += 6.0;
                }
                double turnPenalty = 0.0;
                if (current.parent != null) {
                    int prevDx = current.pos.getX() - current.parent.pos.getX();
                    int prevDz = current.pos.getZ() - current.parent.pos.getZ();
                    int newDx = neighbor.getX() - current.pos.getX();
                    int newDz = neighbor.getZ() - current.pos.getZ();
                    if (prevDx != newDx || prevDz != newDz) {
                        turnPenalty = 0.35;
                    }
                }

                double tentativeG = current.g + moveCost + turnPenalty;
                Node node = allNodes.get(neighbor);
                if (node == null || tentativeG < node.g) {
                    Node next = new Node(neighbor, current, tentativeG, heuristic(neighbor, goal));
                    allNodes.put(neighbor, next);
                    open.add(next);
                }
            }
        }

        return Collections.emptyList();
    }


    private double estimatePathCost(List<BlockPos> path) {
        if (path.isEmpty()) return Double.MAX_VALUE;
        double cost = 0.0;
        for (int i = 1; i < path.size(); i++) {
            BlockPos a = path.get(i - 1);
            BlockPos b = path.get(i);
            cost += a.distanceSq(b);
            cost += Math.abs(a.getY() - b.getY()) * 1.2;
        }
        return cost;
    }


    private List<BlockPos> smoothPath(List<BlockPos> path) {
        if (path.size() < 3) return path;
        List<BlockPos> result = new ArrayList<BlockPos>();
        result.add(path.get(0));

        int anchor = 0;
        while (anchor < path.size() - 1) {
            int furthest = anchor + 1;
            for (int i = path.size() - 1; i > anchor + 1; i--) {
                if (hasLineOfWalk(path.get(anchor), path.get(i))) {
                    furthest = i;
                    break;
                }
            }
            result.add(path.get(furthest));
            anchor = furthest;
        }
        return result;
    }

    private boolean hasLineOfWalk(BlockPos from, BlockPos to) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 start = new Vec3(from.getX() + 0.5, from.getY() + 1.0, from.getZ() + 0.5);
        Vec3 end = new Vec3(to.getX() + 0.5, to.getY() + 1.0, to.getZ() + 0.5);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(start, end, false, true, false);
        return hit == null;
    }

    private BlockPos findDiggableStep(Minecraft mc, BlockPos check, int currentY) {
        BlockPos feet = new BlockPos(check.getX(), currentY, check.getZ());
        BlockPos head = feet.up();
        BlockPos ground = feet.down();
        if (!isSolidBlock(mc, ground)) return null;

        Block feetBlock = mc.theWorld.getBlockState(feet).getBlock();
        Block headBlock = mc.theWorld.getBlockState(head).getBlock();
        boolean feetDiggable = feetBlock != Blocks.air && feetBlock != Blocks.bedrock && feetBlock.getBlockHardness(mc.theWorld, feet) >= 0;
        boolean headFree = !isSolidBlock(mc, head) || (headBlock != Blocks.bedrock && headBlock.getBlockHardness(mc.theWorld, head) >= 0);

        if (feetDiggable && headFree) {
            return feet;
        }
        return null;
    }

    private List<BlockPos> reconstruct(Node end) {
        LinkedList<BlockPos> path = new LinkedList<BlockPos>();
        Node n = end;
        while (n != null) {
            path.addFirst(n.pos);
            n = n.parent;
        }
        return path;
    }

    private double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private List<BlockPos> getNeighbors(Minecraft mc, BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<BlockPos>(8);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                if (ox == 0 && oz == 0) continue;

                BlockPos check = new BlockPos(pos.getX() + ox, pos.getY(), pos.getZ() + oz);
                BlockPos walk = findClosestWalkable(mc, check);
                if (walk != null && Math.abs(walk.getY() - pos.getY()) <= 1) {
                    neighbors.add(walk);
                } else {
                    BlockPos tunnel = findDiggableStep(mc, check, pos.getY());
                    if (tunnel != null) {
                        neighbors.add(tunnel);
                    }
                }
            }
        }
        return neighbors;
    }

    private BlockPos findClosestWalkable(Minecraft mc, BlockPos origin) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos p = origin.add(0, dy, 0);
            if (isWalkable(mc, p.getX(), p.getY(), p.getZ())) {
                return p;
            }
        }
        return null;
    }


    private ObstacleInfo scanObstacleAhead(Minecraft mc, EntityPlayerSP player, BlockPos steering) {
        MovingObjectPosition direct = getBlockHit(mc, player, steering);
        if (direct == null || direct.getBlockPos() == null) {
            return new ObstacleInfo(null, false, false);
        }

        BlockPos hitPos = direct.getBlockPos();
        BlockPos top = hitPos.up();
        BlockPos twoAbove = top.up();
        BlockPos landing = hitPos.up();

        boolean climbable = isSolidBlock(mc, hitPos) && !isSolidBlock(mc, top) && !isSolidBlock(mc, twoAbove);
        boolean landingSafe = isSafeLanding(mc, landing);

        double dirX = -MathHelper.sin(player.rotationYaw * (float) Math.PI / 180.0f);
        double dirZ = MathHelper.cos(player.rotationYaw * (float) Math.PI / 180.0f);

        BlockPos front1 = new BlockPos(player.posX + dirX * 1.1, player.posY, player.posZ + dirZ * 1.1);
        BlockPos front2 = new BlockPos(player.posX + dirX * 2.1, player.posY, player.posZ + dirZ * 2.1);
        boolean front1Solid = isSolidBlock(mc, front1);
        boolean front2Solid = isSolidBlock(mc, front2);

        boolean canJumpNow = climbable && landingSafe;
        if (front1Solid && front2Solid && canJumpNow) {
            // stacked obstacle ahead; prefer dig to avoid getting clipped on second block
            canJumpNow = false;
        }

        Block block = mc.theWorld.getBlockState(hitPos).getBlock();
        float hardness = block.getBlockHardness(mc.theWorld, hitPos);
        boolean breakable = block != Blocks.bedrock && block != Blocks.obsidian && hardness >= 0;

        boolean shouldDig = !canJumpNow && breakable;
        if (!shouldDig && !canJumpNow && !breakable) {
            // unbreakable wall; force jump attempts if physically possible
            canJumpNow = climbable;
        }

        return new ObstacleInfo(direct, canJumpNow, shouldDig);
    }

    private boolean isSafeLanding(Minecraft mc, BlockPos landingFeet) {
        BlockPos below = landingFeet.down();
        BlockPos feet = landingFeet;
        BlockPos head = landingFeet.up();

        if (!isSolidBlock(mc, below)) {
            return false;
        }
        if (isSolidBlock(mc, feet) || isSolidBlock(mc, head)) {
            return false;
        }

        // avoid stepping onto dangerous blocks when deciding jump-vs-dig
        Block ground = mc.theWorld.getBlockState(below).getBlock();
        return ground != Blocks.lava && ground != Blocks.flowing_lava && ground != Blocks.fire && ground != Blocks.cactus;
    }

    private MovingObjectPosition getBlockHit(Minecraft mc, EntityPlayerSP player, BlockPos to) {
        Vec3 from = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 targetVec = new Vec3(to.getX() + 0.5, to.getY() + 0.8, to.getZ() + 0.5);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(from, targetVec, false, true, false);
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && !hit.getBlockPos().equals(to)) {
            return hit;
        }
        return null;
    }

    private void tryDigForward(Minecraft mc, EntityPlayerSP player, MovingObjectPosition blockHit) {
        if (blockHit == null || blockHit.getBlockPos() == null) return;
        if (digCooldown > 0) {
            digCooldown--;
            return;
        }

        BlockPos blockPos = blockHit.getBlockPos();
        IBlockState state = mc.theWorld.getBlockState(blockPos);
        Block block = state.getBlock();
        if (block == Blocks.air || block == Blocks.bedrock) return;

        float hardness = block.getBlockHardness(mc.theWorld, blockPos);
        if (hardness < 0) return;

        mc.playerController.onPlayerDamageBlock(blockPos, blockHit.sideHit == null ? EnumFacing.UP : blockHit.sideHit);
        player.swingItem();
        currentAction = "Digging obstacle";
        digCooldown = 2;
        repathCooldown = 0;
        obstacleCommitTicks = 4;
    }

    private boolean canStepUp(Minecraft mc, EntityPlayerSP player) {
        double dirX = -MathHelper.sin(player.rotationYaw * (float) Math.PI / 180.0f);
        double dirZ = MathHelper.cos(player.rotationYaw * (float) Math.PI / 180.0f);
        BlockPos aheadFeet = new BlockPos(player.posX + dirX, player.posY, player.posZ + dirZ);
        return isSolidBlock(mc, aheadFeet) && !isSolidBlock(mc, aheadFeet.up()) && !isSolidBlock(mc, aheadFeet.up(2));
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
        Material material = block.getMaterial();
        return material.isSolid() && material.blocksMovement();
    }

    private float getScaledRotationStep() {
        // 1.0 = snappy, 20.0 = very smooth
        double t = (headRotationScale - 1.0) / 19.0;
        return (float) (34.0 - (t * 30.0));
    }

    private float smoothAngleEaseInOut(float current, float target, float maxStep) {
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        float distance = Math.abs(delta);
        float t = MathHelper.clamp_float(distance / maxStep, 0.0f, 1.0f);
        // smootherstep ease-in-out
        float eased = t * t * t * (t * (t * 6 - 15) + 10);
        float allowed = Math.max(1.0f, eased * maxStep);
        if (delta > allowed) delta = allowed;
        if (delta < -allowed) delta = -allowed;
        return current + delta;
    }

    private void releaseMovementKeys(Minecraft mc) {
        if (mc.gameSettings == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
    }

    private void sendChat(String message) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(message));
    }

    private static class ObstacleInfo {
        private final MovingObjectPosition hit;
        private final boolean shouldJump;
        private final boolean shouldDig;
        private final boolean hasBlock;

        private ObstacleInfo(MovingObjectPosition hit, boolean shouldJump, boolean shouldDig) {
            this.hit = hit;
            this.shouldJump = shouldJump;
            this.shouldDig = shouldDig;
            this.hasBlock = hit != null;
        }
    }

    private static class Node implements Comparable<Node> {
        private final BlockPos pos;
        private final Node parent;
        private final double g;
        private final double f;

        private Node(BlockPos pos, Node parent, double g, double h) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = g + h;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.f, other.f);
        }
    }
}
