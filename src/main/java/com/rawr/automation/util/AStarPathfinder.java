package com.rawr.automation.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * A* pathfinder inspired by Baritone's approach, ported to 1.21.1.
 */
public class AStarPathfinder {

    private static final int[][] CARDINAL = {{1,0}, {-1,0}, {0,1}, {0,-1}};
    private static final int[][] DIAGONAL = {{1,1}, {1,-1}, {-1,1}, {-1,-1}};

    private static final double COST_WALK = 1.0, COST_DIAGONAL = 1.414, COST_ASCEND = 1.8;
    private static final double COST_DESCEND_1 = 1.0, COST_DESCEND_2 = 1.5, COST_DESCEND_3 = 2.0;
    private static final double COST_PARKOUR = 4.0, COST_LADDER = 1.5;
    private static final double PENALTY_WATER = 2.0, PENALTY_SOUL_SAND = 1.5;
    private static final int MAX_ITERATIONS = 10000, MAX_FALL_DISTANCE = 3, MAX_PATH_LENGTH = 500;

    public static class PathResult {
        public final List<BlockPos> path;
        public final List<MoveType> moveTypes;
        public final boolean complete;
        public final int nodesExplored;
        public PathResult(List<BlockPos> path, List<MoveType> moveTypes, boolean complete, int nodesExplored) {
            this.path = path; this.moveTypes = moveTypes; this.complete = complete; this.nodesExplored = nodesExplored;
        }
    }

    public enum MoveType { WALK, WALK_DIAGONAL, ASCEND, DESCEND, PARKOUR, LADDER, FALL, START }

    private static class Node implements Comparable<Node> {
        final BlockPos pos; final double g, f; final Node parent; final MoveType moveType;
        Node(BlockPos pos, double g, double f, Node parent, MoveType moveType) {
            this.pos = pos; this.g = g; this.f = f; this.parent = parent; this.moveType = moveType;
        }
        @Override public int compareTo(Node o) { return Double.compare(this.f, o.f); }
    }

    public static PathResult findPath(BlockPos start, BlockPos goal) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return new PathResult(Collections.emptyList(), Collections.emptyList(), false, 0);

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<Long, Double> bestG = new HashMap<>();
        Node startNode = new Node(start, 0, heuristic(start, goal), null, MoveType.START);
        openSet.add(startNode);
        bestG.put(posKey(start), 0.0);
        Node bestNode = startNode;
        double bestHeuristic = heuristic(start, goal);
        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            Node current = openSet.poll();
            int dx = current.pos.getX() - goal.getX();
            int dy = current.pos.getY() - goal.getY();
            int dz = current.pos.getZ() - goal.getZ();
            double goalDist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (goalDist < 1.5) return buildResult(current, true, iterations);

            double h = heuristic(current.pos, goal);
            if (h < bestHeuristic) { bestHeuristic = h; bestNode = current; }

