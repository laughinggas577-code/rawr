package com.rawr.automation.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AutoFarm - Automatically harvests fully grown crops within reach and replants them.
 */
public class AutoFarm extends Module {

    private static final int RANGE = 4;
    private int tickCounter = 0;
    private static final int TICK_INTERVAL = 4;

    public AutoFarm() {
        super("AutoFarm", "Auto-harvests and replants crops within reach");
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) return;

        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        BlockPos playerPos = player.blockPosition();

        for (int x = -RANGE; x <= RANGE; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -RANGE; z <= RANGE; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    tryHarvest(mc, player, pos);
                }
            }
        }
    }

    private void tryHarvest(Minecraft mc, LocalPlayer player, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof CropBlock) {
            if (((CropBlock) block).isMaxAge(state)) {
                breakAndReplant(mc, player, pos, block);
            }
        } else if (block == Blocks.NETHER_WART) {
            int age = state.getValue(NetherWartBlock.AGE);
            if (age >= 3) {
                breakAndReplant(mc, player, pos, block);
            }
        } else if (block == Blocks.MELON || block == Blocks.PUMPKIN) {
            mc.gameMode.continueDestroyBlock(pos, Direction.UP);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void breakAndReplant(Minecraft mc, LocalPlayer player, BlockPos pos, Block block) {
        mc.gameMode.continueDestroyBlock(pos, Direction.UP);
        player.swing(InteractionHand.MAIN_HAND);

        int seedSlot = findSeedSlot(player, block);
        if (seedSlot != -1) {
            int prevSlot = player.getInventory().selected;
            player.getInventory().selected = seedSlot;

            BlockHitResult hitResult = new BlockHitResult(
                    Vec3.atCenterOf(pos), Direction.UP, pos, false
            );
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);

            player.getInventory().selected = prevSlot;
        }
    }

    private int findSeedSlot(LocalPlayer player, Block block) {
        Item seedItem = null;

        if (block instanceof CropBlock) {
            if (block == Blocks.WHEAT) {
                seedItem = Items.WHEAT_SEEDS;
            } else if (block == Blocks.CARROTS) {
                seedItem = Items.CARROT;
            } else if (block == Blocks.POTATOES) {
                seedItem = Items.POTATO;
            }
        } else if (block == Blocks.NETHER_WART) {
            seedItem = Items.NETHER_WART;
        }

        if (seedItem == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == seedItem) {
                return i;
            }
        }
        return -1;
    }
}
