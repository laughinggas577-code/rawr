package com.rawr.automation.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * AutoFish - Automatically reels in fish and recasts the rod.
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
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof FishingRodItem)) {
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

    private void handleIdle(Minecraft mc, LocalPlayer player) {
        FishingHook hook = player.fishing;
        if (hook != null) {
            state = State.WAITING_FOR_BITE;
            lastBobberY = hook.getY();
            stableTicks = 0;
        } else {
            rightClick(mc);
            tickDelay = 20;
            state = State.WAITING_FOR_BITE;
        }
    }

    private void handleWaiting(Minecraft mc, LocalPlayer player) {
        FishingHook hook = player.fishing;
        if (hook == null) {
            state = State.IDLE;
            return;
        }

        if (stableTicks < 10) {
            lastBobberY = hook.getY();
            stableTicks++;
            return;
        }

        double currentY = hook.getY();
        double yDelta = lastBobberY - currentY;

        if (yDelta > 0.05) {
            state = State.REELING_IN;
            tickDelay = 2;
        }

        lastBobberY = currentY;
    }

    private void handleReeling(Minecraft mc, LocalPlayer player) {
        rightClick(mc);
        state = State.RECASTING;
        tickDelay = 15;
    }

    private void handleRecasting(Minecraft mc, LocalPlayer player) {
        rightClick(mc);
        state = State.WAITING_FOR_BITE;
        lastBobberY = 0;
        stableTicks = 0;
        tickDelay = 20;
    }

    private static Method rightClickMethod = null;

    private void rightClick(Minecraft mc) {
        // startUseItem() is private - access via reflection (Mojang mappings name)
        if (rightClickMethod == null) {
            String[] names = {"startUseItem", "rightClickMouse"};
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
