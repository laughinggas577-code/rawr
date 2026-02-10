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

    private static final String[] RIGHT_CLICK_METHODS = {
            "rightClickMouse",
            "func_147121_ag",
            "ag"
    };

    private Method rightClickMethod;
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
                handleWaiting(player);
                break;
            case REELING_IN:
                handleReeling(mc);
                break;
            case RECASTING:
                handleRecasting(mc);
                break;
        }
    }

    private void handleIdle(Minecraft mc, EntityPlayerSP player) {
        EntityFishHook hook = player.fishEntity;
        if (hook != null) {
            state = State.WAITING_FOR_BITE;
            lastBobberY = hook.posY;
            stableTicks = 0;
        } else {
            rightClick(mc);
            tickDelay = 20;
            state = State.WAITING_FOR_BITE;
        }
    }

    private void handleWaiting(EntityPlayerSP player) {
        EntityFishHook hook = player.fishEntity;
        if (hook == null) {
            state = State.IDLE;
            return;
        }

        if (stableTicks < 10) {
            lastBobberY = hook.posY;
            stableTicks++;
            return;
        }

        double currentY = hook.posY;
        double yDelta = lastBobberY - currentY;

        if (yDelta > 0.05) {
            state = State.REELING_IN;
            tickDelay = 2;
        }

        lastBobberY = currentY;
    }

    private void handleReeling(Minecraft mc) {
        rightClick(mc);
        state = State.RECASTING;
        tickDelay = 15;
    }

    private void handleRecasting(Minecraft mc) {
        rightClick(mc);
        state = State.WAITING_FOR_BITE;
        lastBobberY = 0;
        stableTicks = 0;
        tickDelay = 20;
    }

    private void rightClick(Minecraft mc) {
        try {
            Method method = resolveRightClickMethod();
            if (method != null) {
                method.invoke(mc);
            }
        } catch (Exception ignored) {
        }
    }

    private Method resolveRightClickMethod() {
        if (rightClickMethod != null) {
            return rightClickMethod;
        }

        for (String methodName : RIGHT_CLICK_METHODS) {
            try {
                Method method = Minecraft.class.getDeclaredMethod(methodName);
                method.setAccessible(true);
                rightClickMethod = method;
                return rightClickMethod;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
