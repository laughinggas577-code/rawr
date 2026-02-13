package com.rawr.automation.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;

/**
 * AutoFish - Automatically reels in fish and recasts the rod.
 * Detects when the bobber dips (fish bite) and reels in, then recasts.
 */
public class AutoFish extends Module {

    private enum State {
        IDLE,
        WAITING_FOR_BITE,
        REELING_IN,
        RECASTING
    }

    private State state = State.IDLE;
    private int tickDelay = 0;
    private double lastBobberY = 0;
    private int stableTicks = 0;

    public AutoFish() {
        super("AutoFish", "Automatically fishes for you");
    }

    @Override
    protected void onEnable() {
        state = State.IDLE;
        tickDelay = 0;
        stableTicks = 0;
    }

    @Override
    protected void onDisable() {
        state = State.IDLE;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.currentScreen != null) return;

        // Check if holding a fishing rod
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemFishingRod)) {
            state = State.IDLE;
            return;
        }

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        switch (state) {
            case IDLE:
                handleIdle(mc, player);
                break;
            case WAITING_FOR_BITE:
                handleWaiting(mc, player);
                break;
            case REELING_IN:
                handleReeling(mc, player);
                break;
            case RECASTING:
                handleRecasting(mc, player);
                break;
        }
    }

    private void handleIdle(Minecraft mc, EntityPlayerSP player) {
        EntityFishHook hook = player.fishEntity;
        if (hook != null) {
            // Already cast, start waiting
            state = State.WAITING_FOR_BITE;
            lastBobberY = hook.posY;
            stableTicks = 0;
        } else {
            // Cast the rod
            rightClick(mc);
            tickDelay = 20; // Wait 1 second for cast
            state = State.WAITING_FOR_BITE;
        }
    }

    private void handleWaiting(Minecraft mc, EntityPlayerSP player) {
        EntityFishHook hook = player.fishEntity;
        if (hook == null) {
            state = State.IDLE;
            return;
        }

        // Wait for bobber to settle
        if (stableTicks < 10) {
            lastBobberY = hook.posY;
            stableTicks++;
            return;
        }

        // Detect a bite: the bobber drops quickly (Y decreases)
        double currentY = hook.posY;
        double yDelta = lastBobberY - currentY;

        if (yDelta > 0.05) {
            // Fish bite detected!
            state = State.REELING_IN;
            tickDelay = 2; // Small delay to be natural
        }

        lastBobberY = currentY;
    }

    private void handleReeling(Minecraft mc, EntityPlayerSP player) {
        // Reel in the fish
        rightClick(mc);
        state = State.RECASTING;
        tickDelay = 15; // Wait before recasting
    }

    private void handleRecasting(Minecraft mc, EntityPlayerSP player) {
        // Cast again
        rightClick(mc);
        state = State.WAITING_FOR_BITE;
        lastBobberY = 0;
        stableTicks = 0;
        tickDelay = 20;
    }

    private static Method rightClickMethod = null;

    private void rightClick(Minecraft mc) {
        // rightClickMouse() is private - access via reflection
        // Try MCP name, then SRG name, then obfuscated name
        if (rightClickMethod == null) {
            String[] names = {"rightClickMouse", "func_147121_ag", "ag"};
            for (String name : names) {
                try {
                    rightClickMethod = Minecraft.class.getDeclaredMethod(name);
                    rightClickMethod.setAccessible(true);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        if (rightClickMethod != null) {
            try {
                rightClickMethod.invoke(mc);
            } catch (Exception ignored) {
            }
        }
    }
}
