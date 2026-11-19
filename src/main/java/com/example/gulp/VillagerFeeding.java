package com.example.gulp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.projectile.ProjectileUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Feeding a villager to a pred villager, and asking for them back. The player has a villager inside (soft mode, stomach) and presses
 * Release (the key, or the villager's Release button on the stomach screen) while looking at a pred villager: the villager moves out of
 * the player's stomach into the pred's. The pred and the fed villager both change how they feel about the player, depending on what each
 * of them is (pred or prey or neither, and their personality). To get them back, open the pred's Storage and press Release villager:
 * the pred lets them out, and can say no. A villager is never lost: they
 * come out after 20 minutes on their own, and when the pred dies. Server side only; the data is saved with the pred (VillagerData).
 */
final class VillagerFeeding {
    private VillagerFeeding() { }

    static final String KEY = "gulp_fed";
    private static final long KEEP_TICKS = 24000L; // 20 minutes
    private static final int RANGE = 8;

    private static final Set<UUID> FEEDERS = new HashSet<>();
    private static MinecraftServer loadedFor;

    // ------------------------------------------------------------------ what is saved

    static ListTag fedOf(Entity pred) {
        return VillagerData.getCompound(pred, KEY).getList("Fed", Tag.TAG_COMPOUND);
    }

    static void saveFed(Entity pred, ListTag list) {
        if (list.isEmpty()) {
            VillagerData.remove(pred, KEY);
            FEEDERS.remove(pred.getUUID());
        } else {
            CompoundTag t = new CompoundTag();
            t.put("Fed", list);
            VillagerData.put(pred, KEY, t);
            FEEDERS.add(pred.getUUID());
        }
    }

    /** The names of the villagers this pred was fed. */
    static java.util.List<String> fedNames(Entity pred) {
        java.util.List<String> out = new ArrayList<>();
        ListTag list = fedOf(pred);
        for (int i = 0; i < list.size(); i++) out.add(list.getCompound(i).getString("Name"));
        return out;
    }

    /** How many villagers this pred is holding right now. */
    static int fedCount(Entity pred) {
        return fedOf(pred).size();
    }

    private static void say(ServerPlayer p, Component name, String line) {
        if (line != null) p.sendSystemMessage(Component.literal("<").append(name).append("> " + line));
    }

    // ------------------------------------------------------------------ feeding

    /** The pred villager the player is looking at (an adult pred within reach), or null. */
    private static Entity lookedAtPred(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(RANGE));
        AABB box = p.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && McaIntegration.isVillager(e), (double) RANGE * RANGE);
        if (hit == null) return null;
        Entity e = hit.getEntity();
        return McaIntegration.isAdultVillager(e) && McaIntegration.typeOf(e) == 2 ? e : null;
    }

    /**
     * The Release key (uid = -1: the last villager you swallowed) or a villager's Release button on the stomach screen (uid = theirs), pressed
     * while looking at a pred villager: the villager is fed to the pred instead of being let out. Looking at anything else just releases
     * them as usual.
     * @return true if it was handled here (fed, or the pred said no), so nothing is released
     */
    static boolean feedLookedAt(ServerPlayer p, Stomach s, StomachManager mgr, int uid) {
        Entity pred = lookedAtPred(p);
        if (pred == null) return false;
        CompoundTag entry = null;
        if (uid >= 0) {
            for (CompoundTag e : s.contents) {
                if (e.getInt("Uid") == uid) { entry = e; break; }
            }
        } else {
            for (int i = s.contents.size() - 1; i >= 0; i--) {
                if (s.contents.get(i).contains("McaInfo")) { entry = s.contents.get(i); break; }
            }
        }
        if (entry == null || !entry.contains("McaInfo")) return false; // not a villager: released as usual
        feedEntry(p, s, mgr, entry, pred);
        return true;
    }

    private static void feedEntry(ServerPlayer p, Stomach s, StomachManager mgr, CompoundTag entry, Entity pred) {
        if (Organ.of(entry) != 0) {
            StomachLogic.say(p, Component.literal("Only a villager in your stomach can be fed to a pred."));
            return;
        }
        if (s.isHard(0) && GulpServerConfig.HARD_MODE_ENABLED.get()) {
            StomachLogic.say(p, Component.literal("Your stomach is in hard mode. Switch it to soft first: you can't pass someone along who is being digested."));
            return;
        }
        if (McaIntegration.isChildEntry(entry, p.serverLevel())) {
            StomachLogic.say(p, Component.literal("Children can't be fed to anyone."));
            return;
        }
        int room = Math.max(1, VillagerHolding.capacityOf(pred)) - VillagerHolding.holdingCount(pred.getUUID()) - fedCount(pred);
        if (room <= 0) {
            say(p, pred.getName(), "My tummy is full right now, sorry!");
            return;
        }
        Flavor predFlavor = McaIntegration.flavorOf(pred);
        VillagerTrait predTemper = McaIntegration.traitOf(pred);
        CompoundTag info = entry.getCompound("McaInfo");
        String preyType = info.getString("Type"); // PREY, PRED or NORMAL

        // Move them: out of your stomach, into the pred's.
        s.contents.remove(entry);
        CompoundTag rec = new CompoundTag();
        rec.put("Entity", entry.getCompound("Entity").copy());
        rec.putString("Owner", p.getUUID().toString());
        rec.putString("Name", StomachLogic.entryName(entry).getString());
        rec.putString("Type", preyType);
        rec.putString("Flavor", info.getString("Flavor"));
        rec.putLong("Since", p.level().getGameTime());
        Flavor preyFlavor = Flavor.byName(info.getString("Flavor"));
        rec.putInt("PendingHearts", preyHeartsDelta(preyType, preyFlavor, info.getString("Attitude"), predFlavor));
        ListTag list = fedOf(pred);
        list.add(rec);
        saveFed(pred, list);
        mgr.setDirty();
        StomachLogic.sync(p, s);

        McaIntegration.reward(pred, p, predHeartsDelta(preyType, predFlavor, predTemper));
        McaIntegration.remember(pred, p.getUUID(), "gulp.fed");
        String predLine = predFlavor.fedByPlayerLine();
        if (predLine == null) predLine = predTemper == VillagerTrait.SHY ? "O-oh... for me? Um... thank you, I'll be careful with them..." : "Mmm, thanks! I'll take good care of them.";
        say(p, pred.getName(), predLine);
        String preyLine = preyFlavor.fedAsPreyLine();
        if (preyLine == null) preyLine = "NORMAL".equals(preyType) ? "Wait, what?! You passed me along?!" : "Oh! A different tummy! Well... okay.";
        say(p, Component.literal(rec.getString("Name")), preyLine);
        StomachLogic.say(p, Component.literal("You fed " + rec.getString("Name") + " to ").append(pred.getName())
                .append(". Open their Storage and press Release villager to get them back."));
    }

    /** How the pred feels about the player for being fed a villager (hearts): always up. A villager who wants it is the best gift. */
    private static int predHeartsDelta(String preyType, Flavor predFlavor, VillagerTrait temper) {
        int base = "PREY".equals(preyType) ? 8 : "PRED".equals(preyType) ? 4 : 6;
        return switch (predFlavor) {
            case ARROGANT -> base + 4;   // loves a good meal
            case FLUSTERED -> base - 2;  // pleased, but embarrassed about it
            case PROTECTIVE -> base;
            case NONE -> base;
        };
    }

    /**
     * How the fed villager feels about the player when they come out (hearts, applied on release): a prey villager likes it (up),
     * a normal villager does not (down), whatever the pred is.
     */
    private static int preyHeartsDelta(String preyType, Flavor preyFlavor, String attitude, Flavor predFlavor) {
        int base;
        if ("PREY".equals(preyType)) base = preyFlavor == Flavor.FLUSTERED ? 5 : 10;
        else if ("PRED".equals(preyType)) base = -6;
        else base = "FEARFUL".equals(attitude) ? -25 : "FAN".equals(attitude) ? -6 : -12;
        if (base < 0 && predFlavor == Flavor.PROTECTIVE) base /= 2; // a protective pred makes it gentler
        return base;
    }

    // ------------------------------------------------------------------ spitting up

    /** The Release villager button on a pred's open storage screen (action 41). */
    static void spitFromStorage(ServerPlayer p, int predId) {
        Entity pred = predId != 0 ? p.level().getEntity(predId) : VillagerStorage.openFor(p);
        if (pred == null) {
            StomachLogic.say(p, Component.literal("Open a pred villager's Stomach screen or Storage first."));
            return;
        }
        spit(p, pred.getId());
    }

    /** Ask the pred to let out the villagers they hold (arg = the pred's entity id). */
    static void spit(ServerPlayer p, int predId) {
        spit(p, predId, -1);
    }

    /** A villager's own Release button on a pred's Stomach screen (action 45): packed = the pred's entity id * 64 + which fed villager. */
    static void spitOne(ServerPlayer p, int packed) {
        spit(p, packed >> 6, packed & 63);
    }

    /** @param only which fed villager to let out (their place in the list), or -1 for all of them */
    private static void spit(ServerPlayer p, int predId, int only) {
        Entity pred = p.level().getEntity(predId);
        if (!(pred instanceof LivingEntity) || !McaIntegration.isVillager(pred) || p.distanceToSqr(pred) > RANGE * RANGE) {
            StomachLogic.say(p, Component.literal("Get closer to the villager first."));
            return;
        }
        ListTag list = fedOf(pred);
        if (list.isEmpty() || only >= list.size()) {
            say(p, pred.getName(), "I'm not holding anyone right now.");
            return;
        }
        Flavor flavor = McaIntegration.flavorOf(pred);
        int hearts = McaIntegration.heartsOf(pred, p);
        if (flavor == Flavor.ARROGANT && hearts < 40 && p.getRandom().nextBoolean()) {
            say(p, pred.getName(), flavor.spitRefuseLine());
            return;
        }
        if (flavor == Flavor.NONE && hearts < -10) {
            say(p, pred.getName(), "No. I don't like you enough to do you favours.");
            return;
        }
        String yes = flavor.spitAcceptLine();
        say(p, pred.getName(), yes != null ? yes : "Alright, alright. Here they come.");
        release(pred, p, only);
    }

    /** Lets every fed villager out next to the pred. @param asker the player to tell (may be null) */
    static void releaseAll(Entity pred, ServerPlayer asker) {
        release(pred, asker, -1);
    }

    /** Lets out the fed villager at place `only` (or all of them when it is -1); anyone who could not be placed stays inside. */
    static void release(Entity pred, ServerPlayer asker, int only) {
        ListTag list = fedOf(pred);
        if (list.isEmpty() || !(pred.level() instanceof ServerLevel w)) return;
        MinecraftServer server = w.getServer();
        ListTag remaining = new ListTag();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag rec = list.getCompound(i);
            if (only >= 0 && i != only) {
                remaining.add(rec);
                continue;
            }
            Entity e = EntityType.create(rec.getCompound("Entity"), w).orElse(null);
            if (e == null) {
                remaining.add(rec);
                continue;
            }
            double ang = (pred.getYRot() + 180f + (i - list.size() / 2.0) * 35.0) * Math.PI / 180.0;
            e.moveTo(pred.getX() - Math.sin(ang) * 1.2, pred.getY() + 0.3, pred.getZ() + Math.cos(ang) * 1.2, pred.getYRot() + 180f, 0f);
            if (!w.noCollision(e)) e.moveTo(pred.getX(), pred.getY() + 0.1, pred.getZ(), pred.getYRot() + 180f, 0f);
            e.setDeltaMovement(Vec3.ZERO);
            e.fallDistance = 0f;
            boolean added = w.addFreshEntity(e);
            if (!added) {
                e.setUUID(UUID.randomUUID());
                added = w.addFreshEntity(e);
            }
            if (!added) {
                remaining.add(rec);
                continue;
            }
            ServerPlayer owner = asker;
            try {
                ServerPlayer o = server.getPlayerList().getPlayer(UUID.fromString(rec.getString("Owner")));
                if (o != null) owner = o;
            } catch (IllegalArgumentException ignored) {
                // no owner saved
            }
            if (rec.getBoolean("Rescue")) { // a guard's rescue: the villager thanks whoever is near
                String thanks = Flavor.byName(rec.getString("Flavor")).spitDoneLine();
                for (ServerPlayer near : w.players()) {
                    if (near.distanceToSqr(e) <= 32.0 * 32.0) {
                        near.sendSystemMessage(Component.literal("<").append(e.getName()).append("> " + (thanks != null ? thanks : "Thank you... I thought that was the end of me.")));
                    }
                }
            }
            if (owner != null) {
                McaIntegration.reward(e, owner, rec.getInt("PendingHearts"));
                McaIntegration.remember(e, owner.getUUID(), "gulp.fed_to_pred");
                String line = Flavor.byName(rec.getString("Flavor")).spitDoneLine();
                if (line == null) line = "Phew. Fresh air.";
                say(owner, e.getName(), line);
            }
        }
        saveFed(pred, remaining);
    }

    // ------------------------------------------------------------------ never lose a villager

    /** Every 10 seconds: preds who held someone for 20 minutes let them out. */
    static void tick(MinecraftServer server) {
        if (loadedFor != server) { // a new world: find the preds that were holding someone when it last saved
            loadedFor = server;
            FEEDERS.clear();
            FEEDERS.addAll(VillagerData.idsWith(server, KEY));
        }
        if (FEEDERS.isEmpty()) return;
        for (UUID id : new ArrayList<>(FEEDERS)) {
            Entity pred = null;
            for (ServerLevel l : server.getAllLevels()) {
                pred = l.getEntity(id);
                if (pred != null) break;
            }
            if (pred == null) continue; // not loaded right now
            ListTag list = fedOf(pred);
            if (list.isEmpty()) {
                FEEDERS.remove(id);
                continue;
            }
            long now = pred.level().getGameTime();
            boolean expired = false;
            for (int i = 0; i < list.size(); i++) if (now - list.getCompound(i).getLong("Since") >= KEEP_TICKS) expired = true;
            if (expired) releaseAll(pred, null);
        }
    }

    /** A pred died: everyone they held comes out where they fell. Called before the pred's data is thrown away. */
    static void onPredDied(Entity pred) {
        if (pred.level().isClientSide || fedOf(pred).isEmpty()) return;
        releaseAll(pred, null);
    }
}
