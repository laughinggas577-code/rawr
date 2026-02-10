package com.rawr.automation.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.AxisAlignedBB;

import java.util.List;

/**
 * AutoAttack - Automatically attacks hostile mobs within range.
 * Only targets hostile mobs by default. Can be configured to target animals too.
 */
public class AutoAttack extends Module {

    private static final double REACH = 4.0;
    private static final int ATTACK_COOLDOWN_TICKS = 10; // ~0.5 seconds between swings
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
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.currentScreen != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        Entity target = findTarget(mc, player);
        if (target == null) return;

        // Attack the target
        mc.playerController.attackEntity(player, target);
        player.swingItem();
        cooldown = ATTACK_COOLDOWN_TICKS;
    }

    private Entity findTarget(Minecraft mc, EntityPlayerSP player) {
        AxisAlignedBB searchBox = player.getEntityBoundingBox().expand(REACH, REACH, REACH);
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(player, searchBox);

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : entities) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) entity;

            // Skip dead entities
            if (living.getHealth() <= 0) continue;

            // Check if it's a valid target
            if (!isValidTarget(living)) continue;

            double dist = player.getDistanceToEntity(entity);
            if (dist <= REACH && dist < closestDist) {
                closest = entity;
                closestDist = dist;
            }
        }

        return closest;
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        // Always target hostile mobs
        if (entity instanceof EntityMob || entity instanceof EntitySlime) {
            return true;
        }

        // Optionally target animals
        if (targetAnimals && entity instanceof EntityAnimal) {
            return true;
        }

        return false;
    }
}
