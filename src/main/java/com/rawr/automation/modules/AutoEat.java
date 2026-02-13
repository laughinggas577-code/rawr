package com.rawr.automation.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

/**
 * AutoEat - Automatically eats food when the player's hunger is below a threshold.
 * Finds food in the hotbar, switches to it, and uses it.
 */
public class AutoEat extends Module {

    private static final int HUNGER_THRESHOLD = 16; // Out of 20
    private int previousSlot = -1;
    private boolean isEating = false;
    private int eatTickCounter = 0;

    public AutoEat() {
        super("AutoEat", "Automatically eats food when hungry");
    }

    @Override
    protected void onDisable() {
        if (isEating) {
            stopEating();
        }
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.currentScreen != null) return;

        int foodLevel = player.getFoodStats().getFoodLevel();

        // If we're currently eating, keep holding right click
        if (isEating) {
            eatTickCounter++;
            // Food takes about 32 ticks (1.6 seconds) to eat
            if (eatTickCounter > 40 || foodLevel >= 20) {
                stopEating();
                return;
            }
            // Keep using item
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            return;
        }

        // Check if we need food
        if (foodLevel > HUNGER_THRESHOLD) return;

        // Don't interrupt if player is doing something
        if (player.isUsingItem()) return;

        // Find food in hotbar
        int foodSlot = findFoodInHotbar(player);
        if (foodSlot == -1) return;

        // Switch to food slot and start eating
        previousSlot = player.inventory.currentItem;
        player.inventory.currentItem = foodSlot;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        isEating = true;
        eatTickCounter = 0;
    }

    private void stopEating() {
        Minecraft mc = Minecraft.getMinecraft();
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        if (previousSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        previousSlot = -1;
        isEating = false;
        eatTickCounter = 0;
    }

    private int findFoodInHotbar(EntityPlayerSP player) {
        int bestSlot = -1;
        float bestSaturation = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemFood) {
                ItemFood food = (ItemFood) stack.getItem();
                // Prefer food with higher saturation
                float saturation = food.getSaturationModifier(stack);
                if (bestSlot == -1 || saturation > bestSaturation) {
                    bestSlot = i;
                    bestSaturation = saturation;
                }
            }
        }
        return bestSlot;
    }
}
