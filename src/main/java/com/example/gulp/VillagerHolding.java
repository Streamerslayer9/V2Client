package com.example.gulp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A pred villager holding a player to comfort them. Only ever soft and only ever by the player's say-so:
 * the villager offers to a hurt player whose role can be swallowed (Prey or Both), and the player has to press Accept.
 *
 * It works like PlayerHolding does for a player swallower: the held player is put in spectator mode looking through
 * the villager's eyes (so they can't interact with anything), and their old game mode is remembered, including in
 * their saved data so a crash can never leave anyone stuck in spectator mode. The player gets 1 HP back every second
 * and is let out when they are healed, after a minute, or the moment they press Release (G). They are also let out at
 * once if the villager is hurt, dies or goes away, or when the player logs out or the server stops.
 * Struggle (R) rubs the walls: it raises the villager's hearts a little (not more than once every 20 seconds).
 *
 * Comforting levels the villager up (saved on the villager itself; talking, gifts and rides give XP too, see VillagerXp):
 * every finished comfort gives them XP, and each level (1 to 10) heals faster (+0.25 HP/s), holds a little longer (+6 s) and, from level 5, also feeds the player.
 */
public final class VillagerHolding {
    private VillagerHolding() { }

    private static final int RUB_COOLDOWN = 20 * 20;
    static final int MAX_LEVEL = 10;
    private static final String KEY = "gulp_comfort"; // saved under the villager's UUID (VillagerData): Level, Xp, perks

    // ------------------------------------------------------------------ the villager's comfort level

