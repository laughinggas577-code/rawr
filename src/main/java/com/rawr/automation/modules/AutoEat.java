package com.rawr.automation.modules;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * AutoEat - Automatically eats food when the player's hunger is below a threshold.
 */
public class AutoEat extends Module {

    private static final int HUNGER_THRESHOLD = 16;
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
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) return;

        int foodLevel = player.getFoodData().getFoodLevel();

        if (isEating) {
            eatTickCounter++;
            if (eatTickCounter > 40 || foodLevel >= 20) {
                stopEating();
                return;
            }
            KeyMapping.set(mc.options.keyUse.getKey(), true);
            return;
        }

        if (foodLevel > HUNGER_THRESHOLD) return;
        if (player.isUsingItem()) return;

        int foodSlot = findFoodInHotbar(player);
        if (foodSlot == -1) return;

        previousSlot = player.getInventory().selected;
        player.getInventory().selected = foodSlot;
        KeyMapping.set(mc.options.keyUse.getKey(), true);
        isEating = true;
        eatTickCounter = 0;
    }

    private void stopEating() {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping.set(mc.options.keyUse.getKey(), false);
        if (previousSlot != -1 && mc.player != null) {
            mc.player.getInventory().selected = previousSlot;
        }
        previousSlot = -1;
        isEating = false;
        eatTickCounter = 0;
    }

    private int findFoodInHotbar(LocalPlayer player) {
        int bestSlot = -1;
        float bestSaturation = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                FoodProperties food = stack.get(DataComponents.FOOD);
                if (food != null) {
                    float saturation = food.saturation();
                    if (bestSlot == -1 || saturation > bestSaturation) {
                        bestSlot = i;
                        bestSaturation = saturation;
                    }
                }
            }
        }
        return bestSlot;
    }
}
