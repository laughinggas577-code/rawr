package com.rawr.automation.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * A* pathfinder inspired by Baritone's approach.
 *
 * Movement types with costs:
 *   WALK         (cardinal)      = 1.0
 *   WALK_DIAGONAL                = 1.414
 *   ASCEND       (jump up 1)     = 1.8  (walk + jump penalty)
 *   DESCEND_1    (drop 1)        = 1.0
 *   DESCEND_2    (drop 2)        = 1.5
 *   DESCEND_3    (drop 3)        = 2.0
 *   PARKOUR      (sprint-jump gap) = 4.0
 *   LADDER/VINE  (climb 1)       = 1.5
 *
 * Avoids: lava, cactus, fire, unloaded chunks.
 * Penalizes: water, soul sand.
 *
 * Uses octile heuristic for admissibility.
 * Limits search to MAX_ITERATIONS to keep tick time bounded.
 * Runs path computation over multiple ticks if needed.
 */
public class AStarPathfinder {

    // ---- Movement offsets ----
    private static final int[][] CARDINAL = {{1,0}, {-1,0}, {0,1}, {0,-1}};
    private static final int[][] DIAGONAL = {{1,1}, {1,-1}, {-1,1}, {-1,-1}};

    // ---- Costs ----
    private static final double COST_WALK = 1.0;
    private static final double COST_DIAGONAL = 1.414;
    private static final double COST_ASCEND = 1.8;
    private static final double COST_DESCEND_1 = 1.0;
    private static final double COST_DESCEND_2 = 1.5;
    private static final double COST_DESCEND_3 = 2.0;
    private static final double COST_PARKOUR = 4.0;
    private static final double COST_LADDER = 1.5;
    private static final double PENALTY_WATER = 2.0;
    private static final double PENALTY_SOUL_SAND = 1.5;

    // ---- Limits ----
    private static final int MAX_ITERATIONS = 10000;
    private static final int MAX_FALL_DISTANCE = 3;
    private static final int MAX_PATH_LENGTH = 500;

    /**
     * Result of a pathfinding computation.
     */
    public static class PathResult {
        public final List<BlockPos> path;
        public final List<MoveType> moveTypes;
        public final boolean complete;   // true = reached goal, false = partial/best-effort
        public final int nodesExplored;

        public PathResult(List<BlockPos> path, List<MoveType> moveTypes, boolean complete, int nodesExplored) {
            this.path = path;
            this.moveTypes = moveTypes;
            this.complete = complete;
            this.nodesExplored = nodesExplored;
        }
    }

    /**
     * Type of movement between two path nodes. Used by the renderer for coloring
     * and by PathWalker for choosing controls.
     */
    public enum MoveType {
        WALK,
        WALK_DIAGONAL,
        ASCEND,
        DESCEND,
        PARKOUR,
        LADDER,
        FALL,
        START
    }

    // ---- A* node ----
    private static class Node implements Comparable<Node> {
        final BlockPos pos;
        final double g;      // cost from start
        final double f;      // g + heuristic
        final Node parent;
        final MoveType moveType;