    /** The villager's comfort level, 1 to 10 (everyone starts at 1). */
    static int levelOf(Entity villager) {
        int level = VillagerData.getCompound(villager, KEY).getInt("Level");
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    static int xpOf(Entity villager) {
        return VillagerData.getCompound(villager, KEY).getInt("Xp");
    }

    static int xpNeeded(int level) { return 30 + 20 * level; }

    /** HP healed every second at this level. */
    static float healPerSecond(int level) { return 1.0f + 0.25f * (level - 1); }

    /** The same with the villager's trait: confident preds heal faster. */
    static float healPerSecond(int level, VillagerTrait trait) { return healPerSecond(level) * trait.healMult; }

    /** The same with the villager's trait: shy preds hold you longer. */
    static int maxTicks(int level, VillagerTrait trait) { return Math.round(maxTicks(level) * trait.holdMult); }

    // ------------------------------------------------------------------ the villager's perks (saved on the villager itself)

    /** The rank this villager has in a perk (0 = not learned). Saved with the villager, so it is per villager and per world. */
    static int rankOf(Entity villager, VillagerPerk perk) {
        int rank = VillagerData.getCompound(villager, KEY).getInt("Perk_" + perk.name());
        return Math.max(0, Math.min(perk.maxRank, rank));
    }

    /** Perk points not spent yet: one per level above 1, minus the ranks already bought. */
    static int pointsFree(Entity villager) {
        int spent = 0;
        for (VillagerPerk perk : VillagerPerk.values()) spent += rankOf(villager, perk);
        return Math.max(0, levelOf(villager) - 1 - spent);
    }

    /** HP per second with level, trait and the Soothing Touch rank. */
    static float healPerSecond(int level, VillagerTrait trait, int soothing) {
        return healPerSecond(level, trait) + VillagerPerk.soothingBonus(soothing);
    }

    /** Holding time with level, trait and the Long Embrace rank. */
    static int maxTicks(int level, VillagerTrait trait, int embrace) {
        return maxTicks(level, trait) + VillagerPerk.embraceBonusTicks(embrace);
    }

    /** How many different perks this villager has at least one rank in (they have to fit in VillagerPerk.slotsFor). */
    static int distinctPerks(Entity villager) {
        int n = 0;
        for (VillagerPerk perk : VillagerPerk.values()) if (rankOf(villager, perk) > 0) n++;
        return n;
    }

    /** How many players this villager can hold at once: from their level and Roomy Belly. */
    static int capacityOf(Entity villager) {
        return VillagerPerk.capacityFor(levelOf(villager), rankOf(villager, VillagerPerk.ROOMY_BELLY));
    }

    private static int holdingCount(UUID villager) {
        int n = 0;
        for (Hold h : HELD.values()) if (h.villager.equals(villager)) n++;
        return n;
    }

    /** True when the villager is holding as many players as they can. */
    static boolean isFull(Entity villager) {
        return holdingCount(villager.getUUID()) >= capacityOf(villager);
    }

    /**
     * A prey villager's ride just ended safely (soft mode): Snug Passenger gives extra hearts, and Parting Gift gives the player XP
     * after a ride of 30 seconds or more, at most once every 10 minutes per villager (so it can't be farmed).
     */
    static void onPreyRide(ServerPlayer p, Entity villager, int heldTicks) {
        if (McaIntegration.typeOf(villager) != 1) return;
        int snug = rankOf(villager, VillagerPerk.SNUG_PASSENGER);
        if (snug > 0) McaIntegration.addHearts(villager, p, 3 * snug);
        int gift = rankOf(villager, VillagerPerk.PARTING_GIFT);
        if (gift <= 0 || heldTicks < 600) return;
        long now = villager.level().getGameTime();
        CompoundTag t = VillagerData.getCompound(villager, KEY);
        if (t.contains("GiftAt") && now - t.getLong("GiftAt") < 12000L) return;
        t.putLong("GiftAt", now);
        VillagerData.put(villager, KEY, t);
        int xp = VillagerPerk.partingGiftXp(gift, heldTicks);
        p.giveExperiencePoints(xp);
        p.sendSystemMessage(Component.empty().append(villager.getName()).append(" thanks you with a parting gift (+" + xp + " XP)."));
    }

    /** How long a cuddle lasts at this level, in ticks: 33 seconds at level 1, up to a minute at level 10. */
    static int cuddleTicks(int level) { return (30 + 3 * level) * 20; }

    /** The player spent a point on one of this villager's perks (action 21). Everything is checked here, on the server. */
    static void buyPerk(ServerPlayer p, int entityId, int perkIndex) {
        Entity v = p.level().getEntity(entityId);
        VillagerPerk perk = VillagerPerk.byIndex(perkIndex);
        if (perk == null || v == null || p.distanceToSqr(v) > 32.0 * 32.0) return;
        if (!McaIntegration.isAdultVillager(v)) {
            p.sendSystemMessage(Component.literal("Only adult villagers have perks."));
            return;
        }
        if (perk.forType != McaIntegration.typeOf(v)) {
            p.sendSystemMessage(Component.literal("That perk is not for this kind of villager."));
            return;
        }
        int rank = rankOf(v, perk);
        if (rank >= perk.maxRank) return;
        if (rank == 0 && distinctPerks(v) >= VillagerPerk.slotsFor(levelOf(v))) {
            p.sendSystemMessage(Component.literal("No free perk slot. ").append(v.getName()).append(" gets another one at a higher level."));
            return;
        }
        if (pointsFree(v) <= 0) {
            p.sendSystemMessage(Component.literal("No perk points left. Villagers get one for every level they gain."));
            return;
        }
        CompoundTag t = VillagerData.getCompound(v, KEY);
        t.putInt("Perk_" + perk.name(), rank + 1);
        VillagerData.put(v, KEY, t);
        p.sendSystemMessage(Component.empty().append(v.getName()).append(" learned " + perk.title + " (rank " + (rank + 1) + ")."));
        McaIntegration.sendVillagerInfo(p, entityId);
    }

    /** How long one comfort can last at this level, in ticks: a minute at level 1, 6 seconds longer for every level after. */
    static int maxTicks(int level) { return (60 + 6 * (level - 1)) * 20; }

    /** A finished comfort: the villager earns XP for the HP they healed. Comforting is never capped. */
    private static void awardXp(Entity villager, ServerPlayer p, float healed) {
        addXp(villager, p, 10 + Math.round(healed), false);
    }

    /**
     * Gives a villager XP and levels them up when it is enough (the player is told). Every villager can earn it, but only
     * adults: children never gain XP. Talking, gifts and rides are "capped" sources: together they give a villager at most
     * mcaVillagerXpDailyCap XP per Minecraft day, so they can't be farmed. Comforting is not capped (it needs a hurt player).
     * @return the XP actually given (0 when capped, maxed out or not allowed)
     */
    static int addXp(Entity villager, ServerPlayer p, int amount, boolean capped) {
        if (amount <= 0 || McaIntegration.isProtectedChild(villager)) return 0;
        int level = levelOf(villager);
        if (level >= MAX_LEVEL) return 0;
        CompoundTag t = VillagerData.getCompound(villager, KEY);
        if (capped) {
            long day = villager.level().getDayTime() / 24000L;
            int used = t.getLong("Day") == day ? t.getInt("DayXp") : 0;
            amount = Math.min(amount, GulpServerConfig.MCA_VILLAGER_XP_DAILY_CAP.get() - used);
            if (amount <= 0) return 0;
            t.putLong("Day", day);
            t.putInt("DayXp", used + amount);
        }
        int xp = xpOf(villager) + amount;
        boolean up = false;
        while (level < MAX_LEVEL && xp >= xpNeeded(level)) {
            xp -= xpNeeded(level);
            level++;
            up = true;
        }
        t.putInt("Level", level);
        t.putInt("Xp", level >= MAX_LEVEL ? 0 : xp);
        VillagerData.put(villager, KEY, t);
        if (up) {
            p.sendSystemMessage(Component.empty().append(villager.getName())
                    .append(" levelled up (level " + level + ")."));
        }
        return amount;
    }

    // ------------------------------------------------------------------ holding

    private static final class Hold {
        final UUID villager;
        final int previousMode;
        final boolean cuddle; // a cosy cuddle (the player is not hurt) instead of a comfort: it ends after a set time, not when healed
        boolean hostile; // an angry pred grabbed the player: no healing, Release does not work, the player has to struggle out
        ForcedOutcome forced; // set when the player climbed in on their own (Climb In), null for comfort and cuddles
        int ticks;
        int rubCd;
        float healed; // total HP given back, for the villager's XP
        // Russian doll: the villager was swallowed by another player while holding this one. The villager is then out of the world,
        // so what they could do is remembered here and the holding carries on from inside that player's stomach.
        UUID nestedIn;          // the player whose stomach the villager is in (null while the villager is out in the world)
        float nestedSize;       // this player's share of the stomach entry's size
        String playerName = ""; // shown as "(Steve player)" next to the villager in that stomach
        int cLevel = 1;
        VillagerTrait cTrait = VillagerTrait.NONE;
        int cSoothing;
        int cEmbrace;
        int cWarm;

        Hold(UUID villager, int previousMode, boolean cuddle) {
            this.villager = villager;
            this.previousMode = previousMode;
            this.cuddle = cuddle;
        }
    }

    /** player -> the villager holding them. Only lives while the server runs. */
    private static final Map<UUID, Hold> HELD = new HashMap<>();

    public static boolean isHeld(ServerPlayer p) { return HELD.containsKey(p.getUUID()); }

    /** Starts holding the player. The caller has already checked the player agreed and can be held. */
    public static boolean hold(ServerPlayer p, LivingEntity villager) {
        return hold(p, villager, false);
    }

    /** @param cuddle true for a cosy cuddle offered to a player who is not hurt (see McaIntegration.tickCuddle) */
    public static boolean hold(ServerPlayer p, LivingEntity villager, boolean cuddle) {
        return start(p, villager, cuddle, null, false);
    }

    /** An angry pred grabs a player without asking (see McaIntegration.tickHostile). Never hurts; the player has to struggle out. */
    static boolean holdHostile(ServerPlayer p, LivingEntity villager) {
        return start(p, villager, true, null, true);
    }

    /** Climb In: the player forced their way in. How it goes comes from the outcome (see ForcedOutcome, McaIntegration.forceSwallow). */
    static boolean holdForced(ServerPlayer p, LivingEntity villager, ForcedOutcome outcome) {
        return start(p, villager, true, outcome, false);
    }

    /** Whether an angry villager who keeps a player too long digests them (mcaHostileKills, and the player's role still allows it). */
    private static boolean lethalHostile(ServerPlayer p) {
        return GulpServerConfig.MCA_HOSTILE_KILLS.get() && StomachManager.get(p.getServer()).of(p.getUUID()).role.canBeEaten();
    }

    /** The time is up and the player did not break free: they die, and the villager keeps all their things. */
    private static void digestPlayer(ServerPlayer p, LivingEntity v, VillagerTrait trait) {
        Component name = p.getName();
        release(p, "You were digested.", false); // game mode back, camera back, you are put down next to the villager
        VillagerStorage.stash(v, p);
        Component line = Component.literal("<").append(v.getName()).append("> " + trait.hostileDigestLine());
        for (ServerPlayer o : p.getServer().getPlayerList().getPlayers()) {
            if (o == p || o.level() == v.level() && o.distanceToSqr(v) <= 32.0 * 32.0) o.sendSystemMessage(line);
        }
        McaIntegration.remember(v, p.getUUID(), "gulp.digested");
        p.getServer().getPlayerList().broadcastSystemMessage(Component.empty().append(name).append(" was digested by ").append(v.getName()).append("."), false);
        p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
    }

    /** Percent chance that one struggle frees a player from an angry villager: the same base chance as struggling out of a player, a little lower for a villager with a high level. */
    private static float hostileChance(Entity villager) {
        float base = GulpServerConfig.PLAYER_STRUGGLE_CHANCE.get().floatValue();
        return Math.max(5f, base * (1f - 0.03f * (levelOf(villager) - 1)));
    }

    /** Forgets every perk rank (used when a villager becomes a pred: they get a new perk list and the points back). */
    static void resetPerks(Entity villager) {
        CompoundTag t = VillagerData.getCompound(villager, KEY);
        for (VillagerPerk perk : VillagerPerk.values()) t.remove("Perk_" + perk.name());
        VillagerData.put(villager, KEY, t);
    }

    private static boolean start(ServerPlayer p, LivingEntity villager, boolean cuddle, ForcedOutcome forced, boolean hostile) {
        if (PlayerHolding.isHeld(p) || !p.isAlive()) return false;
        if (isFull(villager)) {
            p.sendSystemMessage(Component.literal("<").append(villager.getName()).append("> My tummy is full right now, sorry! Give me a moment."));
            return false;
        }
        int previous = p.gameMode.getGameModeForPlayer().getId();
        Hold hold = new Hold(villager.getUUID(), previous, cuddle);
        hold.forced = forced;
        hold.hostile = hostile;
        HELD.put(p.getUUID(), hold);
        if (hostile) { /* remembered by McaIntegration.tickHostile */ } else if (forced == null) McaIntegration.remember(villager, p.getUUID(), cuddle ? "gulp.cuddled" : "gulp.comforted"); // history: it makes them warmer next time
        p.getPersistentData().putInt(PlayerHolding.KEY_PREV_MODE, previous); // survives a crash
        p.setGameMode(GameType.SPECTATOR);
        p.setCamera(villager);
        if (hostile) {
            p.sendSystemMessage(Component.empty().append(villager.getName()).append(" grabs you and swallows you whole! They won't just let you out:"
                    + " press your Struggle key (default R) to try to break free."
                    + (lethalHostile(p) ? " If you are still in there after " + GulpServerConfig.MCA_HOSTILE_HOLD_SECONDS.get()
                    + " seconds, they will digest you and keep everything you carry!" : " They let go after a while.")));
        }
        String intro = forced != null
                ? (forced.rewards() ? "You climb into " : "You force your way into ")
                : null;
        if (hostile) {
            // the message above already said it
        } else if (intro != null) {
            p.sendSystemMessage(Component.literal(intro).append(villager.getName()).append("'s tummy."
                    + (forced.holdSeconds() > 0 ? " They won't put up with it for long." : "")
                    + " Press your Release key (default G) to get out whenever you like."));
        } else {
            p.sendSystemMessage(Component.empty().append(villager.getName()).append(
                    (cuddle ? " pulls you into a cosy cuddle in their tummy." : " is holding you in their tummy to help you heal.")
                    + " Press your Release key (default G) to get out whenever you like,"
                    + " or your Struggle key (default R) to rub the walls."));
        }
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HeldPacket(hostile ? 3 : 1, villager.getName().getString(), hostile && lethalHostile(p) ? 1f : 0f, hostile ? hostileChance(villager) : 0f));
        p.sendSystemMessage(Component.literal("<").append(villager.getName()).append("> " + (hostile ? McaIntegration.traitOf(villager).hostileGrabLine() : forced != null ? forced.enterLine()
                : cuddle ? McaIntegration.traitOf(villager).cuddleHoldLine() : McaIntegration.traitOf(villager).holdLine())));
        return true;
    }

