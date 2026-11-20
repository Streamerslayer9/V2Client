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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
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

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private enum Phase { CHASE, CARRY }

    private static final class Rescue {
        final UUID target;
        Phase phase = Phase.CHASE;
        final long started;
        long carriedSince;
        int stuck; // how many checks in a row the guard did not get closer
        double lastDistance = Double.MAX_VALUE;

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
                    LOGGER.warn("Vore Mod: guard rescue failed for {}", g.getName().getString(), e); // never let one guard's trouble stop the others
                    ACTIVE.remove(g.getUUID());
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
            LOGGER.info("Vore Mod: guard {} goes to rescue {} ({} HP)", guard.getName().getString(), best.getName().getString(), best.getHealth());
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
            } else {
                double d = guard.distanceToSqr(target);
                r.stuck = d > r.lastDistance - 1.0 ? r.stuck + 1 : 0; // no closer than last time
                r.lastDistance = d;
                if (r.stuck >= 6 && d <= SEE * SEE) { // three seconds without getting closer (MCA's own tasks keep walking them off): a dash
                    guard.teleportTo(target.getX() + 1.0, target.getY(), target.getZ());
                    r.stuck = 0;
                } else {
                    walkTo(guard, target.position(), 1.25f);
                }
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
            if (away != null) walkTo(guard, away, 1.1f);
        }
        boolean well = hp >= max * WELL;
        boolean calm = danger == null || guard.distanceToSqr(danger) > 14.0 * 14.0;
        if ((well && calm) || now - r.carriedSince > CARRY_MAX) {
            VillagerFeeding.release(guard, null, index);
            tellNear(level, guard, guard.getName(), well ? "You're safe now. Look after yourself out there." : "That's all the time I can give you. Stay safe.");
            finish(id, now);
        }
    }

    /**
     * Sends a villager walking: through their navigation, and through the walk target their brain's own tasks follow (MCA villagers keep
     * giving themselves new paths, so a navigation order alone is often cancelled a moment later).
     */
    private static void walkTo(LivingEntity e, Vec3 pos, float speed) {
        if (e instanceof Mob mob) mob.getNavigation().moveTo(pos.x, pos.y, pos.z, speed);
        try {
            e.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, speed, 1));
        } catch (RuntimeException ignored) {
            // this villager's brain has no walk target: the navigation order above has to do
        }
    }

    /** /gulp guards: what each villager near the player would do, to find out why a rescue does not start. */
    static void report(ServerPlayer p) {
        List<LivingEntity> near = p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40.0), McaIntegration::isVillager);
        int shown = 0;
        for (LivingEntity v : near) {
            boolean guard = McaIntegration.guard(v);
            if (!guard && v.getHealth() > v.getMaxHealth() * LOW) continue;
            if (shown++ >= 12) break;
            String line = v.getName().getString() + ": " + (guard ? "GUARD" : "villager") + ", "
                    + Math.round(v.getHealth()) + "/" + Math.round(v.getMaxHealth()) + " HP, "
                    + (McaIntegration.typeOf(v) == 2 ? "pred" : McaIntegration.typeOf(v) == 1 ? "prey" : "normal") + ", " + McaIntegration.flavorOf(v).name().toLowerCase();
            if (guard) {
                Rescue r = ACTIVE.get(v.getUUID());
                boolean can = McaIntegration.typeOf(v) == 2 && McaIntegration.flavorOf(v) == Flavor.PROTECTIVE;
                line += can ? (r != null ? " -> rescuing (" + r.phase.name().toLowerCase() + ")" : " -> ready") : " -> will NOT rescue (needs: pred + protective)";
            } else {
                line += v.getHealth() <= v.getMaxHealth() * LOW ? " -> needs rescuing" : "";
            }
            p.sendSystemMessage(Component.literal(" - " + line));
        }
        if (shown == 0) p.sendSystemMessage(Component.literal("[Gulp] No guards or hurt villagers within 40 blocks."));
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
