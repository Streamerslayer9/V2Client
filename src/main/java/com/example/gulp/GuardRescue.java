package com.example.gulp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Guard rescue: a protective guard who is a pred runs over to a villager with low health, scoops them up into their stomach (soft mode: they
 * are held like a villager you were fed, never digested), heals them while they carry them away from any danger, and lets them out
 * safely once they are well and nothing dangerous is near. It runs on the server twice a second for guards near players, it uses the
 * guard's normal pathfinding, and it can be turned off with guardRescueEnabled. The rescued villager is stored the same way as a
 * villager fed to a pred (VillagerFeeding), so they also come out after 20 minutes at the latest, and if the guard dies.
 */
final class GuardRescue {
    private GuardRescue() { }

    private static final float LOW = 0.4f;     // below this share of their health a villager needs rescuing
    private static final float WELL = 0.9f;    // above this they are well enough to be let out
    private static final double SEE = 24.0;    // how far a guard notices someone hurt
    private static final long CARRY_MAX = 2400L; // at most 2 minutes of carrying

    private enum Phase { CHASE, CARRY }

    private static final class Rescue {
        final UUID target;
        Phase phase = Phase.CHASE;
        final long started;
        long carriedSince;

        Rescue(UUID target, long started) {
            this.target = target;
            this.started = started;
        }
    }

    private static final Map<UUID, Rescue> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static MinecraftServer forServer;

