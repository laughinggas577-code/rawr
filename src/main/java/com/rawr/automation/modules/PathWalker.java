package com.rawr.automation.modules;

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

    private double headRotationScale = 5.0;

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
        this.planningTicksRemaining = 12 + random.nextInt(9);
        this.repathCooldown = 0;
        this.digCooldown = 0;
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
        if (scale > 10.0) scale = 10.0;
        this.headRotationScale = Math.round(scale * 2.0) / 2.0;
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

        if (planningTicksRemaining > 0) {
            planningTicksRemaining--;
            currentAction = "Thinking...";
            releaseMovementKeys(mc);
            return;
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
            repathCooldown = 10;
        }
        chooseWaypoint(player);

        BlockPos steering = currentWaypoint != null ? currentWaypoint : target;
        applyRotation(player, steering);

        MovingObjectPosition blockHit = getBlockHit(mc, player, steering);
        boolean blockedAhead = blockHit != null;
        boolean shouldJump = blockedAhead && canStepUp(mc, player);
        if (blockedAhead && !shouldJump) {
            tryDigForward(mc, player, blockHit);
        }

        boolean microPause = shouldMicroPause();
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
            if (stuckTicks > 8) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                currentAction = "Unsticking (jump)";
            }
            if (stuckTicks > 20) {
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
            }
        } else {
            stuckTicks = 0;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
        }
    }

    private void planPath(Minecraft mc, EntityPlayerSP player) {
        plannedPath.clear();

        BlockPos start = findClosestWalkable(mc, new BlockPos(player.posX, player.posY, player.posZ));
        BlockPos goal = findClosestWalkable(mc, target);
        if (start == null || goal == null) return;

        List<BlockPos> path = findPathAStar(mc, start, goal);
        if (path.isEmpty()) return;

        for (int i = 1; i < path.size() && i < MAX_PATH_STEPS; i++) {
            plannedPath.add(path.get(i));
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

            for (BlockPos neighbor : getNeighbors(mc, current.pos)) {
                if (closed.contains(neighbor)) continue;

                double moveCost = current.pos.distanceSq(neighbor) + Math.abs(neighbor.getY() - current.pos.getY()) * 1.5;
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
        digCooldown = 4;
        repathCooldown = 0;
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
        // 1.0 = snappy, 10.0 = very smooth
        double t = (headRotationScale - 1.0) / 9.0;
        return (float) (32.0 - (t * 27.0));
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
    }

    private void sendChat(String message) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(message));
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
