package com.example.gulp;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minecraft Comes Alive (MCA Reborn) reactions. Optional: if MCA isn't installed, none of this does anything.
 *
 * MCA is reached by name (reflection) instead of being a build dependency, so this mod builds and runs the same
 * with or without it, and if a future MCA update changes something, the worst case is that the reactions switch
 * themselves off (with one line in the log) instead of crashing anything.
 *
 * What happens:
 *  - Swallowing an MCA villager changes how that villager feels about you (MCA "hearts" + mood), and nearby villagers
 *    who saw it react too. Some villagers enjoy being carried SAFELY (see attitude()): they gain hearts and happiness.
 *  - You get a "predator" status that grows when you swallow/digest villagers and slowly fades with time.
 *  - While it is high, villagers who are afraid of you refuse to talk, trade or marry you. Villagers who dislike you
 *    personally refuse too while the status lasts. When it fades, everyone talks to you again.
 *  - Witnesses panic and run from you for a few seconds (MCA villagers don't talk while panicking).
 *  - When a villager is digested, MCA's own death rules also apply (it already makes witnesses lose hearts).
 */
public final class McaIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KIND_KEY = "gulp_kind"; // saved under the villager (VillagerData): Pred = true once they were asked to become one

    private static final int WITNESS_RANGE = 16;
    private static final int MAX_WITNESSES = 12;
    private static final int MAX_PREDATOR = 200;

    private enum Attitude { FAN, NEUTRAL, FEARFUL }

    /**
     * Whether a villager is a pred, a prey or just normal. Worked out from their UUID, so it never changes and needs nothing saved.
     * Together with their MCA personality (Attitude: confident / easygoing / shy) that gives every villager a kind, like "pred, shy".
     * The mix: about 1 in 4 is a pred, 1 in 4 is a prey and the other half are normal (see voreTypeOf).
     *  NORMAL  villagers act exactly as they did before this existed.
     *  PREY    villagers are more open to being carried: even shy ones can ask, once they trust you enough.
     *  PRED    villagers never ask to be carried (they would rather do the carrying; eating the player comes later).
     */
    private enum VoreType { NORMAL, PREY, PRED }

    private static Boolean modLoaded;
    private static boolean broken; // MCA looked different from what we expected: stop trying
    private static Class<?> villagerClass;
    private static Method getVillagerBrain, getMemoriesForPlayer, getHearts, rewardHearts, getPersonality, isGuard;

    private record Scare(LivingEntity villager, UUID player, long until) { }

    private static final List<Scare> SCARED = new ArrayList<>();
    private static final Map<UUID, Long> LAST_REFUSAL_MESSAGE = new HashMap<>();

    private McaIntegration() { }

    // ------------------------------------------------------------------ finding MCA

    /** True if MCA is installed, working, and switched on in the server config. */
    public static boolean active() {
        if (!GulpServerConfig.MCA_ENABLED.get()) return false;
        if (modLoaded == null) modLoaded = ModList.get().isLoaded("mca");
        return modLoaded && !broken && ensureReflection();
    }

    /** A one-line chat message saying whether this mod is connected to MCA (shown on join; see mcaStatusMessage). */
    public static Component status() {
        if (!GulpServerConfig.MCA_ENABLED.get()) {
            return Component.literal("[Gulp] MCA: reactions are turned off in the server config.").withStyle(ChatFormatting.GRAY);
        }
        if (modLoaded == null) modLoaded = ModList.get().isLoaded("mca");
        if (!modLoaded) {
            return Component.literal("[Gulp] MCA: not found (Minecraft Comes Alive isn't installed).").withStyle(ChatFormatting.GRAY);
        }
        if (active()) {
            return Component.literal("[Gulp] MCA connected: villager reactions are on.").withStyle(ChatFormatting.GREEN);
        }
        return Component.literal("[Gulp] MCA found, but it didn't match what I expected, so reactions are off (see latest.log).")
                .withStyle(ChatFormatting.RED);
    }

    /**
     * Finds one of MCA's classes by its normal name (net.mca...). MCA's Forge build renames its classes with a
     * "forge." in front (forge.net.mca...), so that spelling is tried too. Used here and in McaSkins.
     */
    static Class<?> mcaClass(String name) throws ClassNotFoundException {
        // MCA 7.7.1 and newer moved its 1.20.1 classes from net.mca to net.conczin.mca; older ones may have the "forge." prefix.
        String moved = name.replaceFirst("^net\\.mca\\.", "net.conczin.mca.");
        String[] candidates = { name, "forge." + name, moved, "forge." + moved };
        ClassNotFoundException last = null;
        for (String candidate : candidates) {
            try {
                return Class.forName(candidate);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        throw last;
    }

    private static boolean ensureReflection() {
        if (villagerClass != null) return true;
        try {
            villagerClass = mcaClass("net.mca.entity.VillagerEntityMCA");
            getVillagerBrain = villagerClass.getMethod("getVillagerBrain");
            Class<?> brainClass = mcaClass("net.mca.entity.ai.brain.VillagerBrain");
            getMemoriesForPlayer = method(brainClass, "getMemoriesForPlayer", 1);
            rewardHearts = method(brainClass, "rewardHearts", 2);
            getPersonality = method(brainClass, "getPersonality", 0);
            getHearts = method(mcaClass("net.mca.entity.ai.Memories"), "getHearts", 0);
            try {
                isGuard = method(villagerClass, "isGuard", 0);
            } catch (NoSuchMethodException ignored) {
                isGuard = null; // optional
            }
            LOGGER.info("Vore Mod: MCA found, villager reactions are ready.");
            return true;
        } catch (Throwable t) {
            broken = true;
            villagerClass = null;
            LOGGER.warn("Vore Mod: MCA is installed but its code isn't what I expected, so the MCA reactions are switched off.", t);
            return false;
        }
    }

    /** Logs a problem with the full error, but only the first few times so a repeating failure can't flood the log. */
    private static int problemsLogged;

    private static void problem(String what, Throwable t) {
        if (problemsLogged++ < 5) LOGGER.warn("Vore Mod: MCA reaction problem: {}", what, t);
    }

    private static Method method(Class<?> c, String name, int parameters) throws NoSuchMethodException {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameters) return m;
        }
        throw new NoSuchMethodException(c.getName() + "." + name);
    }

    public static boolean isVillager(Entity e) {
        return e != null && villagerClass != null && villagerClass.isInstance(e);
    }

    /** A living, adult MCA villager (checked every time: the age is read from MCA itself). Only these ever earn XP. */
    static boolean isAdultVillager(Entity e) {
        return active() && isVillager(e) && e instanceof LivingEntity le && le.isAlive() && !isProtectedChild(e);
    }

    /** The villager's hearts with this player (0 if it can't be read). */
    static int heartsOf(Entity villager, Player player) {
        return heartsWith(villager, player);
    }

    // ------------------------------------------------------------------ reading a villager

    private static Object brainOf(Entity villager) throws ReflectiveOperationException {
        return getVillagerBrain.invoke(villager);
    }

    private static int heartsWith(Entity villager, Player player) {
        try {
            Object memories = getMemoriesForPlayer.invoke(brainOf(villager), player);
            return (Integer) getHearts.invoke(memories);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String personalityOf(Entity villager) {
        try {
            Object personality = getPersonality.invoke(brainOf(villager));
            return personality instanceof Enum<?> e ? e.name() : "";
        } catch (Throwable t) {
            problem("couldn't read a villager's personality", t);
            return "";
        }
    }

    private static VoreType voreTypeOf(Entity villager) {
        if (VillagerData.getFlag(villager, KIND_KEY, "Pred")) return VoreType.PRED; // asked to become a pred (askConvert)
        return switch (Math.floorMod(villager.getUUID().hashCode() * 31 + 7, 4)) {
            case 0 -> VoreType.PRED;
            case 1 -> VoreType.PREY;
            default -> VoreType.NORMAL; // 2 and 3: half of all villagers
        };
    }

    /**
     * A pred or prey villager's temper (shy or confident); NONE for normal villagers. Taken from their MCA personality,
     * and for the personalities in between from their UUID, so it never changes and needs nothing saved.
     */
    static VillagerTrait traitOf(Entity villager) {
        if (voreTypeOf(villager) == VoreType.NORMAL) return VillagerTrait.NONE;
        return switch (attitude(villager)) {
            case FAN -> VillagerTrait.CONFIDENT;
            case FEARFUL -> VillagerTrait.SHY;
            case NEUTRAL -> ((villager.getUUID().getLeastSignificantBits() >>> 20) & 1L) == 0L
                    ? VillagerTrait.CONFIDENT : VillagerTrait.SHY;
        };
    }

    /** 0 normal, 1 prey, 2 pred: the numbers VillagerInfoPacket and VillagerPerk.forType use. */
    static int typeOf(Entity villager) {
        return switch (voreTypeOf(villager)) {
            case NORMAL -> 0;
            case PREY -> 1;
            case PRED -> 2;
        };
    }

    /** Whether this villager is a pred type (only preds hold and comfort players). */
    static boolean isPredVillager(Entity villager) {
        return voreTypeOf(villager) == VoreType.PRED;
    }

    /** "pred, shy" / "prey, confident" / "normal, easygoing": what kind of villager this is, for /gulp villagerinfo. */
    private static String voreKind(Entity villager) {
        VillagerTrait trait = traitOf(villager);
        if (trait != VillagerTrait.NONE) {
            return voreTypeOf(villager).name().toLowerCase(java.util.Locale.ROOT) + ", " + trait.label.toLowerCase(java.util.Locale.ROOT);
        }
        String temper = switch (attitude(villager)) {
            case FAN -> "confident";
            case FEARFUL -> "shy";
            case NEUTRAL -> "easygoing";
        };
        return voreTypeOf(villager).name().toLowerCase(java.util.Locale.ROOT) + ", " + temper;
    }

    private static boolean guard(Entity villager) {
        try {
            return isGuard != null && (Boolean) isGuard.invoke(villager);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * How a villager feels about being swallowed, from their MCA personality:
     *  FAN      (peppy, confident, athletic, odd): they find being carried safely a thrill.
     *  FEARFUL  (sensitive, shy, gloomy, grumpy): they hate it.
     *  NEUTRAL  (everyone else): they don't like it, but they're not terrified.
     */
    private static Attitude attitude(Entity villager) {
        return switch (personalityOf(villager)) {
            case "PEPPY", "CONFIDENT", "ATHLETIC", "ODD" -> Attitude.FAN;
            case "SENSITIVE", "SHY", "GLOOMY", "GRUMPY" -> Attitude.FEARFUL;
            default -> Attitude.NEUTRAL;
        };
    }

    /**
     * How a witness feels about what they saw. Pred and prey villagers use their Gulp temper (shy = frightened, confident = fan),
     * so even a villager whose MCA personality is in between reacts in character. Everyone else uses their MCA personality.
     */
    private static Attitude witnessAttitude(Entity villager) {
        if (voreTypeOf(villager) == VoreType.NORMAL) return attitude(villager);
        return traitOf(villager) == VillagerTrait.SHY ? Attitude.FEARFUL : Attitude.FAN;
    }

    /**
     * Writes what kind of villager this is into their memory of the player ("gulp.kind.pred.shy", "gulp.kind.prey.confident", ...),
     * once, the first time the player talks to them. MCA dialogue reads it with an ordinary memory condition, which is how
     * the "Tell me about yourself" page knows who is answering. Normal villagers get nothing (the dialogue has a default for them).
     */
    static void stampKind(Entity villager, ServerPlayer p) {
        VoreType type = voreTypeOf(villager);
        if (type == VoreType.NORMAL) return;
        String id = "gulp.kind." + type.name().toLowerCase(java.util.Locale.ROOT) + "."
                + (traitOf(villager) == VillagerTrait.SHY ? "shy" : "confident");
        if (memoryCount(villager, p.getUUID(), id) == 0) remember(villager, p.getUUID(), id);
    }

    // ------------------------------------------------------------------ an angry pred grabs you

    private static final Map<UUID, Long> HOSTILE_PLAYER_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> HOSTILE_VILLAGER_COOLDOWN = new HashMap<>();

    /**
     * Every 5 seconds: a pred villager within 5 blocks of a player (role Prey or Both) whose hearts for them are at or below
     * mcaLowAffectionHearts (shy preds: twice as low, they hold back more) may swallow them without asking. Confident preds
     * do it more readily. The hold never hurts: see VillagerHolding.holdHostile.
     */
    public static void tickHostile(MinecraftServer server) {
        if (!GulpServerConfig.MCA_HOSTILE_ENABLED.get() || !active()) return;
        long now = server.getTickCount();
        HOSTILE_PLAYER_COOLDOWN.values().removeIf(t -> t <= now);
        HOSTILE_VILLAGER_COOLDOWN.values().removeIf(t -> t <= now);
        StomachManager mgr = StomachManager.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator() || PlayerHolding.isHeld(p)
                    || HOSTILE_PLAYER_COOLDOWN.containsKey(p.getUUID())) continue;
            if (!mgr.of(p.getUUID()).role.canBeEaten()) continue;
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(5.0), McaIntegration::isVillager)) {
                if (!(e instanceof LivingEntity le) || !le.isAlive() || isProtectedChild(le) || !isAdultVillager(le)
                        || voreTypeOf(le) != VoreType.PRED || HOSTILE_VILLAGER_COOLDOWN.containsKey(le.getUUID())
                        || VillagerHolding.isFull(le)) continue;
                VillagerTrait trait = traitOf(le);
                int low = GulpServerConfig.MCA_LOW_AFFECTION_HEARTS.get();
                if (heartsWith(le, p) > (trait == VillagerTrait.SHY ? low * 2 : low)) continue;
                if (p.getRandom().nextFloat() > (trait == VillagerTrait.SHY ? 0.25f : 0.5f)) continue;
                if (VillagerHolding.holdHostile(p, le)) {
                    long until = now + GulpServerConfig.MCA_HOSTILE_COOLDOWN.get() * 20L;
                    HOSTILE_PLAYER_COOLDOWN.put(p.getUUID(), until);
                    HOSTILE_VILLAGER_COOLDOWN.put(le.getUUID(), until);
                    remember(le, p.getUUID(), "gulp.hostile");
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ Climb In, and asking a villager to become a pred

    /**
     * A villager's temper for the two features below: SHY or CONFIDENT, or NONE for an easygoing normal villager. Pred and prey
     * villagers use their Gulp trait; normal ones their MCA personality.
     */
    static VillagerTrait temperOf(Entity villager) {
        if (voreTypeOf(villager) != VoreType.NORMAL) return traitOf(villager);
        return switch (attitude(villager)) {
            case FAN -> VillagerTrait.CONFIDENT;
            case FEARFUL -> VillagerTrait.SHY;
            case NEUTRAL -> VillagerTrait.NONE;
        };
    }

    private static final Map<UUID, Long> FORCE_COOLDOWN = new HashMap<>();

    private static LivingEntity nearbyAdult(ServerPlayer p, int entityId) {
        if (!active()) return null;
        Entity e = p.level().getEntity(entityId);
        if (!(e instanceof LivingEntity le) || !isVillager(e) || !le.isAlive() || p.distanceToSqr(e) > 36.0) {
            StomachLogic.say(p, Component.literal("Get closer to the villager first."));
            return null;
        }
        if (!isAdultVillager(e) || isProtectedChild(e)) {
            StomachLogic.say(p, Component.literal("Only adult villagers can do this."));
            return null;
        }
        return le;
    }

    /**
     * Climb In (action 24): the player forces their way into an adult villager's tummy, whatever kind of villager they are.
     * The outcome comes from ForcedOutcome (type + temper). Heart losses are halved with a villager who already likes you a lot
     * (mcaAffectionHearts) and made worse with one who is cold to you (mcaLowAffectionHearts).
     */
    static void forceSwallow(ServerPlayer p, int entityId) {
        if (!GulpServerConfig.MCA_FORCE_SWALLOW_ENABLED.get()) {
            StomachLogic.say(p, Component.literal("Climbing into villagers is turned off on this server."));
            return;
        }
        LivingEntity le = nearbyAdult(p, entityId);
        if (le == null) return;
        if (PlayerHolding.isHeld(p) || VillagerHolding.isHeld(p) || p.isSpectator()) return;
        if (!StomachManager.get(p.getServer()).of(p.getUUID()).role.canBeEaten()) {
            StomachLogic.say(p, Component.literal("Your role can't be swallowed. Set it to Prey or Both in Settings first."));
            return;
        }
        long now = p.getServer().getTickCount();
        Long until = FORCE_COOLDOWN.get(p.getUUID());
        if (until != null && until > now) {
            StomachLogic.say(p, Component.literal("Give it a rest: you can climb in again in " + ((until - now + 19) / 20) + " seconds."));
            return;
        }
        VillagerTrait temper = temperOf(le);
        ForcedOutcome o = ForcedOutcome.of(typeOf(le), temper);
        if (!VillagerHolding.holdForced(p, le, o)) return; // full, or not possible: they said why
        FORCE_COOLDOWN.put(p.getUUID(), now + GulpServerConfig.MCA_FORCE_SWALLOW_COOLDOWN.get() * 20L);
        int hearts = o.hearts();
        if (hearts < 0) {
            int have = heartsWith(le, p);
            if (have >= GulpServerConfig.MCA_AFFECTION_HEARTS.get()) hearts = Math.round(hearts * 0.5f); // friends forgive
            else if (have <= GulpServerConfig.MCA_LOW_AFFECTION_HEARTS.get()) hearts = Math.round(hearts * 1.5f); // already cold to you
        }
        reward(le, p, hearts);
        remember(le, p.getUUID(), "gulp.forced");
        if (o.scare()) scare(le, p);
    }

    /**
     * Ask to be a Pred (action 25): a prey or normal villager who likes the player enough agrees to become a pred for good.
     * Needs mcaConvertHearts (x1.25 for shy villagers, x0.8 for confident ones). Their old perks are forgotten and the points
     * come back, because a pred has its own perk list.
     */
    static void askConvert(ServerPlayer p, int entityId) {
        if (!GulpServerConfig.MCA_CONVERT_ENABLED.get()) {
            StomachLogic.say(p, Component.literal("Asking villagers to become preds is turned off on this server."));
            return;
        }
        LivingEntity le = nearbyAdult(p, entityId);
        if (le == null) return;
        VillagerTrait temper = temperOf(le);
        if (voreTypeOf(le) == VoreType.PRED) {
            p.sendSystemMessage(Component.literal("<").append(le.getName()).append("> " + temper.convertAlreadyLine()));
            return;
        }
        int need = (int) Math.round(GulpServerConfig.MCA_CONVERT_HEARTS.get() * temper.heartsMult);
        int have = heartsWith(le, p);
        if (have < need) {
            p.sendSystemMessage(Component.literal("<").append(le.getName()).append("> " + temper.convertRefuseLine()));
            p.sendSystemMessage(Component.literal("They need " + need + " hearts with you to agree. You have " + have + ".").withStyle(ChatFormatting.GRAY));
            return;
        }
        VillagerHolding.resetPerks(le); // pred villagers have their own perks: the points come back
        CompoundTag kind = new CompoundTag();
        kind.putBoolean("Pred", true);
        VillagerData.put(le, KIND_KEY, kind);
        remember(le, p.getUUID(), "gulp.converted");
        reward(le, p, 5);
        p.sendSystemMessage(Component.literal("<").append(le.getName()).append("> " + temper.convertAcceptLine()));
        p.sendSystemMessage(Component.empty().append(le.getName()).append(" is a pred villager now! Their perk points were given back.")
                .withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(Component.literal("<").append(le.getName()).append("> " + temper.convertAfterLine()));
        p.playNotifySound(SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        if (le.level() instanceof ServerLevel w) {
            w.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + 1.0, le.getZ(), 12, 0.4, 0.5, 0.4, 0.0);
        }
        sendVillagerInfo(p, entityId); // the Perks screen updates itself
    }

    // ------------------------------------------------------------------ changing a villager

    /**
     * Changes how a villager feels about the player. MCA's own rewardHearts does the work: it changes the hearts,
     * moves their mood the same amount (so big losses make them "depressed", big gains "overjoyed"), shows the
     * happy/angry particles, and doubles the loss for sensitive villagers.
     */
    private static void reward(Entity villager, ServerPlayer player, int hearts) {
        int scaled = Math.round(hearts * GulpServerConfig.MCA_REACTION_MULTIPLIER.get().floatValue());
        if (scaled == 0) return;
        try {
            rewardHearts.invoke(brainOf(villager), player, scaled);
            LOGGER.info("Vore Mod: villager {} hearts {} for {}", villager.getName().getString(),
                    (scaled > 0 ? "+" : "") + scaled, player.getName().getString());
        } catch (Throwable t) {
            problem("couldn't change a villager's hearts", t);
        }
    }

    private static void addPredator(ServerPlayer p, Stomach s, int amount) {
        if (amount <= 0) return;
        int lock = GulpServerConfig.MCA_PREDATOR_LOCK.get();
        int before = s.predator;
        s.predator = Math.min(MAX_PREDATOR, s.predator + amount);
        if (before < lock && s.predator >= lock) {
            StomachLogic.say(p, Component.literal("Villagers are afraid of you now."));
        }
    }

    /** Makes a villager run from the player for a few seconds (not guards: they'd fight instead). */
    private static void scare(Entity villager, ServerPlayer player) {
        if (!GulpServerConfig.MCA_PANIC.get() || !(villager instanceof LivingEntity le) || guard(villager)) return;
        SCARED.add(new Scare(le, player.getUUID(), le.level().getGameTime() + 160)); // 8 seconds
    }

    private static void reactNearby(ServerPlayer p, Entity subject, boolean hard) {
        int count = 0;
        for (Entity w : p.level().getEntities(p, p.getBoundingBox().inflate(WITNESS_RANGE), e -> e != subject && isVillager(e))) {
            if (++count > MAX_WITNESSES) break;
            Attitude a = witnessAttitude(w);
            VoreType kind = voreTypeOf(w);
            remember(w, p.getUUID(), "gulp.witnessed.swallow");
            int hearts = hard
                    ? (a == Attitude.FAN ? -3 : a == Attitude.FEARFUL ? -15 : -10)   // they saw something terrifying
                    : (a == Attitude.FAN ? 5 : a == Attitude.FEARFUL ? -8 : -2);     // a safe ride: fun to some, upsetting to others
            // Preds are less shocked (it is what they do); preys picture themselves in the victim's place, but like a safe ride more.
            if (kind == VoreType.PRED) hearts += hard ? 4 : 2;
            else if (kind == VoreType.PREY) hearts += hard ? -4 : 3;
            reward(w, p, hearts);
            boolean calmPred = kind == VoreType.PRED && a == Attitude.FAN; // a confident pred does not panic at the sight of a swallow
            if ((hard && !calmPred) || a == Attitude.FEARFUL) scare(w, p);
        }
    }

    // ------------------------------------------------------------------ hooks called by the stomach

    /** A villager is about to be swallowed (it is still in the world, so what it feels is saved with it). */
    public static void onSwallow(ServerPlayer p, Stomach s, LivingEntity swallowed) {
        onSwallow(p, s, swallowed, false);
    }

    /** @param invited true when the villager asked to be carried: it is always soft, they gain hearts, and nobody is "a predator" for it */
    public static void onSwallow(ServerPlayer p, Stomach s, LivingEntity swallowed, boolean invited) {
        boolean on = active();
        if (!on || !isVillager(swallowed)) {
            // Help work out why nothing happened: say so when a villager-type mob is swallowed but we didn't react.
            if (swallowed.getType().toString().contains("villager")) {
                LOGGER.info("Vore Mod: swallowed {} ({}) but no MCA reaction: MCA reactions {}, is MCA villager: {}",
                        swallowed.getName().getString(), swallowed.getClass().getName(),
                        on ? "on" : "off (config off, MCA missing, or MCA didn't match)", isVillager(swallowed));
            }
            return;
        }
        boolean hard = s.isHard(s.organ()) && GulpServerConfig.HARD_MODE_ENABLED.get() && !invited;
        Attitude a = attitude(swallowed);
        if (invited) remember(swallowed, p.getUUID(), "gulp.invited");
        remember(swallowed, p.getUUID(), "gulp.swallowed");
        remember(swallowed, p.getUUID(), hard ? "gulp.swallowed.hard" : "gulp.swallowed.soft");
        LOGGER.info("Vore Mod: MCA villager {} swallowed (personality {}, attitude {}, {} mode)",
                swallowed.getName().getString(), personalityOf(swallowed), a, hard ? "hard" : "soft");
        int hearts = invited ? (a == Attitude.FAN ? 15 : 8)
                : hard
                ? (a == Attitude.FAN ? -10 : a == Attitude.FEARFUL ? -50 : -40)
                : (a == Attitude.FAN ? 15 : a == Attitude.FEARFUL ? -25 : -10);
        reward(swallowed, p, hearts);
        int predator = invited ? 0 : hard
                ? (a == Attitude.FAN ? 8 : a == Attitude.FEARFUL ? 20 : 15)
                : (a == Attitude.FAN ? 0 : a == Attitude.FEARFUL ? 5 : 3);
        addPredator(p, s, predator);
        reactNearby(p, swallowed, hard);
    }

    /** A villager is let out. The ones who enjoy it get happier the longer the ride was. */
    public static void onRelease(ServerPlayer p, Stomach s, Entity released, int heldTicks) {
        if (!active() || !isVillager(released)) return;
        if (!s.isHard(s.organ())) VillagerXp.onRide(p, released, heldTicks); // a soft ride teaches a villager something
        remember(released, p.getUUID(), "gulp.released");
        if (heldTicks >= 1200) remember(released, p.getUUID(), "gulp.long_ride"); // a ride of a minute or more
        if (!s.isHard(s.organ())) VillagerHolding.onPreyRide(p, released, heldTicks); // Snug Passenger and Parting Gift
        Attitude a = attitude(released);
        if (a == Attitude.FAN) {
            reward(released, p, Math.min(25, 5 + heldTicks / 200)); // +5, then +1 for every 10 seconds, up to +25
        } else if (!s.isHard(s.organ())) {
            reward(released, p, a == Attitude.FEARFUL ? 3 : 5);     // relief at being let out safely
        }
    }

    /** A villager is being digested. MCA's own death rules apply too; this adds the predator status and panic. */
    public static void onDigest(ServerPlayer p, Stomach s, LivingEntity digested) {
        if (!active() || !isVillager(digested)) return;
        Attitude a = attitude(digested);
        addPredator(p, s, a == Attitude.FAN ? 10 : a == Attitude.FEARFUL ? 35 : 25);
        for (Entity w : p.level().getEntities(p, p.getBoundingBox().inflate(WITNESS_RANGE), e -> e != digested && isVillager(e))) {
            if (!(voreTypeOf(w) == VoreType.PRED && witnessAttitude(w) == Attitude.FAN)) scare(w, p); // confident preds keep their nerve
            remember(w, p.getUUID(), "gulp.witnessed.digest"); // they never forget this one
        }
    }

    // ------------------------------------------------------------------ talking, trading and marrying

    /** @return why this villager refuses to deal with the player right now, or null if they're fine with it */
    public static Component refusal(ServerPlayer p, Entity target) {
        if (!active() || !GulpServerConfig.MCA_LOCKOUT_ENABLED.get() || !isVillager(target)) return null;
        Stomach s = StomachManager.get(p.getServer()).of(p.getUUID());
        if (s.predator <= 0) return null;
        if (s.predator >= GulpServerConfig.MCA_PREDATOR_LOCK.get() && attitude(target) != Attitude.FAN) {
            return Component.empty().append(target.getName()).append(" is too afraid of you to talk.");
        }
        if (heartsWith(target, p) <= GulpServerConfig.MCA_HEARTS_LOCK.get()) {
            return Component.empty().append(target.getName()).append(" wants nothing to do with you right now.");
        }
        return null;
    }

    /** Stops the "refuses to talk" message from repeating on every click. */
    public static boolean shouldTell(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long last = LAST_REFUSAL_MESSAGE.put(p.getUUID(), now);
        return last == null || now - last > 1500;
    }

    // ------------------------------------------------------------------ every 10 ticks: keep scared villagers running

    public static void tickScared(MinecraftServer server) {
        if (SCARED.isEmpty()) return;
        Iterator<Scare> it = SCARED.iterator();
        while (it.hasNext()) {
            Scare s = it.next();
            LivingEntity v = s.villager();
            if (v.isRemoved() || !v.isAlive() || v.level().getGameTime() > s.until()) {
                it.remove();
                continue;
            }
            try {
                ServerPlayer p = server.getPlayerList().getPlayer(s.player());
                Brain<?> brain = v.getBrain();
                if (p != null) brain.setMemoryWithExpiry(MemoryModuleType.HURT_BY_ENTITY, p, 40L); // what to run from
                brain.setActiveActivityIfPossible(Activity.PANIC);
            } catch (Throwable t) {
                it.remove();
            }
        }
    }

    // ================================================================== child protection (always on)

    private static final Map<Class<?>, Optional<Method>> AGE_METHODS = new ConcurrentHashMap<>();

    /**
     * True for anyone who must never be swallowed or digested: vanilla baby villagers and every MCA villager who isn't an
     * adult (baby, toddler, child and teen). This does NOT depend on mcaIntegrationEnabled: if MCA is installed the
     * protection is on. If MCA's age code can't be read, MCA villagers fall back to the plain "is a baby" check.
     */
    public static boolean isProtectedChild(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        if (le instanceof AbstractVillager && le.isBaby()) return true;
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(le.getType());
        if (key == null || !key.getNamespace().equals("mca")) return false;
        try {
            Optional<Method> getter = AGE_METHODS.computeIfAbsent(le.getClass(), c -> {
                try {
                    return Optional.of(c.getMethod("getAgeState"));
                } catch (NoSuchMethodException none) {
                    return Optional.empty();
                }
            });
            if (getter.isPresent() && getter.get().invoke(le) instanceof Enum<?> age) {
                switch (age.name()) {
                    case "BABY", "TODDLER", "CHILD", "TEEN" -> { return true; }
                    case "ADULT" -> { return false; }
                    default -> { }
                }
            }
        } catch (Throwable t) {
            problem("couldn't read a villager's age", t);
        }
        return le.isBaby();
    }

    /** Same check for someone already inside a stomach (an entry saved as NBT). The answer is remembered on the entry. */
    public static boolean isChildEntry(CompoundTag entry, ServerLevel level) {
        if (entry.getBoolean("ChildChecked")) return entry.getBoolean("ChildEntry");
        CompoundTag saved = entry.getCompound("Entity");
        String id = saved.getString("id");
        boolean child = false;
        if (id.startsWith("mca:") || id.equals("minecraft:villager") || id.equals("minecraft:wandering_trader")) {
            child = EntityType.create(saved, level).map(McaIntegration::isProtectedChild).orElse(false);
        }
        entry.putBoolean("ChildChecked", true);
        entry.putBoolean("ChildEntry", child);
        return child;
    }

    // ================================================================== memory

    private static final String MEMORY_TAG = "GulpMemory";
    private static final Map<Class<?>, Optional<Method>> MEMORY_GETTERS = new ConcurrentHashMap<>();
    private static boolean memoryProblemLogged;

    /**
     * Remembers something about a player. It is always written to the villager's own saved data, and (best effort) into
     * MCA's LongTermMemory under "<id>.<player uuid>", the same way MCA's dialogue "remember" action does, so MCA
     * dialogue and the MCA: Conversations add-on can read it with an ordinary "memory" condition. These memories never expire.
     * Ids used: gulp.swallowed, gulp.swallowed.soft, gulp.swallowed.hard, gulp.released, gulp.long_ride,
     * gulp.witnessed.swallow, gulp.witnessed.digest, and the villager's kind (see stampKind): gulp.kind.pred.shy,
     * gulp.kind.pred.confident, gulp.kind.prey.shy, gulp.kind.prey.confident.
     */
    static void remember(Entity villager, UUID player, String id) {
        if (!GulpServerConfig.MCA_MEMORY_ENABLED.get()) return;
        CompoundTag all = VillagerData.getCompound(villager, MEMORY_TAG);
        CompoundTag mine = all.getCompound(player.toString());
        mine.putInt(id, mine.getInt(id) + 1);
        mine.putLong(id + ".last", villager.level().getGameTime());
        all.put(player.toString(), mine);
        VillagerData.put(villager, MEMORY_TAG, all);
        try {
            Optional<Method> getter = MEMORY_GETTERS.computeIfAbsent(villager.getClass(), c -> {
                for (Method m : c.getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("LongTermMemory")) return Optional.of(m);
                }
                return Optional.empty();
            });
            if (getter.isEmpty()) throw new NoSuchMethodException("no LongTermMemory getter on " + villager.getClass().getName());
            Object memory = getter.get().invoke(villager);
            Method remember = null;
            for (Method m : memory.getClass().getMethods()) {
                if (m.getName().equals("remember") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) remember = m;
            }
            if (remember == null) throw new NoSuchMethodException("no remember(String) on " + memory.getClass().getName());
            remember.invoke(memory, id + "." + player);
        } catch (Throwable t) {
            if (!memoryProblemLogged) {
                memoryProblemLogged = true;
                LOGGER.warn("Vore Mod: couldn't write to MCA's long-term memory, so villagers only remember in the mod's own data. "
                        + "(MCA: Conversations won't see these memories.)", t);
            }
        }
        // Milestones the dialogue can tell apart from "once": a few rides, a regular passenger, a regular invited passenger.
        int times = mine.getInt(id);
        if (id.equals("gulp.swallowed") && times == 3) remember(villager, player, "gulp.swallowed.many");
        else if (id.equals("gulp.swallowed") && times == 10) remember(villager, player, "gulp.swallowed.lots");
        else if (id.equals("gulp.invited") && times == 3) remember(villager, player, "gulp.invited.many");
    }

    private static int memoryCount(Entity villager, UUID player, String id) {
        return VillagerData.getCompound(villager, MEMORY_TAG).getCompound(player.toString()).getInt(id);
    }

    // ================================================================== affection: requests and comments

    private static final String[] FAN_LINES = {
            "Whoa, it's so warm and cozy in here!", "Best ride in the whole village!",
            "Don't let me out yet, this is fun.", "I can hear your heartbeat. It's kind of soothing."};
    private static final String[] NEUTRAL_LINES = {
            "It's... surprisingly comfortable in here.", "I trust you, but please don't take too long.",
            "It's warm. Strange, but I don't mind.", "Thanks for keeping me safe in here."};
    private static final String[] FEARFUL_LINES = {
            "I'm still nervous, but I know you'd never hurt me.", "It's okay. I'm okay. I trust you.",
            "Can we go back out soon? ...but thank you for being gentle."};
    private static final String REGULAR_LINE = "I'm getting used to this, you know. You carry me often.";

    private record Offer(UUID villager, long expires) { }

    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    private static final Map<UUID, Long> VILLAGER_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> PLAYER_COOLDOWN = new HashMap<>();

    /**
     * Stored on a villager's entry when they are swallowed: how they feel (so they can comment while inside, even though
     * the villager itself isn't in the world) and a first comment timer.
     */
    static void tagEntry(CompoundTag entry, Entity villager, ServerPlayer p) {
        if (!active() || !isVillager(villager)) return;
        CompoundTag info = new CompoundTag();
        info.putString("Attitude", attitude(villager).name());
        info.putInt("Hearts", heartsWith(villager, p));
        info.putInt("Times", memoryCount(villager, p.getUUID(), "gulp.swallowed"));
        entry.put("McaInfo", info);
        entry.putInt("CommentIn", 600 + p.getRandom().nextInt(600)); // first comment after 30 to 60 seconds
    }

    /**
     * Called every tick for each villager inside a stomach. A villager who likes you enough sometimes says something about
     * being inside. Nobody chats while being digested in hard mode (only invited, safe rides are exempt from that).
     */
    public static void tickInside(ServerPlayer p, CompoundTag entry, boolean hardNow) {
        CompoundTag info = entry.getCompound("McaInfo");
        if (info.isEmpty() || !GulpServerConfig.MCA_AFFECTION_ENABLED.get()) return;
        if (hardNow && !entry.getBoolean("Safe")) return;
        if (info.getInt("Hearts") < GulpServerConfig.MCA_AFFECTION_HEARTS.get()) return;
        int left = entry.getInt("CommentIn") - 1;
        if (left > 0) {
            entry.putInt("CommentIn", left);
            return;
        }
        entry.putInt("CommentIn", 1200 + p.getRandom().nextInt(1200)); // the next one in 1 to 2 minutes
        String[] pool = switch (info.getString("Attitude")) {
            case "FAN" -> FAN_LINES;
            case "FEARFUL" -> FEARFUL_LINES;
            default -> NEUTRAL_LINES;
        };
        String line = info.getInt("Times") >= 3 && p.getRandom().nextInt(4) == 0
                ? REGULAR_LINE : pool[p.getRandom().nextInt(pool.length)];
        p.sendSystemMessage(Component.literal("<").append(StomachLogic.entryName(entry)).append("> " + line));
    }

    /** Every 5 seconds: a villager close to you who likes you enough may ask to be carried (safely: soft mode only). */
    public static void tickAffection(MinecraftServer server) {
        if (!GulpServerConfig.MCA_AFFECTION_ENABLED.get() || !active()) return;
        long now = server.getTickCount();
        OFFERS.values().removeIf(o -> o.expires() <= now);
        VILLAGER_COOLDOWN.values().removeIf(t -> t <= now);
        PLAYER_COOLDOWN.values().removeIf(t -> t <= now);
        StomachManager mgr = StomachManager.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || PlayerHolding.isHeld(p) || OFFERS.containsKey(p.getUUID())
                    || PLAYER_COOLDOWN.containsKey(p.getUUID())) continue;
            Stomach s = mgr.of(p.getUUID());
            if (!s.role.canEat() || !s.usesGulpSwallow() || (s.isHard(s.organ()) && GulpServerConfig.HARD_MODE_ENABLED.get())) continue;
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(6), McaIntegration::isVillager)) {
                if (!(e instanceof LivingEntity le) || !le.isAlive() || isProtectedChild(le)
                        || VILLAGER_COOLDOWN.containsKey(le.getUUID())) continue;
                Attitude a = attitude(le);
                VoreType type = voreTypeOf(le);
                if (type == VoreType.PRED) continue; // preds don't ask to be carried
                // a prey villager is always either shy or confident (see traitOf), and that decides how they ask
                if (type == VoreType.PREY) a = traitOf(le) == VillagerTrait.SHY ? Attitude.FEARFUL : Attitude.FAN;
                if (a == Attitude.FEARFUL && type != VoreType.PREY) continue; // shy villagers who aren't prey types never ask, as before
                int eager = VillagerHolding.rankOf(le, VillagerPerk.EAGER_RIDER); // Eager Rider: fewer hearts needed, asks more often
                int needHearts = Math.round(GulpServerConfig.MCA_AFFECTION_HEARTS.get() * (a == Attitude.FEARFUL ? 2 : 1) * (1f - 0.1f * eager)); // shy villagers need to trust you twice as much
                if (heartsWith(le, p) < needHearts) continue;
                if (p.getRandom().nextFloat() > (a == Attitude.FAN ? 0.5f : a == Attitude.FEARFUL ? 0.1f : 0.25f) * (1f + 0.35f * eager)) continue;
                float size = Math.max(0.05f, le.getBbWidth() * le.getBbWidth() * le.getBbHeight());
                if (size > s.capacityOf(s.organ()) || s.usedIn(s.organ()) + size > s.capacityOf(s.organ()) || s.contents.size() >= Stomach.MAX_MOBS) continue;
                makeOffer(p, le, a, memoryCount(le, p.getUUID(), "gulp.swallowed") > 0, now);
                break;
            }
        }
    }

    private static void makeOffer(ServerPlayer p, LivingEntity villager, Attitude a, boolean again, long now) {
        OFFERS.put(p.getUUID(), new Offer(villager.getUUID(), now + 600)); // 30 seconds to answer
        VILLAGER_COOLDOWN.put(villager.getUUID(), now + GulpServerConfig.MCA_REQUEST_COOLDOWN_SECONDS.get() * 20L);
        PLAYER_COOLDOWN.put(p.getUUID(), now + 1200); // at most one request per player per minute
        String text = a == Attitude.FAN
                ? (again ? "Hey! Could you carry me in your tummy again? That was so much fun!"
                         : "Hey! Could you carry me in your tummy for a bit? I'd love to try it!")
                : (again ? "I trust you... would you carry me safely in your stomach again?"
                         : "I trust you... would you carry me safely in your stomach for a little while?");
        MutableComponent accept = Component.literal("[Accept]").withStyle(Style.EMPTY
                .withColor(net.minecraft.ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gulp carry accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Carry them safely (soft mode)"))));
        MutableComponent decline = Component.literal("[Decline]").withStyle(Style.EMPTY
                .withColor(net.minecraft.ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gulp carry decline"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Politely say no"))));
        p.sendSystemMessage(Component.literal("<").append(villager.getName()).append("> " + text + " ")
                .append(accept).append(" ").append(decline));
        p.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
    }

    /** The player answered a villager's request (clicked a button or typed /gulp carry accept|decline). */
    public static void respondCarry(ServerPlayer p, boolean accept) {
        Offer offer = OFFERS.remove(p.getUUID());
        long now = p.getServer().getTickCount();
        if (offer == null || offer.expires() <= now) {
            p.sendSystemMessage(Component.literal("Nobody is waiting for an answer."));
            return;
        }
        Entity e = p.serverLevel().getEntity(offer.villager());
        if (!(e instanceof LivingEntity le) || !le.isAlive() || p.distanceToSqr(le) > 64.0) {
            p.sendSystemMessage(Component.literal("They've wandered off."));
            return;
        }
        if (!accept) {
            p.sendSystemMessage(Component.literal("<").append(le.getName()).append("> That's okay, maybe another time."));
            return;
        }
        Stomach s = StomachManager.get(p.getServer()).of(p.getUUID());
        if (!s.usesGulpSwallow()) {
            StomachLogic.say(p, StomachLogic.notGulpSwallowMessage(s));
            return;
        }
        if (!s.role.canEat() || (s.isHard(s.organ()) && GulpServerConfig.HARD_MODE_ENABLED.get())) {
            StomachLogic.say(p, Component.literal("Switch to soft mode (and a role that can swallow) first. Rides by invitation are always safe."));
            return;
        }
        // safe = true: this villager came in by invitation, so they can never be digested, whatever mode you switch to.
        if (StomachLogic.swallowLiving(p, s, le, true)) {
            StomachManager.get(p.getServer()).setDirty();
            StomachLogic.sync(p, s);
        }
    }

    // ------------------------------------------------------------------ comforting a hurt player

    private static final Map<UUID, Offer> COMFORT_OFFERS = new HashMap<>();
    private static final Map<UUID, Long> COMFORT_PLAYER_COOLDOWN = new HashMap<>();

    /** The MCA villager screen asked (action 20): tell that player this villager's type and comfort level. */
    static void sendVillagerInfo(ServerPlayer p, int entityId) {
        if (!active()) return;
        Entity e = p.level().getEntity(entityId);
        if (e == null || !isVillager(e) || p.distanceToSqr(e) > 32.0 * 32.0) return;
        int type = switch (voreTypeOf(e)) {
            case NORMAL -> 0;
            case PREY -> 1;
            case PRED -> 2;
        };
        int level = VillagerHolding.levelOf(e);
        int trait = traitOf(e).ordinal();
        int[] ranks = new int[VillagerPerk.values().length];
        for (VillagerPerk perk : VillagerPerk.values()) ranks[perk.ordinal()] = VillagerHolding.rankOf(e, perk);
        int points = VillagerHolding.pointsFree(e);
        net.minecraftforge.network.PacketDistributor.PacketTarget target = net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p);
        Net.CHANNEL.send(target, new VillagerInfoPacket(entityId, isAdultVillager(e), type, trait, ranks, points, level, VillagerHolding.xpOf(e), VillagerHolding.xpNeeded(level)));
    }

    /** " | comfort lv 3" for pred villagers (they are the ones who comfort), nothing for the rest. */
    private static String comfortNote(Entity villager) {
        return voreTypeOf(villager) == VoreType.PRED ? " | comfort lv " + VillagerHolding.levelOf(villager) : "";
    }

    /** Changes a villager's hearts with a player (used when a villager has held someone). */
    static void addHearts(Entity villager, ServerPlayer player, int hearts) {
        if (active() && isVillager(villager)) reward(villager, player, hearts);
    }

    /**
     * Every 5 seconds: a hurt player (under half health) whose role can be swallowed (Prey or Both), with a pred villager
     * within 6 blocks who likes them enough, is offered a rest in that villager's tummy. They have to press Accept.
     */
    public static void tickComfort(MinecraftServer server) {
        if (!GulpServerConfig.MCA_COMFORT_ENABLED.get() || !active()) return;
        long now = server.getTickCount();
        COMFORT_OFFERS.values().removeIf(o -> o.expires() <= now);
        COMFORT_PLAYER_COOLDOWN.values().removeIf(t -> t <= now);
        StomachManager mgr = StomachManager.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || PlayerHolding.isHeld(p) || COMFORT_OFFERS.containsKey(p.getUUID())
                    || COMFORT_PLAYER_COOLDOWN.containsKey(p.getUUID())) continue;
            if (p.getHealth() > p.getMaxHealth() * VillagerTrait.CONFIDENT.comfortHpFraction) continue; // only when hurt (confident preds offer first, at 60%)
            if (!mgr.of(p.getUUID()).role.canBeEaten()) continue;   // being swallowed is the player's own choice: Prey or Both
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(6), McaIntegration::isVillager)) {
                if (!(e instanceof LivingEntity le) || !le.isAlive() || isProtectedChild(le)
                        || voreTypeOf(le) != VoreType.PRED || VillagerHolding.isFull(le)) continue;
                VillagerTrait trait = traitOf(le);
                if (p.getHealth() > p.getMaxHealth() * trait.comfortHpFraction) continue; // shy preds wait until you are worse off
                // A hurt player is simply asked, straight away, by any pred who doesn't dislike them. Only a villager who really
                // doesn't like them (hearts at or below mcaLowAffectionHearts) refuses, and says so.
                if (heartsWith(le, p) <= GulpServerConfig.MCA_LOW_AFFECTION_HEARTS.get()) {
                    if (snub(p, le, trait, now)) break;
                    continue;
                }
                makeComfortOffer(p, le, trait, now);
                break;
            }
        }
    }

    private static final Map<UUID, Long> SNUB_COOLDOWN = new HashMap<>();

    /** A pred who doesn't like a hurt player says so instead of offering comfort (at most once every 3 minutes per villager). */
    private static boolean snub(ServerPlayer p, LivingEntity villager, VillagerTrait trait, long now) {
        SNUB_COOLDOWN.values().removeIf(t -> t <= now);
        if (SNUB_COOLDOWN.containsKey(villager.getUUID())) return false;
        SNUB_COOLDOWN.put(villager.getUUID(), now + 3600);
        p.sendSystemMessage(Component.literal("<").append(villager.getName()).append("> " + trait.snubLine()));
        return true;
    }

    /** No comfort offers for this player for this long (after they said no, or climbed out on purpose). */
    static void pauseComfort(ServerPlayer p, int ticks) {
        if (ticks <= 0 || p.getServer() == null) return;
        COMFORT_PLAYER_COOLDOWN.put(p.getUUID(), (long) p.getServer().getTickCount() + ticks);
    }

    private static void makeComfortOffer(ServerPlayer p, LivingEntity villager, VillagerTrait trait, long now) {
        COMFORT_OFFERS.put(p.getUUID(), new Offer(villager.getUUID(), now + 600)); // 30 seconds to answer
        String text = trait.comfortOfferLine();
        MutableComponent accept = Component.literal("[Accept]").withStyle(Style.EMPTY
                .withColor(net.minecraft.ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gulp comfort accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Rest safely until you're healed. You can climb out any time."))));
        MutableComponent decline = Component.literal("[Decline]").withStyle(Style.EMPTY
                .withColor(net.minecraft.ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gulp comfort decline"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("No thank you"))));
        p.sendSystemMessage(Component.literal("<").append(villager.getName()).append("> " + text + " ")
                .append(accept).append(" ").append(decline));
        p.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
    }

    /** The player answered a comfort offer (clicked a button or typed /gulp comfort accept|decline). */
    public static void respondComfort(ServerPlayer p, boolean accept) {
        Offer offer = COMFORT_OFFERS.remove(p.getUUID());
        long now = p.getServer().getTickCount();
        if (offer == null || offer.expires() <= now) {
            p.sendSystemMessage(Component.literal("Nobody is waiting for an answer."));
            return;
        }
        Entity e = p.serverLevel().getEntity(offer.villager());
        if (!(e instanceof LivingEntity le) || !le.isAlive() || p.distanceToSqr(le) > 64.0) {
            p.sendSystemMessage(Component.literal("They've wandered off."));
            return;
        }
        if (!accept) {
            pauseComfort(p, GulpServerConfig.MCA_COMFORT_DECLINE_PAUSE_SECONDS.get() * 20);
            p.sendSystemMessage(Component.literal("<").append(le.getName()).append("> Of course. I'm here if you change your mind."));
            return;
        }
        if (PlayerHolding.isHeld(p) || p.isSpectator()) return;
        if (!StomachManager.get(p.getServer()).of(p.getUUID()).role.canBeEaten()) {
            StomachLogic.say(p, Component.literal("Your role can't be swallowed. Set it to Prey or Both in Settings first."));
            return;
        }
        VillagerHolding.hold(p, le);
    }

    // ------------------------------------------------------------------ a cosy cuddle (the player is not hurt)

    private static final Map<UUID, Offer> CUDDLE_OFFERS = new HashMap<>();
    private static final Map<UUID, Long> CUDDLE_VILLAGER_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> CUDDLE_PLAYER_COOLDOWN = new HashMap<>();

    /**
     * Every 5 seconds: a pred villager who loves you may offer a cosy cuddle in their tummy even though you are not hurt. It is the
     * relationship talking, so it needs: hearts (1.5 times the comfort amount the first time, the plain amount once they have held or
     * carried you before, scaled by their temper), no bad history (a villager who saw you digest someone never offers), a player who
     * can be swallowed (Prey or Both), and a free spot in their tummy. Shy preds ask less often. The player must press Accept.
     */
    public static void tickCuddle(MinecraftServer server) {
        if (!GulpServerConfig.MCA_CUDDLE_ENABLED.get() || !active()) return;
        long now = server.getTickCount();
        CUDDLE_OFFERS.values().removeIf(o -> o.expires() <= now);
        CUDDLE_VILLAGER_COOLDOWN.values().removeIf(t -> t <= now);
        CUDDLE_PLAYER_COOLDOWN.values().removeIf(t -> t <= now);
        StomachManager mgr = StomachManager.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (!p.isAlive() || p.isSpectator() || PlayerHolding.isHeld(p) || CUDDLE_OFFERS.containsKey(id)
                    || COMFORT_OFFERS.containsKey(id) || OFFERS.containsKey(id) || CUDDLE_PLAYER_COOLDOWN.containsKey(id)) continue;
            if (p.getHealth() <= p.getMaxHealth() * VillagerTrait.CONFIDENT.comfortHpFraction) continue; // hurt players are offered comfort instead
            if (!mgr.of(id).role.canBeEaten()) continue; // being swallowed is the player's own choice: Prey or Both
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(6), McaIntegration::isVillager)) {
                if (!(e instanceof LivingEntity le) || !le.isAlive() || isProtectedChild(le) || voreTypeOf(le) != VoreType.PRED
                        || CUDDLE_VILLAGER_COOLDOWN.containsKey(le.getUUID()) || VillagerHolding.isFull(le)) continue;
                if (memoryCount(le, id, "gulp.witnessed.digest") > 0) continue; // they have seen what you do to villagers
                VillagerTrait trait = traitOf(le);
                boolean history = memoryCount(le, id, "gulp.comforted") + memoryCount(le, id, "gulp.cuddled")
                        + memoryCount(le, id, "gulp.swallowed") > 0;
                long need = Math.round(GulpServerConfig.MCA_AFFECTION_HEARTS.get() * trait.heartsMult * (history ? 1.0 : 1.5));
                if (heartsWith(le, p) < need) continue;
                if (p.getRandom().nextFloat() > (trait == VillagerTrait.CONFIDENT ? 0.3f : 0.1f)) continue;
                makeCuddleOffer(p, le, trait, now);
                break;
            }
        }
    }

    private static void makeCuddleOffer(ServerPlayer p, LivingEntity villager, VillagerTrait trait, long now) {
        CUDDLE_OFFERS.put(p.getUUID(), new Offer(villager.getUUID(), now + 600)); // 30 seconds to answer
        CUDDLE_VILLAGER_COOLDOWN.put(villager.getUUID(),
                now + Math.round(GulpServerConfig.MCA_REQUEST_COOLDOWN_SECONDS.get() * 20L * trait.cooldownMult * 2));
        CUDDLE_PLAYER_COOLDOWN.put(p.getUUID(), now + 12000); // at most one cuddle offer per player every ten minutes
        MutableComponent accept = Component.literal("[Accept]").withStyle(Style.EMPTY
                .withColor(net.minecraft.ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gulp cuddle accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("A cosy rest in their tummy. You can climb out any time."))));
        MutableComponent decline = Component.literal("[Decline]").withStyle(Style.EMPTY
                .withColor(net.minecraft.ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gulp cuddle decline"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("No thank you"))));
        p.sendSystemMessage(Component.literal("<").append(villager.getName()).append("> " + trait.cuddleOfferLine() + " ")
                .append(accept).append(" ").append(decline));
        p.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
    }

    /** The player answered a cuddle offer (clicked a button or typed /gulp cuddle accept|decline). */
    public static void respondCuddle(ServerPlayer p, boolean accept) {
        Offer offer = CUDDLE_OFFERS.remove(p.getUUID());
        long now = p.getServer().getTickCount();
        if (offer == null || offer.expires() <= now) {
            p.sendSystemMessage(Component.literal("Nobody is waiting for an answer."));
            return;
        }
        Entity e = p.serverLevel().getEntity(offer.villager());
        if (!(e instanceof LivingEntity le) || !le.isAlive() || p.distanceToSqr(le) > 64.0) {
            p.sendSystemMessage(Component.literal("They've wandered off."));
            return;
        }
        if (!accept) {
            p.sendSystemMessage(Component.literal("<").append(le.getName()).append("> No problem. The offer stands."));
            return;
        }
        if (PlayerHolding.isHeld(p) || p.isSpectator()) return;
        if (!StomachManager.get(p.getServer()).of(p.getUUID()).role.canBeEaten()) {
            StomachLogic.say(p, Component.literal("Your role can't be swallowed. Set it to Prey or Both in Settings first."));
            return;
        }
        VillagerHolding.hold(p, le, true);
    }

    /** /gulp villagers: every villager within 24 blocks (nearest first, at most 20) with their kind, hearts and distance. */
    public static void listNearby(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("[Gulp] villagers nearby:").withStyle(ChatFormatting.AQUA));
        if (!active()) {
            p.sendSystemMessage(Component.literal("- MCA integration is off or didn't match MCA (see latest.log).").withStyle(ChatFormatting.YELLOW));
            return;
        }
        List<Entity> found = new ArrayList<>(p.level().getEntities(p, p.getBoundingBox().inflate(24), McaIntegration::isVillager));
        if (found.isEmpty()) {
            p.sendSystemMessage(Component.literal("- No MCA villager within 24 blocks.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        found.sort(java.util.Comparator.comparingDouble((Entity v) -> p.distanceToSqr(v)));
        int shown = 0;
        for (Entity e : found) {
            if (++shown > 20) {
                p.sendSystemMessage(Component.literal("...and " + (found.size() - 20) + " more."));
                break;
            }
            p.sendSystemMessage(Component.literal("- ").append(e.getName()).append(
                    " | " + voreKind(e) + comfortNote(e) + " | hearts " + heartsWith(e, p) + " | " + Math.round(p.distanceTo(e)) + " blocks"
                    + (isProtectedChild(e) ? " | child" : "")));
        }
    }

    /** /gulp villagerinfo: tells you why the nearest villager isn't asking to be carried (or that they would). */
    public static void debugNearest(ServerPlayer p) {
        Stomach s = StomachManager.get(p.getServer()).of(p.getUUID());
        long now = p.getServer().getTickCount();
        List<String> why = new ArrayList<>();
        if (!GulpServerConfig.MCA_AFFECTION_ENABLED.get()) why.add("mcaAffectionEnabled is false in the config.");
        if (!active()) why.add("MCA integration is off or didn't match MCA (see latest.log).");
        if (!s.role.canEat()) why.add("Your role is " + s.role.label + ", which can't swallow.");
        if (!s.usesGulpSwallow()) why.add("Gulp isn't your swallowing system (Settings > Swallowing).");
        if (s.isHard(s.organ()) && GulpServerConfig.HARD_MODE_ENABLED.get()) why.add("You're in hard mode: requests only come in soft mode.");
        if (OFFERS.containsKey(p.getUUID())) why.add("A request is already waiting for your answer.");
        Long pc = PLAYER_COOLDOWN.get(p.getUUID());
        if (pc != null && pc > now) why.add("You had a request recently: wait " + (pc - now) / 20 + "s.");

        Entity nearest = null;
        double best = Double.MAX_VALUE;
        if (active()) {
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(8), McaIntegration::isVillager)) {
                double d = p.distanceToSqr(e);
                if (d < best) { best = d; nearest = e; }
            }
        }
        p.sendSystemMessage(Component.literal("[Gulp] villager check:").withStyle(ChatFormatting.AQUA));
        if (nearest == null) {
            why.add(active() ? "No MCA villager within 8 blocks." : "No villager check possible.");
        } else if (nearest instanceof LivingEntity le) {
            int hearts = heartsWith(le, p);
            int need = GulpServerConfig.MCA_AFFECTION_HEARTS.get();
            Attitude a = attitude(le);
            p.sendSystemMessage(Component.literal("Nearest: ").append(le.getName()).append(
                    " | personality " + personalityOf(le) + " (" + a + ") | kind " + voreKind(le) + comfortNote(le) + " | hearts " + hearts + " (needs " + need + ")"
                    + " | child: " + isProtectedChild(le)));
            p.sendSystemMessage(Component.literal("Trade prices with you: " + VillagerTrade.describe(hearts)));
            if (isProtectedChild(le)) why.add("They are a child: never asked, never swallowed.");
            VoreType type = voreTypeOf(le);
            if (type == VoreType.PRED) why.add("They are a pred type: preds don't ask to be carried.");
            if (a == Attitude.FEARFUL && type != VoreType.PREY) why.add("Shy villagers only ask if they are a prey type.");
            if (a == Attitude.FEARFUL) need *= 2; // shy prey need to trust you twice as much
            if (hearts < need) why.add("Hearts " + hearts + " is below " + need + ". Talk to them or give gifts, or lower mcaAffectionHearts.");
            Long vc = VILLAGER_COOLDOWN.get(le.getUUID());
            if (vc != null && vc > now) why.add("They asked recently: wait " + (vc - now) / 20 + "s.");
            float size = Math.max(0.05f, le.getBbWidth() * le.getBbWidth() * le.getBbHeight());
            if (size > s.capacityOf(s.organ()) || s.usedIn(s.organ()) + size > s.capacityOf(s.organ()) || s.contents.size() >= Stomach.MAX_MOBS) {
                why.add("They wouldn't fit in your stomach right now.");
            }
        }
        if (why.isEmpty()) {
            p.sendSystemMessage(Component.literal("Nothing is blocking a request. They ask on a random check every 5 seconds.").withStyle(ChatFormatting.GREEN));
        } else {
            for (String w : why) p.sendSystemMessage(Component.literal("- " + w).withStyle(ChatFormatting.YELLOW));
        }
    }
}
