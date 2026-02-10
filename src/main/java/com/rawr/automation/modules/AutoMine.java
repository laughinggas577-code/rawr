package com.rawr.automation.modules;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;

import java.util.Arrays;
import java.util.List;

/**
 * AutoMine - Mines looked-at blocks, or pathfinds to configured ore targets.
 */
public class AutoMine extends Module {

    private static final int SEARCH_RADIUS = 18;
    private static final List<String> TARGET_BLOCKS = Arrays.asList(
            "diamond_ore",
            "emerald_ore",
            "gold_ore",
            "iron_ore",
            "coal_ore",
            "redstone_ore",
            "lapis_ore",
            "obsidian"
    );

    private boolean wasBreaking = false;
    private String targetBlock = TARGET_BLOCKS.get(0);

    public AutoMine() {
        super("AutoMine", "Automatically mines blocks you look at");
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && wasBreaking) {
            PlayerControllerMP controller = mc.playerController;
            if (controller != null) {
                controller.resetBlockRemoving();
            }
            wasBreaking = false;
        }
    }

    public void setTargetBlock(String blockName) {
        if (blockName == null || blockName.trim().isEmpty()) return;
        if (TARGET_BLOCKS.contains(blockName)) {
            targetBlock = blockName;
        }
    }

    public String getTargetBlock() {
        return targetBlock;
    }

    public void cycleTargetBlock() {
        int idx = TARGET_BLOCKS.indexOf(targetBlock);
        if (idx < 0) idx = 0;
        targetBlock = TARGET_BLOCKS.get((idx + 1) % TARGET_BLOCKS.size());
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.currentScreen != null) return;

        BlockPos target = findNearestTargetBlock(mc, player);
        if (target != null) {
            double dist = player.getDistance(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            if (dist > 4.5) {
                PathWalker walker = ModuleManager.getInstance() != null ? (PathWalker) ModuleManager.getInstance().getModule("pathwalker") : null;
                if (walker != null) {
                    walker.setTarget(target.getX(), target.getY(), target.getZ());
                    if (!walker.isEnabled()) {
                        walker.setEnabled(true);
                    }
                }
                return;
            }
            mineBlock(mc, target, EnumFacing.UP);
            return;
        }

        // fallback: old behavior
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            if (wasBreaking) {
                mc.playerController.resetBlockRemoving();
                wasBreaking = false;
            }
            return;
        }

        mineBlock(mc, mop.getBlockPos(), mop.sideHit);
    }

    private void mineBlock(Minecraft mc, BlockPos pos, EnumFacing face) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block.getMaterial().isLiquid()) return;

        if (mc.playerController.onPlayerDamageBlock(pos, face)) {
            mc.thePlayer.swingItem();
            wasBreaking = true;
        }

        if (!wasBreaking) {
            mc.playerController.clickBlock(pos, face);
            wasBreaking = true;
        }
    }

    private BlockPos findNearestTargetBlock(Minecraft mc, EntityPlayerSP player) {
        BlockPos base = new BlockPos(player.posX, player.posY, player.posZ);
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos pos = base.add(x, y, z);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    String name = block.getRegistryName() != null ? block.getRegistryName().toString() : "";
                    if (!name.endsWith(targetBlock)) continue;

                    double d = player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (d < bestDist) {
                        bestDist = d;
                        bestPos = pos;
                    }
                }
            }
        }

        return bestPos;
    }
}