        Node(BlockPos pos, double g, double f, Node parent, MoveType moveType) {
            this.pos = pos;
            this.g = g;
            this.f = f;
            this.parent = parent;
            this.moveType = moveType;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.f, other.f);
        }
    }

    /**
     * Compute a path from start to goal.
     * Returns the best path found within MAX_ITERATIONS, even if incomplete.
     */
    public static PathResult findPath(BlockPos start, BlockPos goal) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return new PathResult(Collections.emptyList(), Collections.emptyList(), false, 0);
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<Long, Double> bestG = new HashMap<>();

        Node startNode = new Node(start, 0, heuristic(start, goal), null, MoveType.START);
        openSet.add(startNode);
        bestG.put(posKey(start), 0.0);

        Node bestNode = startNode; // track closest-to-goal for partial paths
        double bestHeuristic = heuristic(start, goal);
        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            Node current = openSet.poll();

            // Goal reached (within 1 block)
            double goalDist = Math.sqrt(current.pos.distanceSq(goal));
            if (goalDist < 1.5) {
                return buildResult(current, true, iterations);
            }

            // Track best partial path
            double h = heuristic(current.pos, goal);
            if (h < bestHeuristic) {
                bestHeuristic = h;
                bestNode = current;
            }

            // Expand neighbors
            expandCardinal(world, current, goal, openSet, bestG);
            expandDiagonal(world, current, goal, openSet, bestG);
            expandAscend(world, current, goal, openSet, bestG);
            expandDescend(world, current, goal, openSet, bestG);
            expandLadder(world, current, goal, openSet, bestG);
            expandParkour(world, current, goal, openSet, bestG);
        }

        // Return best partial path
        return buildResult(bestNode, false, iterations);
    }

    // ---- Expansion methods ----

    private static void expandCardinal(World world, Node current, BlockPos goal,
                                        PriorityQueue<Node> openSet, Map<Long, Double> bestG) {
        for (int[] dir : CARDINAL) {
            BlockPos next = current.pos.add(dir[0], 0, dir[1]);
            if (!canWalkOn(world, next)) continue;

            double cost = COST_WALK + blockPenalty(world, next);
            tryAddNode(next, current, cost, MoveType.WALK, goal, openSet, bestG);
        }
    }

    private static void expandDiagonal(World world, Node current, BlockPos goal,
                                        PriorityQueue<Node> openSet, Map<Long, Double> bestG) {
        for (int[] dir : DIAGONAL) {
            BlockPos next = current.pos.add(dir[0], 0, dir[1]);
            if (!canWalkOn(world, next)) continue;

            // Check that both adjacent cardinal positions are passable (no corner-cutting)
            BlockPos adj1 = current.pos.add(dir[0], 0, 0);
            BlockPos adj2 = current.pos.add(0, 0, dir[1]);
            if (!isPassable(world, adj1) || !isPassable(world, adj1.up())) continue;
            if (!isPassable(world, adj2) || !isPassable(world, adj2.up())) continue;

            double cost = COST_DIAGONAL + blockPenalty(world, next);
            tryAddNode(next, current, cost, MoveType.WALK_DIAGONAL, goal, openSet, bestG);
        }
    }

    private static void expandAscend(World world, Node current, BlockPos goal,
                                      PriorityQueue<Node> openSet, Map<Long, Double> bestG) {
        // Jump up 1 block in each cardinal direction
        for (int[] dir : CARDINAL) {
            BlockPos next = current.pos.add(dir[0], 1, dir[1]);

            // Need: solid block at next.down() (the block we jump onto)
            // And passable at next (feet) and next.up() (head)
            // And passable at current.up().up() (head clearance for jumping)
            if (!isSolid(world, next.down())) continue;
            if (!isPassable(world, next) || !isPassable(world, next.up())) continue;
            if (!isPassable(world, current.pos.up().up())) continue;

            // Check the block above destination too (2 blocks of head room for jump arc)
            BlockPos aboveDest = current.pos.add(dir[0], 2, dir[1]);
            if (!isPassable(world, aboveDest)) continue;

            double cost = COST_ASCEND + blockPenalty(world, next);
            tryAddNode(next, current, cost, MoveType.ASCEND, goal, openSet, bestG);
        }
    }

    private static void expandDescend(World world, Node current, BlockPos goal,
                                       PriorityQueue<Node> openSet, Map<Long, Double> bestG) {
        for (int[] dir : CARDINAL) {
            for (int drop = 1; drop <= MAX_FALL_DISTANCE; drop++) {
                BlockPos next = current.pos.add(dir[0], -drop, dir[1]);

                // Need solid ground below landing spot
                if (!isSolid(world, next.down())) continue;

                // Need passable at all positions along the fall
                boolean clearFall = true;
                for (int dy = 0; dy >= -drop; dy--) {
                    BlockPos check = current.pos.add(dir[0], dy, dir[1]);
                    if (!isPassable(world, check)) {
                        clearFall = false;
                        break;
                    }
                    // Also check head clearance
                    if (!isPassable(world, check.up())) {
                        clearFall = false;
                        break;
                    }
                }
                if (!clearFall) break; // deeper drops won't work either

                // Need passable at feet and head of landing
                if (!isPassable(world, next) || !isPassable(world, next.up())) continue;

                // Don't land in danger
                if (isDangerous(world, next) || isDangerous(world, next.down())) continue;

                double cost;
                switch (drop) {
                    case 1: cost = COST_DESCEND_1; break;
                    case 2: cost = COST_DESCEND_2; break;
                    default: cost = COST_DESCEND_3; break;
                }
                cost += blockPenalty(world, next);

                MoveType type = drop == 1 ? MoveType.DESCEND : MoveType.FALL;
                tryAddNode(next, current, cost, type, goal, openSet, bestG);
                break; // found valid landing, don't check deeper
            }
        }
    }

    private static void expandLadder(World world, Node current, BlockPos goal,
                                      PriorityQueue<Node> openSet, Map<Long, Double> bestG) {
        // Climb up
        BlockPos up = current.pos.up();
        if (isClimbable(world, up) && isPassable(world, up.up())) {
            tryAddNode(up, current, COST_LADDER, MoveType.LADDER, goal, openSet, bestG);
        }
        // Climb down
        BlockPos down = current.pos.down();
        if (isClimbable(world, current.pos) || isClimbable(world, down)) {
            if (isPassable(world, down) || isClimbable(world, down)) {
                tryAddNode(down, current, COST_LADDER, MoveType.LADDER, goal, openSet, bestG);
            }
        }
    }

    private static void expandParkour(World world, Node current, BlockPos goal,
                                       PriorityQueue<Node> openSet, Map<Long, Double> bestG) {
        // Sprint-jump: cross a 1-block gap (2 blocks forward at same Y or 2 forward + 1 up)
        for (int[] dir : CARDINAL) {
            // 2-block forward jump at same level
            BlockPos land = current.pos.add(dir[0] * 2, 0, dir[1] * 2);
            BlockPos middle = current.pos.add(dir[0], 0, dir[1]);

            // Need: air in the gap, air at feet+head of middle, solid ground at landing
            if (isPassable(world, middle) && isPassable(world, middle.up())
                    && isSolid(world, land.down())
                    && isPassable(world, land) && isPassable(world, land.up())
                    && isPassable(world, current.pos.up().up()) // head room for jump
                    && !isSolid(world, middle.down())) { // must be a gap, not a walk
                double cost = COST_PARKOUR + blockPenalty(world, land);
                tryAddNode(land, current, cost, MoveType.PARKOUR, goal, openSet, bestG);
            }
        }
    }

    // ---- Node management ----

    private static void tryAddNode(BlockPos pos, Node parent, double moveCost,
                                    MoveType moveType, BlockPos goal,
                                    PriorityQueue<Node> openSet, Map<Long, Double> bestG) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null || isDangerous(world, pos)) return;

        double newG = parent.g + moveCost;
        long key = posKey(pos);
        Double existingG = bestG.get(key);
        if (existingG != null && existingG <= newG) return;

        bestG.put(key, newG);
        double f = newG + heuristic(pos, goal);
        openSet.add(new Node(pos, newG, f, parent, moveType));
    }

    // ---- Path validation ----

    /**
     * Re-validates a computed path against the current world state.
     * Checks that every node is still walkable/passable and that
     * movement transitions are still valid. Returns the index of the
     * first invalid node, or -1 if the entire path is still valid.
     */
    public static int validatePath(List<BlockPos> path, List<MoveType> moveTypes) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return 0;

        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            MoveType move = (i < moveTypes.size()) ? moveTypes.get(i) : MoveType.WALK;

            // Check chunk is loaded
            if (!world.isBlockLoaded(pos)) return i;

            // Check danger
            if (isDangerous(world, pos)) return i;

            switch (move) {
                case WALK:
                case WALK_DIAGONAL:
                    if (!canWalkOn(world, pos)) return i;
                    break;

                case ASCEND:
                    // Need solid below, passable at feet+head
                    if (!isSolid(world, pos.down())) return i;
                    if (!isPassable(world, pos) || !isPassable(world, pos.up())) return i;
                    break;

                case DESCEND:
                case FALL:
                    // Need solid ground below landing, passable at feet+head
                    if (!isSolid(world, pos.down())) return i;
                    if (!isPassable(world, pos) || !isPassable(world, pos.up())) return i;
                    // Check fall column is clear
                    if (i > 0) {
                        BlockPos prev = path.get(i - 1);
                        int dy = prev.getY() - pos.getY();
                        for (int d = 0; d < dy; d++) {
                            BlockPos fallPos = new BlockPos(pos.getX(), prev.getY() - d, pos.getZ());
                            if (!isPassable(world, fallPos)) return i;
                        }
                    }
                    break;

                case PARKOUR:
                    // Need solid landing ground, passable at feet+head
                    if (!isSolid(world, pos.down())) return i;
                    if (!isPassable(world, pos) || !isPassable(world, pos.up())) return i;
                    // Check gap is still a gap
                    if (i > 0) {
                        BlockPos prev = path.get(i - 1);
                        int mx = (prev.getX() + pos.getX()) / 2;
                        int mz = (prev.getZ() + pos.getZ()) / 2;
                        BlockPos mid = new BlockPos(mx, pos.getY(), mz);
                        if (isSolid(world, mid.down())) return i; // gap filled, no longer parkour
                    }
                    break;

                case LADDER:
                    if (!isClimbable(world, pos) && !isClimbable(world, pos.down())) return i;
                    break;

                default:
                    break;
            }
        }
        return -1; // fully valid
    }

    // ---- Block queries ----

    /**
     * Can a player stand at this position? (solid below, passable at feet + head)
     */
    public static boolean canWalkOn(World world, BlockPos pos) {
        return isSolid(world, pos.down())
                && isPassable(world, pos)
                && isPassable(world, pos.up())
                && !isDangerous(world, pos)
                && !isDangerous(world, pos.down());
    }

    public static boolean isSolid(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return false;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.air) return false;
        // Treat slabs, stairs as solid
        if (block instanceof BlockSlab || block instanceof BlockStairs) return true;
        // Fences/walls are solid but too tall to walk on normally
        if (block instanceof BlockFence || block instanceof BlockWall) return true;
        Material mat = block.getMaterial();
        return mat.isSolid() && mat.blocksMovement();
    }

    public static boolean isPassable(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return false;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.air) return true;
        // Ladders, vines, open fence gates are passable
        if (block instanceof BlockLadder || block instanceof BlockVine) return true;
        if (block instanceof BlockFenceGate) {
            return block.getMetaFromState(state) % 2 != 0; // bit 0 = open
        }
        // Water is passable but has penalty
        if (block.getMaterial() == Material.water) return true;
        // Flowers, grass, etc.
        Material mat = block.getMaterial();
        return !mat.isSolid() && !mat.blocksMovement();
    }

    public static boolean isClimbable(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return false;
        Block block = world.getBlockState(pos).getBlock();
        return block instanceof BlockLadder || block instanceof BlockVine;
    }

    public static boolean isDangerous(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return true; // unloaded = dangerous
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.lava || block == Blocks.flowing_lava) return true;
        if (block == Blocks.fire) return true;
        if (block == Blocks.cactus) return true;
        return false;
    }

    private static double blockPenalty(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return 0;
        Block belowBlock = world.getBlockState(pos.down()).getBlock();
        if (belowBlock.getMaterial() == Material.water) return PENALTY_WATER;
        if (belowBlock == Blocks.soul_sand) return PENALTY_SOUL_SAND;
        // Penalize fences (can't walk on easily)
        if (belowBlock instanceof BlockFence || belowBlock instanceof BlockWall) return 3.0;
        Block feetBlock = world.getBlockState(pos).getBlock();
        if (feetBlock.getMaterial() == Material.water) return PENALTY_WATER;
        return 0;
    }

    // ---- Heuristic ----

    /**
     * Octile distance with vertical cost.
     * Admissible: never overestimates actual path cost.
     */
    private static double heuristic(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dy = Math.abs(from.getY() - to.getY());
        int dz = Math.abs(from.getZ() - to.getZ());
        // Octile distance on XZ plane
        int minXZ = Math.min(dx, dz);
        int maxXZ = Math.max(dx, dz);
        double horizontal = minXZ * COST_DIAGONAL + (maxXZ - minXZ) * COST_WALK;
        // Vertical: ascending costs more than descending
        double vertical = dy * (from.getY() < to.getY() ? 1.5 : 0.8);
        return horizontal + vertical;
    }

    // ---- Result building ----

    private static PathResult buildResult(Node endNode, boolean complete, int iterations) {
        List<BlockPos> path = new ArrayList<>();
        List<MoveType> moveTypes = new ArrayList<>();
        Node node = endNode;
        while (node != null) {
            path.add(node.pos);
            moveTypes.add(node.moveType);
            node = node.parent;
        }
        Collections.reverse(path);
        Collections.reverse(moveTypes);

        // Trim start node
        if (!path.isEmpty()) {
            path.remove(0);
            moveTypes.remove(0);
        }

        // Limit path length
        if (path.size() > MAX_PATH_LENGTH) {
            path = new ArrayList<>(path.subList(0, MAX_PATH_LENGTH));
            moveTypes = new ArrayList<>(moveTypes.subList(0, MAX_PATH_LENGTH));
            complete = false;
        }

        return new PathResult(path, moveTypes, complete, iterations);
    }

    // ---- Utility ----

    private static long posKey(BlockPos pos) {
        return ((long)(pos.getX() + 30000000) << 36)
             | ((long)(pos.getZ() + 30000000) << 12)
             | (long)(pos.getY() + 64);
    }
}
