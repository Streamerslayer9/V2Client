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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Feeding a villager to a pred villager, and asking for them back. The player has a villager inside (soft mode, stomach) and uses
 * the Feed button on a pred villager's Perks screen: the villager moves out of the player's stomach into the pred's. The pred and the
 * fed villager both change how they feel about the player, depending on what each of them is (pred or prey or neither, and their
 * personality). The Spit Up button asks the pred to let them out again, and the pred can say no. A villager is never lost: they
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

    private static ListTag fedOf(Entity pred) {
        return VillagerData.getCompound(pred, KEY).getList("Fed", Tag.TAG_COMPOUND);
    }

    private static void saveFed(Entity pred, ListTag list) {
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

    /** How many villagers this pred is holding right now. */
    static int fedCount(Entity pred) {
        return fedOf(pred).size();
    }

    private static void say(ServerPlayer p, Component name, String line) {
        if (line != null) p.sendSystemMessage(Component.literal("<").append(name).append("> " + line));
    }

    // ------------------------------------------------------------------ feeding

    /** The Feed button (action 40). */
    static void feed(ServerPlayer p, int predId) {
        Entity pred = p.level().getEntity(predId);
        if (!(pred instanceof LivingEntity predLiving) || !McaIntegration.isAdultVillager(pred) || p.distanceToSqr(pred) > RANGE * RANGE) {
            StomachLogic.say(p, Component.literal("Get closer to the villager first."));
            return;
        }
        if (McaIntegration.typeOf(pred) != 2) {
            StomachLogic.say(p, Component.literal("Only a pred villager can hold another villager."));
            return;
        }
        StomachManager mgr = StomachManager.get(p.getServer());
        Stomach s = mgr.of(p.getUUID());
        CompoundTag entry = null;
        for (CompoundTag e : s.contents) {
            if (e.contains("McaInfo") && Organ.of(e) == 0) { entry = e; break; }
        }
        if (entry == null) {
            StomachLogic.say(p, Component.literal("You have no villager in your stomach to feed them."));
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
        int hearts = McaIntegration.heartsOf(pred, p);
        if (predFlavor == Flavor.PROTECTIVE && !"PREY".equals(preyType)) {
            say(p, pred.getName(), "They look scared. I won't hurt a villager who doesn't want this.");
            return;
        }
        int need = predFlavor == Flavor.ARROGANT ? 0 : predFlavor == Flavor.FLUSTERED ? 20 : predTemper == VillagerTrait.SHY ? 15 : 8;
        if (hearts < need) {
            say(p, pred.getName(), predFlavor == Flavor.FLUSTERED ? "I-I'm sorry... I don't know you well enough for that yet..."
                    : "I'd rather not. I don't know you well enough.");
            return;
        }

        // Move them: out of your stomach, into the pred's.
        s.contents.remove(entry);
        CompoundTag rec = new CompoundTag();
        rec.put("Entity", entry.getCompound("Entity").copy());
        rec.putString("Owner", p.getUUID().toString());
        rec.putString("Name", StomachLogic.entryName(entry).getString());
        rec.putString("Type", preyType);
        rec.putString("Flavor", info.getString("Flavor"));
        rec.putLong("Since", p.level().getGameTime());
        int preyDelta = preyHeartsDelta(preyType, Flavor.byName(info.getString("Flavor")), info.getString("Attitude"), predFlavor);
        rec.putInt("PendingHearts", preyDelta);
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
        Flavor preyFlavor = Flavor.byName(info.getString("Flavor"));
        String preyLine = preyFlavor.fedAsPreyLine();
        if (preyLine == null) preyLine = "NORMAL".equals(preyType) ? "Wait, what?! You passed me along?!" : "Oh! A different tummy! Well... okay.";
        say(p, Component.literal(rec.getString("Name")), preyLine);
        StomachLogic.say(p, Component.literal("You fed " + rec.getString("Name") + " to ").append(pred.getName())
                .append(". Press Spit Up on their Perks screen to ask for them back."));
        predLiving.level().broadcastEntityEvent(predLiving, (byte) 14); // a few happy particles
    }

    /** How the pred feels about the player for being fed a villager (hearts). */
    private static int predHeartsDelta(String preyType, Flavor predFlavor, VillagerTrait temper) {
        boolean preyKind = "PREY".equals(preyType);
        int base = preyKind ? 8 : "PRED".equals(preyType) ? 2 : -2; // a villager who wants this is a gift; an unwilling one is a worry
        return switch (predFlavor) {
            case ARROGANT -> base + 4;                       // loves a good meal either way
            case FLUSTERED -> base - (preyKind ? 0 : 3);     // embarrassed by an unwilling one
            case PROTECTIVE -> base - (preyKind ? 0 : 4);    // does not like it if they did not want it
            case NONE -> temper == VillagerTrait.SHY && !preyKind ? base - 2 : base;
        };
    }

    /** How the fed villager will feel about the player when they come out (hearts, applied on release). */
    private static int preyHeartsDelta(String preyType, Flavor preyFlavor, String attitude, Flavor predFlavor) {
        int base;
        if ("PREY".equals(preyType)) base = preyFlavor == Flavor.FLUSTERED ? 5 : 10;
        else if ("PRED".equals(preyType)) base = -6;
        else base = "FEARFUL".equals(attitude) ? -25 : "FAN".equals(attitude) ? 4 : -12;
        if (base < 0 && predFlavor == Flavor.PROTECTIVE) base /= 2; // a protective pred makes it gentler
        if (base < 0 && preyFlavor == Flavor.ARROGANT) base -= 4;   // an arrogant villager hates being handed around
        return base;
    }

    // ------------------------------------------------------------------ spitting up

    /** The Spit Up button (action 41): ask the pred to let out the villagers they hold. */
    static void spit(ServerPlayer p, int predId) {
        Entity pred = p.level().getEntity(predId);
        if (!(pred instanceof LivingEntity) || !McaIntegration.isVillager(pred) || p.distanceToSqr(pred) > RANGE * RANGE) {
            StomachLogic.say(p, Component.literal("Get closer to the villager first."));
            return;
        }
        ListTag list = fedOf(pred);
        if (list.isEmpty()) {
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
        releaseAll(pred, p);
    }

    /** Lets every fed villager out next to the pred. @param asker the player to tell (may be null) */
    static void releaseAll(Entity pred, ServerPlayer asker) {
        ListTag list = fedOf(pred);
        if (list.isEmpty() || !(pred.level() instanceof ServerLevel w)) return;
        MinecraftServer server = w.getServer();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag rec = list.getCompound(i);
            Entity e = EntityType.create(rec.getCompound("Entity"), w).orElse(null);
            if (e == null) continue;
            double ang = (pred.getYRot() + 180f + (i - list.size() / 2.0) * 35.0) * Math.PI / 180.0;
            e.moveTo(pred.getX() - Math.sin(ang) * 1.2, pred.getY() + 0.3, pred.getZ() + Math.cos(ang) * 1.2, pred.getYRot() + 180f, 0f);
            if (!w.noCollision(e)) e.moveTo(pred.getX(), pred.getY() + 0.1, pred.getZ(), pred.getYRot() + 180f, 0f);
            e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            e.fallDistance = 0f;
            boolean added = w.addFreshEntity(e);
            if (!added) {
                e.setUUID(UUID.randomUUID());
                added = w.addFreshEntity(e);
            }
            if (!added) continue;
            ServerPlayer owner = asker;
            try {
                ServerPlayer o = server.getPlayerList().getPlayer(UUID.fromString(rec.getString("Owner")));
                if (o != null) owner = o;
            } catch (IllegalArgumentException ignored) {
                // no owner saved
            }
            if (owner != null) {
                McaIntegration.reward(e, owner, rec.getInt("PendingHearts"));
                McaIntegration.remember(e, owner.getUUID(), "gulp.fed_to_pred");
                String line = Flavor.byName(rec.getString("Flavor")).spitDoneLine();
                if (line == null) line = "Phew. Fresh air.";
                say(owner, e.getName(), line);
            }
        }
        saveFed(pred, new ListTag());
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