    /** Every tick for every player (called from PlayerHolding.tickHeld). Heals, and lets the player out when it is time or something is wrong. */
    static void tick(ServerPlayer p) {
        Hold h = HELD.get(p.getUUID());
        if (h == null) return;
        if (h.rubCd > 0) h.rubCd--;
        Entity e = p.serverLevel().getEntity(h.villager);
        if (e == null && h.nestedIn != null) { // the villager is inside someone's stomach, and so are you: the holding is paused
            nestedTick(p, h);
            return;
        }
        h.ticks++;
        if (!(e instanceof LivingEntity v) || !v.isAlive() || v.hurtTime > 0) {
            release(p, "The villager had to let you out.", false);
            return;
        }
        if (h.nestedIn != null) { // the villager was let out of that stomach: they carry on holding you
            h.nestedIn = null;
            p.sendSystemMessage(Component.empty().append(v.getName()).append(" is out in the world again, and still has you in their tummy."));
            p.sendSystemMessage(Component.literal("<").append(v.getName()).append("> " + (h.cuddle ? McaIntegration.traitOf(v).cuddleHoldLine() : McaIntegration.traitOf(v).holdLine())));
        }
        if (p.getCamera() != v) p.setCamera(v); // pressing sneak would make a spectator stop looking through their eyes
        int level = levelOf(v);
        VillagerTrait trait = McaIntegration.traitOf(v);
        float scale = h.hostile ? 0f : h.forced == null ? 1f : h.forced.healScale(); // a villager who did not ask for you heals you less, or not at all
        if (h.ticks % 20 == 0 && scale > 0f) {
            float before = p.getHealth();
            p.heal(healPerSecond(level, trait, rankOf(v, VillagerPerk.SOOTHING)) * scale);
            h.healed += Math.max(0f, p.getHealth() - before);
            if (level >= 5 && scale >= 1f) p.getFoodData().eat(1, 0.2f); // a well-practised villager feeds you too
        }
        int warm = rankOf(v, VillagerPerk.WARM_BELLY);
        if (warm > 0 && scale >= 1f && h.ticks % 40 == 0) p.getFoodData().eat(warm, 0.1f * warm); // Warm Belly: every 2 seconds
        if (h.ticks % 400 == 300) { // now and then they say something
            String idle = h.hostile ? trait.hostileIdleLine() : h.forced != null ? h.forced.idleLine() : trait.holdIdleLine();
            p.sendSystemMessage(Component.literal("<").append(v.getName()).append("> " + idle));
        }
        int cuddleLimit = h.forced != null && h.forced.holdSeconds() > 0 ? h.forced.holdSeconds() * 20 : cuddleTicks(level);
        if (h.hostile) {
            int limit = GulpServerConfig.MCA_HOSTILE_HOLD_SECONDS.get() * 20;
            if (h.ticks >= limit) {
                if (lethalHostile(p)) {
                    digestPlayer(p, v, trait);
                } else {
                    p.sendSystemMessage(Component.literal("<").append(v.getName()).append("> " + trait.hostileEndLine()));
                    release(p, "They finally let you out.", false);
                }
            } else if ((h.ticks == limit / 2 || h.ticks == limit - 200) && lethalHostile(p)) {
                p.sendSystemMessage(Component.literal("<").append(v.getName()).append("> " + trait.hostileWarnLine()));
                p.sendSystemMessage(Component.literal("Time is running out: break free (Struggle key) or they will digest you!").withStyle(ChatFormatting.RED));
            }
        } else if (!h.cuddle && p.getHealth() >= p.getMaxHealth()) {
            release(p, "All better. You were let out.", true);
        } else if (h.cuddle && h.ticks >= cuddleLimit) {
            release(p, "The cuddle is over. You were let out.", true);
        } else if (h.ticks >= maxTicks(level, trait, rankOf(v, VillagerPerk.EMBRACE))) {
            release(p, "You were let out.", true);
        }
    }