    static void tick(MinecraftServer server) {
        if (!GulpServerConfig.MCA_ENABLED.get() || !GulpServerConfig.GUARD_RESCUE_ENABLED.get() || !McaIntegration.active()) return;
        if (forServer != server) { // a new world
            forServer = server;
            ACTIVE.clear();
            COOLDOWN.clear();
        }
        Set<UUID> targeted = new HashSet<>();
        for (Rescue r : ACTIVE.values()) targeted.add(r.target);
        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) continue;
            Set<LivingEntity> villagers = new LinkedHashSet<>();
            for (ServerPlayer p : level.players()) {
                AABB box = p.getBoundingBox().inflate(40.0);
                villagers.addAll(level.getEntitiesOfClass(LivingEntity.class, box, McaIntegration::isVillager));
            }
            for (LivingEntity g : villagers) {
                if (!g.isAlive() || !McaIntegration.isAdultVillager(g) || !McaIntegration.guard(g)) continue;
                if (McaIntegration.typeOf(g) != 2 || McaIntegration.flavorOf(g) != Flavor.PROTECTIVE) continue;
                try {
                    step(level, g, villagers, targeted);
                } catch (RuntimeException e) {
                    ACTIVE.remove(g.getUUID()); // never let one guard's trouble stop the others
                }
            }
        }
    }

    private static void step(ServerLevel level, LivingEntity guard, Set<LivingEntity> villagers, Set<UUID> targeted) {
        long now = level.getGameTime();
        UUID id = guard.getUUID();
        Rescue r = ACTIVE.get(id);
        if (r == null) {
            if (now < COOLDOWN.getOrDefault(id, 0L) || VillagerHolding.holdingCount(id) > 0) return;
            int room = Math.max(1, VillagerHolding.capacityOf(guard)) - VillagerFeeding.fedCount(guard);
            if (room <= 0) return;
            LivingEntity best = null;
            double bestDist = SEE * SEE;
            for (LivingEntity t : villagers) {
                if (t == guard || !t.isAlive() || targeted.contains(t.getUUID())) continue;
                if (t.getHealth() > t.getMaxHealth() * LOW) continue;
                if (VillagerHolding.holdingCount(t.getUUID()) > 0 || VillagerFeeding.fedCount(t) > 0) continue; // they are carrying someone: leave them be
                double d = guard.distanceToSqr(t);
                if (d < bestDist) { best = t; bestDist = d; }
            }
            if (best == null) return;
            ACTIVE.put(id, new Rescue(best.getUUID(), now));
            targeted.add(best.getUUID());
            tellNear(level, guard, guard.getName(), "Hold on! I'm coming!");
            return;
        }

        if (r.phase == Phase.CHASE) {
            Entity t = level.getEntity(r.target);
            if (!(t instanceof LivingEntity target) || !target.isAlive() || target.getHealth() > target.getMaxHealth() * WELL || now - r.started > 600L) {
                finish(id, now); // got away, got better, or too far to reach
                return;
            }
            if (guard.distanceToSqr(target) <= 2.5 * 2.5) {
                if (scoop(guard, target, now)) {
                    r.phase = Phase.CARRY;
                    r.carriedSince = now;
                    tellNear(level, guard, guard.getName(), "I've got you. Stay still while I get you somewhere safe.");
                } else {
                    finish(id, now);
                }
            } else if (guard instanceof Mob mob) {
                mob.getNavigation().moveTo(target, 1.2);
            }
            return;
        }

        // carrying: heal them, keep away from danger, let them out when they are well and it is calm
        ListTag fed = VillagerFeeding.fedOf(guard);
        int index = -1;
        for (int i = 0; i < fed.size(); i++) if (fed.getCompound(i).getBoolean("Rescue")) index = i;
        if (index < 0) { // they were let out some other way
            finish(id, now);
            return;
        }
        CompoundTag rec = fed.getCompound(index);
        CompoundTag ent = rec.getCompound("Entity");
        float max = maxHealthOf(ent);
        float hp = Math.min(max, ent.getFloat("Health") + 0.75f); // 1.5 HP a second, called twice a second
        ent.putFloat("Health", hp);
        VillagerFeeding.saveFed(guard, fed); // saves the new health

        Mob danger = nearestDanger(level, guard);
        if (danger != null && guard instanceof PathfinderMob mob && now % 20L < 10L) {
            Vec3 away = LandRandomPos.getPosAway(mob, 16, 7, danger.position());
            if (away != null) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
        }
        boolean well = hp >= max * WELL;
        boolean calm = danger == null || guard.distanceToSqr(danger) > 14.0 * 14.0;
        if ((well && calm) || now - r.carriedSince > CARRY_MAX) {
            VillagerFeeding.release(guard, null, index);
            tellNear(level, guard, guard.getName(), well ? "You're safe now. Look after yourself out there." : "That's all the time I can give you. Stay safe.");
            finish(id, now);
        }
    }

    private static void finish(UUID guard, long now) {
        ACTIVE.remove(guard);
        COOLDOWN.put(guard, now + 600L); // half a minute before they go looking for the next one
    }

    /** Takes the villager out of the world and into the guard's stomach, like a villager fed to a pred. */
    private static boolean scoop(LivingEntity guard, LivingEntity target, long now) {
        if (VillagerHolding.holdingCount(target.getUUID()) > 0 || VillagerFeeding.fedCount(target) > 0) return false;
        CompoundTag nbt = new CompoundTag();
        if (!target.save(nbt)) return false;
        CompoundTag rec = new CompoundTag();
        rec.put("Entity", nbt);
        rec.putString("Owner", "");
        rec.putString("Name", target.getName().getString());
        rec.putString("Type", McaIntegration.voreTypeOf(target).name());
        rec.putString("Flavor", McaIntegration.flavorOf(target).name());
        rec.putLong("Since", now);
        rec.putInt("PendingHearts", 0);
        rec.putBoolean("Rescue", true);
        ListTag list = VillagerFeeding.fedOf(guard);
        list.add(rec);
        VillagerFeeding.saveFed(guard, list);
        target.discard();
        return true;
    }

    private static Mob nearestDanger(ServerLevel level, LivingEntity guard) {
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, guard.getBoundingBox().inflate(20.0), m -> m instanceof Enemy && m.isAlive());
        Mob best = null;
        double bestDist = Double.MAX_VALUE;
        for (Mob m : mobs) {
            double d = guard.distanceToSqr(m);
            if (d < bestDist) { best = m; bestDist = d; }
        }
        return best;
    }

    /** A villager's maximum health from their saved data (20 if it does not say). */
    private static float maxHealthOf(CompoundTag entity) {
        ListTag attributes = entity.getList("Attributes", 10);
        for (int i = 0; i < attributes.size(); i++) {
            CompoundTag a = attributes.getCompound(i);
            if ("minecraft:generic.max_health".equals(a.getString("Name"))) return (float) Math.max(1.0, a.getDouble("Base"));
        }
        return 20f;
    }

    private static void tellNear(ServerLevel level, Entity speaker, Component name, String line) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(speaker) <= 32.0 * 32.0) p.sendSystemMessage(Component.literal("<").append(name).append("> " + line));
        }
    }
}
