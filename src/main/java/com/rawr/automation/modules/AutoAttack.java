package com.rawr.automation.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * AutoAttack - Automatically attacks hostile mobs within range.
 */
public class AutoAttack extends Module {

    private static final double REACH = 4.0;
    private static final int ATTACK_COOLDOWN_TICKS = 10;
    private int cooldown = 0;
    private boolean targetAnimals = false;

    public AutoAttack() {
        super("AutoAttack", "Automatically attacks hostile mobs nearby");
    }

    public boolean isTargetingAnimals() {
        return targetAnimals;
    }

    public void setTargetAnimals(boolean targetAnimals) {
        this.targetAnimals = targetAnimals;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        Entity target = findTarget(mc, player);
        if (target == null) return;

        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        cooldown = ATTACK_COOLDOWN_TICKS;
    }

    private Entity findTarget(Minecraft mc, LocalPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(REACH);
        List<Entity> entities = mc.level.getEntities(player, searchBox);

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;

            if (living.getHealth() <= 0) continue;
            if (!isValidTarget(living)) continue;

            double dist = player.distanceTo(entity);
            if (dist <= REACH && dist < closestDist) {
                closest = entity;
                closestDist = dist;
            }
        }

        return closest;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof Monster || entity instanceof Slime) {
            return true;
        }
        if (targetAnimals && entity instanceof Animal) {
            return true;
        }
        return false;
    }
}