    /** The Release key. Always works: this is a gentle rest, never a trap. */
    static void tryRelease(ServerPlayer p) {
        Hold h = HELD.get(p.getUUID());
        if (h != null && h.hostile && h.nestedIn == null) { // an angry villager: Release does not work, you have to struggle
            StomachLogic.say(p, Component.literal("They won't let you out. Struggle to try to break free!"));
            Entity e = p.serverLevel().getEntity(h.villager);
            if (e != null) p.sendSystemMessage(Component.literal("<").append(e.getName()).append("> " + McaIntegration.traitOf(e).hostileHoldRefuseLine()));
            return;
        }
        release(p, "You climbed out.", false);
    }

    /** The Struggle key: rub the walls. The villager is pleased (a few hearts), at most once every 20 seconds. */
    static void rub(ServerPlayer p) {
        Hold h = HELD.get(p.getUUID());
        if (h == null) return;
        if (h.rubCd > 0) {
            StomachLogic.say(p, Component.literal(h.hostile ? "Catch your breath before you struggle again." : "Give it a moment before you rub again."));
            return;
        }
        Entity e = p.serverLevel().getEntity(h.villager);
        if (h.hostile && h.nestedIn == null && e != null) { // struggling out of an angry villager, like struggling out of a player
            h.rubCd = 20;
            float chance = hostileChance(e);
            VillagerTrait trait = McaIntegration.traitOf(e);
            if (p.getRandom().nextFloat() * 100f < chance) {
                release(p, "You broke free!", false);
                p.sendSystemMessage(Component.literal("<").append(e.getName()).append("> " + trait.hostileEscapeLine()));
            } else {
                StomachLogic.say(p, Component.literal(String.format("You struggle, but can't break free (%.0f%% chance)", chance)));
                if (p.getRandom().nextInt(3) == 0) p.sendSystemMessage(Component.literal("<").append(e.getName()).append("> " + trait.hostileStruggleLine()));
            }
            return;
        }
        h.rubCd = RUB_COOLDOWN;
        if (h.forced != null && !h.forced.rewards()) { // they did not want you in there
            if (e != null) McaIntegration.addHearts(e, p, -2);
            StomachLogic.say(p, Component.literal("You squirm around in there. They really don't like that."));
            return;
        }
        if (e != null) McaIntegration.addHearts(e, p, 2);
        StomachLogic.say(p, Component.literal("You rub the walls gently. They seem pleased."));
    }