            expandCardinal(level, current, goal, openSet, bestG);
            expandDiagonal(level, current, goal, openSet, bestG);
            expandAscend(level, current, goal, openSet, bestG);
            expandDescend(level, current, goal, openSet, bestG);
            expandLadder(level, current, goal, openSet, bestG);
            expandParkour(level, current, goal, openSet, bestG);
        }
        return buildResult(bestNode, false, iterations);
    }

    private static void expandCardinal(Level level, Node cur, BlockPos goal, PriorityQueue<Node> open, Map<Long, Double> bestG) {
        for (int[] d : CARDINAL) {
            BlockPos next = cur.pos.offset(d[0], 0, d[1]);
            if (!canWalkOn(level, next)) continue;
            tryAddNode(next, cur, COST_WALK + blockPenalty(level, next), MoveType.WALK, goal, open, bestG);
        }
    }

    private static void expandDiagonal(Level level, Node cur, BlockPos goal, PriorityQueue<Node> open, Map<Long, Double> bestG) {
        for (int[] d : DIAGONAL) {
            BlockPos next = cur.pos.offset(d[0], 0, d[1]);
            if (!canWalkOn(level, next)) continue;
            BlockPos a1 = cur.pos.offset(d[0], 0, 0), a2 = cur.pos.offset(0, 0, d[1]);
            if (!isPassable(level, a1) || !isPassable(level, a1.above())) continue;
            if (!isPassable(level, a2) || !isPassable(level, a2.above())) continue;
            tryAddNode(next, cur, COST_DIAGONAL + blockPenalty(level, next), MoveType.WALK_DIAGONAL, goal, open, bestG);
        }
    }

    private static void expandAscend(Level level, Node cur, BlockPos goal, PriorityQueue<Node> open, Map<Long, Double> bestG) {
        for (int[] d : CARDINAL) {
            BlockPos next = cur.pos.offset(d[0], 1, d[1]);
            if (!isSolid(level, next.below())) continue;
            if (!isPassable(level, next) || !isPassable(level, next.above())) continue;
            if (!isPassable(level, cur.pos.above(2))) continue;
            BlockPos aboveDest = cur.pos.offset(d[0], 2, d[1]);
            if (!isPassable(level, aboveDest)) continue;
            tryAddNode(next, cur, COST_ASCEND + blockPenalty(level, next), MoveType.ASCEND, goal, open, bestG);
        }
    }

    private static void expandDescend(Level level, Node cur, BlockPos goal, PriorityQueue<Node> open, Map<Long, Double> bestG) {
        for (int[] d : CARDINAL) {
            for (int drop = 1; drop <= MAX_FALL_DISTANCE; drop++) {
                BlockPos next = cur.pos.offset(d[0], -drop, d[1]);
                if (!isSolid(level, next.below())) continue;
                boolean clear = true;
                for (int dy = 0; dy >= -drop; dy--) {
                    BlockPos check = cur.pos.offset(d[0], dy, d[1]);
                    if (!isPassable(level, check) || !isPassable(level, check.above())) { clear = false; break; }
                }
                if (!clear) break;
                if (!isPassable(level, next) || !isPassable(level, next.above())) continue;
                if (isDangerous(level, next) || isDangerous(level, next.below())) continue;
                double cost = drop == 1 ? COST_DESCEND_1 : drop == 2 ? COST_DESCEND_2 : COST_DESCEND_3;
                tryAddNode(next, cur, cost + blockPenalty(level, next), drop == 1 ? MoveType.DESCEND : MoveType.FALL, goal, open, bestG);
                break;
            }
        }
    }

    private static void expandLadder(Level level, Node cur, BlockPos goal, PriorityQueue<Node> open, Map<Long, Double> bestG) {
        BlockPos up = cur.pos.above();
        if (isClimbable(level, up) && isPassable(level, up.above()))
            tryAddNode(up, cur, COST_LADDER, MoveType.LADDER, goal, open, bestG);
        BlockPos down = cur.pos.below();
        if (isClimbable(level, cur.pos) || isClimbable(level, down))
            if (isPassable(level, down) || isClimbable(level, down))
                tryAddNode(down, cur, COST_LADDER, MoveType.LADDER, goal, open, bestG);
    }

    private static void expandParkour(Level level, Node cur, BlockPos goal, PriorityQueue<Node> open, Map<Long, Double> bestG) {
        for (int[] d : CARDINAL) {
            BlockPos land = cur.pos.offset(d[0] * 2, 0, d[1] * 2);
            BlockPos mid = cur.pos.offset(d[0], 0, d[1]);
            if (isPassable(level, mid) && isPassable(level, mid.above())
                    && isSolid(level, land.below()) && isPassable(level, land) && isPassable(level, land.above())
                    && isPassable(level, cur.pos.above(2)) && !isSolid(level, mid.below()))
                tryAddNode(land, cur, COST_PARKOUR + blockPenalty(level, land), MoveType.PARKOUR, goal, open, bestG);
        }
    }

    private static void tryAddNode(BlockPos pos, Node parent, double moveCost, MoveType moveType, BlockPos goal,
                                    PriorityQueue<Node> open, Map<Long, Double> bestG) {
        Level level = Minecraft.getInstance().level;
        if (level == null || isDangerous(level, pos)) return;
        double newG = parent.g + moveCost;
        long key = posKey(pos);
        Double existing = bestG.get(key);
        if (existing != null && existing <= newG) return;
        bestG.put(key, newG);
        open.add(new Node(pos, newG, newG + heuristic(pos, goal), parent, moveType));
    }

    // ---- Path validation ----

    public static int validatePath(List<BlockPos> path, List<MoveType> moveTypes) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return 0;
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            MoveType move = (i < moveTypes.size()) ? moveTypes.get(i) : MoveType.WALK;
            if (!level.isLoaded(pos)) return i;
            if (isDangerous(level, pos)) return i;
            switch (move) {
                case WALK: case WALK_DIAGONAL: if (!canWalkOn(level, pos)) return i; break;
                case ASCEND:
                    if (!isSolid(level, pos.below()) || !isPassable(level, pos) || !isPassable(level, pos.above())) return i; break;
                case DESCEND: case FALL:
                    if (!isSolid(level, pos.below()) || !isPassable(level, pos) || !isPassable(level, pos.above())) return i;
                    if (i > 0) { BlockPos prev = path.get(i-1); int dy = prev.getY()-pos.getY();
                        for (int d=0;d<dy;d++) if (!isPassable(level,new BlockPos(pos.getX(),prev.getY()-d,pos.getZ()))) return i; } break;
                case PARKOUR:
                    if (!isSolid(level, pos.below()) || !isPassable(level, pos) || !isPassable(level, pos.above())) return i;
                    if (i > 0) { BlockPos prev = path.get(i-1);
                        BlockPos mid = new BlockPos((prev.getX()+pos.getX())/2, pos.getY(), (prev.getZ()+pos.getZ())/2);
                        if (isSolid(level, mid.below())) return i; } break;
                case LADDER: if (!isClimbable(level, pos) && !isClimbable(level, pos.below())) return i; break;
                default: break;
            }
        }
        return -1;
    }

    // ---- Block queries ----

    public static boolean canWalkOn(Level level, BlockPos pos) {
        return isSolid(level, pos.below()) && isPassable(level, pos) && isPassable(level, pos.above())
                && !isDangerous(level, pos) && !isDangerous(level, pos.below());
    }

    public static boolean isSolid(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        Block block = state.getBlock();
        if (block instanceof SlabBlock || block instanceof StairBlock) return true;
        if (block instanceof FenceBlock || block instanceof WallBlock) return true;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean isPassable(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        Block block = state.getBlock();
        if (block instanceof LadderBlock || block instanceof VineBlock) return true;
        if (block instanceof FenceGateBlock) return state.getValue(FenceGateBlock.OPEN);
        if (state.liquid()) return true;
        return state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean isClimbable(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof LadderBlock || block instanceof VineBlock;
    }

    public static boolean isDangerous(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return true;
        Block block = level.getBlockState(pos).getBlock();
        return block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.CACTUS;
    }

    private static double blockPenalty(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return 0;
        Block below = level.getBlockState(pos.below()).getBlock();
        if (below == Blocks.WATER) return PENALTY_WATER;
        if (below == Blocks.SOUL_SAND) return PENALTY_SOUL_SAND;
        if (below instanceof FenceBlock || below instanceof WallBlock) return 3.0;
        Block feet = level.getBlockState(pos).getBlock();
        if (feet == Blocks.WATER) return PENALTY_WATER;
        return 0;
    }

    private static double heuristic(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX()-to.getX()), dy = Math.abs(from.getY()-to.getY()), dz = Math.abs(from.getZ()-to.getZ());
        int minXZ = Math.min(dx, dz), maxXZ = Math.max(dx, dz);
        return minXZ * COST_DIAGONAL + (maxXZ - minXZ) * COST_WALK + dy * (from.getY() < to.getY() ? 1.5 : 0.8);
    }

    private static PathResult buildResult(Node endNode, boolean complete, int iterations) {
        List<BlockPos> path = new ArrayList<>(); List<MoveType> moveTypes = new ArrayList<>();
        for (Node n = endNode; n != null; n = n.parent) { path.add(n.pos); moveTypes.add(n.moveType); }
        Collections.reverse(path); Collections.reverse(moveTypes);
        if (!path.isEmpty()) { path.remove(0); moveTypes.remove(0); }
        if (path.size() > MAX_PATH_LENGTH) {
            path = new ArrayList<>(path.subList(0, MAX_PATH_LENGTH));
            moveTypes = new ArrayList<>(moveTypes.subList(0, MAX_PATH_LENGTH));
            complete = false;
        }
        return new PathResult(path, moveTypes, complete, iterations);
    }

    private static long posKey(BlockPos pos) {
        return ((long)(pos.getX()+30000000)<<36) | ((long)(pos.getZ()+30000000)<<12) | (long)(pos.getY()+64);
    }
}
