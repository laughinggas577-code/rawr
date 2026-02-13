package com.rawr.automation.modules;

import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * AutoFarm - Automatically harvests fully grown crops within reach and replants them.
 * Supports wheat, carrots, potatoes, nether wart, and melon/pumpkin stems.
 */
public class AutoFarm extends Module {

    private static final int RANGE = 4;
    private int tickCounter = 0;
    private static final int TICK_INTERVAL = 4; // Check every 4 ticks

    public AutoFarm() {
        super("AutoFarm", "Auto-harvests and replants crops within reach");
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.currentScreen != null) return;

        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        BlockPos playerPos = new BlockPos(player.posX, player.posY, player.posZ);

        for (int x = -RANGE; x <= RANGE; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -RANGE; z <= RANGE; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    tryHarvest(mc, player, pos);
                }
            }
        }
    }

    private void tryHarvest(Minecraft mc, EntityPlayerSP player, BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof BlockCrops) {
            // Wheat, carrots, potatoes - metadata 7 = fully grown
            int meta = block.getMetaFromState(state);
            if (meta >= 7) {
                breakAndReplant(mc, player, pos, block);
            }
        } else if (block == Blocks.nether_wart) {
            int age = state.getValue(BlockNetherWart.AGE);
            if (age >= 3) {
                breakAndReplant(mc, player, pos, block);
            }
        } else if (block == Blocks.melon_block || block == Blocks.pumpkin) {
            // Just break melons/pumpkins, no replant needed (stems regrow)
            mc.playerController.onPlayerDamageBlock(pos, EnumFacing.UP);
            player.swingItem();
        }
    }

    private void breakAndReplant(Minecraft mc, EntityPlayerSP player, BlockPos pos, Block block) {
        // Break the crop
        mc.playerController.onPlayerDamageBlock(pos, EnumFacing.UP);
        player.swingItem();

        // Find seed in hotbar for replanting
        int seedSlot = findSeedSlot(player, block);
        if (seedSlot != -1) {
            int prevSlot = player.inventory.currentItem;
            player.inventory.currentItem = seedSlot;
            mc.playerController.onPlayerRightClick(
                    player, mc.theWorld, player.getHeldItem(), pos, EnumFacing.UP,
                    player.getLookVec()
            );
            player.inventory.currentItem = prevSlot;
        }
    }

    private int findSeedSlot(EntityPlayerSP player, Block block) {
        Item seedItem = null;

        if (block instanceof BlockCrops) {
            if (block == Blocks.wheat) {
                seedItem = Items.wheat_seeds;
            } else if (block == Blocks.carrots) {
                seedItem = Items.carrot;
            } else if (block == Blocks.potatoes) {
                seedItem = Items.potato;
            }
        } else if (block == Blocks.nether_wart) {
            seedItem = Items.nether_wart;
        }

        if (seedItem == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == seedItem) {
                return i;
            }
        }
        return -1;
    }
}