    /**
     * Lets the player out and gives them their game mode back, putting them down in front of the villager.
     * @param thanked true when it ended on its own (healed or time up): the villager is pleased with that
     */
    static void release(ServerPlayer p, String message, boolean thanked) {
        Hold h = HELD.remove(p.getUUID());
        if (h == null) return;
        if (!thanked) McaIntegration.pauseComfort(p, GulpServerConfig.MCA_COMFORT_DECLINE_PAUSE_SECONDS.get() * 20); // you climbed out: leave you be
        Entity villager = p.serverLevel().getEntity(h.villager);
        GameType mode = GameType.byId(h.previousMode);
        if (mode == GameType.SPECTATOR) mode = GameType.SURVIVAL; // never leave anyone stuck as a spectator
        p.setCamera(p);
        p.setGameMode(mode);
        p.getPersistentData().remove(PlayerHolding.KEY_PREV_MODE);
        if (h.nestedIn != null) leaveNest(p, h);
        if (villager != null && villager.level() instanceof ServerLevel w) {
            Vec3 fwd = villager.getViewVector(1.0f).scale(1.5);
            double x = villager.getX() + fwd.x;
            double y = villager.getY();
            double z = villager.getZ() + fwd.z;
            AABB box = p.getBoundingBox().move(x - p.getX(), y - p.getY(), z - p.getZ());
            if (!w.noCollision(box)) { // no room in front of them: use the spot they're standing on
                x = villager.getX();
                y = villager.getY();
                z = villager.getZ();
            }
            p.teleportTo(w, x, y, z, villager.getYRot() + 180f, 0f);
            if (thanked && (h.forced == null || h.forced.rewards())) {
                McaIntegration.addHearts(villager, p, h.cuddle ? 4 : 2);
                if (h.cuddle) addXp(villager, p, 6, true); // a cuddle teaches a little (and counts towards the daily cap)
                else awardXp(villager, p, h.healed);
                int wind = h.cuddle ? 0 : rankOf(villager, VillagerPerk.SECOND_WIND);
                if (wind > 0) { // Second Wind: a rest that ended well sends you off with Regeneration
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, VillagerPerk.secondWindTicks(wind), 0));
                    p.sendSystemMessage(Component.empty().append(villager.getName()).append(" gives you a second wind."));
                }
            }
        }
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HeldPacket(0, "", 0f, 0f));
        p.sendSystemMessage(Component.literal(message));
        if (villager != null && message.equals("You climbed out.")) {
            VillagerTrait trait = McaIntegration.traitOf(villager);
            p.sendSystemMessage(Component.literal("<").append(villager.getName()).append("> " + (h.forced != null ? h.forced.exitLine() : trait.earlyExitLine())));
        } else if (villager != null && thanked) {
            VillagerTrait trait = McaIntegration.traitOf(villager);
            p.sendSystemMessage(Component.literal("<").append(villager.getName()).append("> " + (h.forced != null ? h.forced.endLine()
                    : h.cuddle ? trait.cuddleFinishLine() : trait.finishLine())));
        }
    }

    // ------------------------------------------------------------------ russian doll: a villager swallowed while holding players

    /** The online players this villager is holding right now (not counting ones already inside a stomach with them). */
    static List<ServerPlayer> heldBy(Entity villager, MinecraftServer server) {
        List<ServerPlayer> list = new ArrayList<>();
        if (server == null) return list;
        for (Map.Entry<UUID, Hold> en : HELD.entrySet()) {
            Hold h = en.getValue();
            if (h.nestedIn != null || !h.villager.equals(villager.getUUID())) continue;
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp != null && sp.isAlive()) list.add(sp);
        }
        return list;
    }

    /** `a` stays held by the villager, who has just been swallowed by `b`: from now on `a` looks through b's eyes. */
    static void nest(ServerPlayer a, LivingEntity villager, ServerPlayer b, float size) {
        Hold h = HELD.get(a.getUUID());
        if (h == null) return;
        h.nestedIn = b.getUUID();
        h.nestedSize = size;
        h.playerName = a.getName().getString();
        h.cLevel = levelOf(villager);
        h.cTrait = McaIntegration.traitOf(villager);
        h.cSoothing = rankOf(villager, VillagerPerk.SOOTHING);
        h.cEmbrace = rankOf(villager, VillagerPerk.EMBRACE);
        h.cWarm = rankOf(villager, VillagerPerk.WARM_BELLY);
        a.setCamera(b);
        a.sendSystemMessage(Component.empty().append(villager.getName()).append(" was swallowed by ").append(b.getName())
                .append(", with you still inside them! The healing is paused until they are let out. Press your Release key (default G) to climb out of both.")); 
        a.sendSystemMessage(Component.literal("<").append(villager.getName()).append("> " + McaIntegration.traitOf(villager).swallowedWhileHoldingLine()));
    }

    /**
     * The villager in this stomach entry is being digested with players inside it: each of them is passed on into the swallower's
     * stomach as a soft held player (see PlayerHolding.adopt). The new entries go to StomachLogic.PENDING_ADD and the caller adds
     * them once it is done looping over the stomach. The villager's own entry has already been removed, which frees its room.
     */
    static void transferNested(ServerPlayer b, CompoundTag entry) {
        MinecraftServer server = b.getServer();
        UUID villagerId = entityUuid(entry);
        ListTag list = entry.getList("NestedPlayers", Tag.TAG_STRING);
        String villagerName = entry.getString("Name");
        for (int i = 0; i < list.size(); i++) {
            UUID u;
            try { u = UUID.fromString(list.getString(i)); } catch (IllegalArgumentException ex) { continue; }
            Hold h = HELD.get(u);
            ServerPlayer a = server == null ? null : server.getPlayerList().getPlayer(u);
            if (h == null || a == null || !a.isAlive() || h.nestedIn == null || !h.nestedIn.equals(b.getUUID())
                    || villagerId == null || !h.villager.equals(villagerId)) continue;
            HELD.remove(u); // no longer held by the villager
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> a), new HeldPacket(0, "", 0f, 0f));
            StomachLogic.PENDING_ADD.add(PlayerHolding.adopt(b, a, h.previousMode));
            a.sendSystemMessage(Component.literal(villagerName + " was digested, and you slipped into " + b.getName().getString()
                    + "'s stomach. Press your Release key (default G) to get out whenever you like."));
            b.sendSystemMessage(Component.literal(a.getName().getString() + " slipped out of the digested villager and into your stomach."));
        }
        entry.remove("NestedPlayers");
        entry.remove("NestedExtra");
    }

    private static UUID entityUuid(CompoundTag entry) {
        CompoundTag ent = entry.getCompound("Entity");
        return ent.hasUUID("UUID") ? ent.getUUID("UUID") : null;
    }

    /** The names of the players who are, right now, inside the villager in this stomach entry. */
    private static List<String> nestedNames(CompoundTag entry) {
        List<String> names = new ArrayList<>();
        ListTag list = entry.getList("NestedPlayers", Tag.TAG_STRING);
        if (list.isEmpty()) return names;
        UUID villager = entityUuid(entry);
        for (int i = 0; i < list.size(); i++) {
            try {
                Hold h = HELD.get(UUID.fromString(list.getString(i)));
                if (h != null && h.nestedIn != null && h.villager.equals(villager)) names.add(h.playerName);
            } catch (IllegalArgumentException ignored) {
                // not a UUID: skip it
            }
        }
        return names;
    }

    /** True if somebody is inside the villager in this stomach entry (such a villager is never digested). */
    static boolean hasNested(CompoundTag entry) {
        return !nestedNames(entry).isEmpty();
    }

    /** Nobody is inside this villager any more (they climbed out, or the server restarted): give the stomach their room back. */
    static void clearNested(CompoundTag entry) {
        entry.putFloat("Size", Math.max(0.05f, entry.getFloat("Size") - entry.getFloat("NestedExtra")));
        entry.remove("NestedPlayers");
        entry.remove("NestedExtra");
    }

    /** " (Steve player)" / " (Steve, Alex players)" to show after the villager's name in the stomach, or "" if nobody is inside. */
    static String nestedLabel(CompoundTag entry) {
        List<String> names = nestedNames(entry);
        if (names.isEmpty()) return "";
        return " (" + String.join(", ", names) + (names.size() == 1 ? " player)" : " players)");
    }

    private static boolean villagerStillInside(MinecraftServer server, ServerPlayer b, Hold h) {
        for (CompoundTag e : StomachManager.get(server).of(b.getUUID()).contents) {
            if (h.villager.equals(entityUuid(e))) return true;
        }
        return false;
    }

    /** Every tick while the villager holding `p` is inside another player's stomach: carries on healing from the remembered stats. */
    private static void nestedTick(ServerPlayer p, Hold h) {
        MinecraftServer server = p.getServer();
        ServerPlayer b = server == null ? null : server.getPlayerList().getPlayer(h.nestedIn);
        if (b == null || !b.isAlive() || b.isSpectator() || !villagerStillInside(server, b, h)) {
            release(p, "You were let out.", false);
            return;
        }
        if (b.level() != p.level()) p.teleportTo(b.serverLevel(), b.getX(), b.getY(), b.getZ(), b.getYRot(), 0f);
        if (p.getCamera() != b) p.setCamera(b);
        // Paused: no healing, no feeding and h.ticks does not advance, so the heal / cuddle timers carry on from here
        // when the villager is let out again. The player can still leave at any time with Release.
    }

    /** The player left the villager while the villager was inside someone's stomach: take their share out of that stomach entry. */
    private static void leaveNest(ServerPlayer p, Hold h) {
        MinecraftServer server = p.getServer();
        if (server == null || h.nestedIn == null) return;
        StomachManager mgr = StomachManager.get(server);
        Stomach s = mgr.of(h.nestedIn);
        boolean changed = false;
        for (CompoundTag entry : s.contents) {
            ListTag list = entry.getList("NestedPlayers", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                if (list.getString(i).equals(p.getUUID().toString())) {
                    list.remove(i);
                    entry.putFloat("Size", Math.max(0.05f, entry.getFloat("Size") - h.nestedSize));
                    entry.putFloat("NestedExtra", Math.max(0f, entry.getFloat("NestedExtra") - h.nestedSize));
                    changed = true;
                    break;
                }
            }
        }
        ServerPlayer b = server.getPlayerList().getPlayer(h.nestedIn);
        if (changed) {
            mgr.setDirty();
            if (b != null) {
                b.sendSystemMessage(Component.literal(p.getName().getString() + " climbed out of the villager inside you."));
                StomachLogic.sync(b, s);
            }
        }
        if (b != null) p.teleportTo(b.serverLevel(), b.getX(), b.getY(), b.getZ(), b.getYRot(), 0f); // out beside the swallower
    }

    /**
     * Fallback when nesting is switched off (nestedSwallowEnabled) or doesn't apply: someone is about to swallow a villager who is
     * holding players (it is still in the world). The players inside tumble out beside
     * it, safe and sound (they only agreed to be held by the villager, so they are not passed on into a second stomach), the
     * swallower is told, and the villager says something about it.
     */
    static void onVillagerSwallowed(Entity villager, ServerPlayer swallower) {
        List<UUID> inside = new ArrayList<>();
        for (Map.Entry<UUID, Hold> en : HELD.entrySet()) {
            if (en.getValue().villager.equals(villager.getUUID())) inside.add(en.getKey());
        }
        if (inside.isEmpty() || swallower.getServer() == null) return;
        String line = McaIntegration.traitOf(villager).swallowedWhileHoldingLine();
        String villagerName = villager.getName().getString();
        for (UUID id : inside) {
            ServerPlayer held = swallower.getServer().getPlayerList().getPlayer(id);
            if (held == null) {
                HELD.remove(id);
                continue;
            }
            release(held, villagerName + " was swallowed by " + swallower.getName().getString() + " with you inside! You tumble out, safe and sound.", false);
            held.sendSystemMessage(Component.literal("<" + villagerName + "> " + line));
            swallower.sendSystemMessage(Component.literal("You swallowed " + villagerName + ", and " + held.getName().getString()
                    + " tumbles out of their tummy beside you!"));
        }
        swallower.sendSystemMessage(Component.literal("<" + villagerName + "> " + line));
    }

    /** The server is stopping: let everyone out first. */
    static void releaseAll(MinecraftServer server) {
        for (UUID id : new ArrayList<>(HELD.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) release(p, "You were let out.", false);
        }
        HELD.clear();
    }
}
