package com.example.gulp;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the client currently knows. Plain Java only (no client-only classes), so it's safe to load anywhere. */
public final class ClientState {
    private ClientState() { }

    /** One swallowed mob as the server describes it. */
    /** organ = an Organ id; transit = how far prey has got through the tail (0..1, only for the tail) */
    public record EntryInfo(int uid, Component name, String typeId, float size, float hp, float maxHp, float digest, int organ, float transit) { }

    private static boolean hard;
    private static boolean instantDigest;
    // While swallowed: 0 = not, 1 = soft, 2 = hard, plus who holds you, how digested you are and your escape chance.
    private static int heldMode;
    /** A swallowed player can look at the inside of the stomach (true) instead of through the swallower's eyes (false). */
    private static boolean insideView;
    private static String heldHolder = "";
    private static float heldProgress;
    private static float heldChance;
    private static Role role = Role.PRED;
    private static SwallowSystem swallowSystem = SwallowSystem.UNSET;
    /** Whether the SERVER has Flamevore. The server decides, so a modded client on a server without Flamevore just plays Gulp. */
    private static boolean serverFlamevore;
    /** True once the server has sent us the first stomach sync of this session. */
    private static boolean synced;
    /** Everyone's role, from the server (for the "point at a player" badge). */
    private static final java.util.Map<java.util.UUID, Role> roles = new java.util.HashMap<>();
    private static int predator;
    private static float itemFill;
    private static int digestProgress;
    private static net.minecraft.world.item.ItemStack strip0 = net.minecraft.world.item.ItemStack.EMPTY;
    private static net.minecraft.world.item.ItemStack strip1 = net.minecraft.world.item.ItemStack.EMPTY;
    private static int level = 1, xp, xpNeeded = 100, points;
    private static float used, cap = 1.75f;
    private static boolean multiOrgans;
    private static int breastMilk, breastButter;
    private static float breastChurn;
    private static int activeOrgan, hardMask;
    private static float[] organUsed = new float[Organ.COUNT];
    private static int[] ranks = new int[Perk.values().length];
    private static List<EntryInfo> entries = new ArrayList<>();

    public static void apply(SyncPacket m) {
        hard = m.hard;
        instantDigest = m.instantDigest;
        level = m.level;
        xp = m.xp;
        xpNeeded = m.xpNeeded;
        used = m.used;
        cap = m.cap;
        multiOrgans = m.multiOrgans;
        breastMilk = m.breastMilk;
        breastButter = m.breastButter;
        breastChurn = m.breastChurn;
        activeOrgan = m.activeOrgan;
        hardMask = m.hardMask;
        organUsed = m.organUsed;
        points = m.points;
        role = Role.byId(m.role) != null ? Role.byId(m.role) : Role.PRED;
        SwallowSystem system = SwallowSystem.byId(m.swallowSystem);
        swallowSystem = system != null ? system : SwallowSystem.UNSET;
        serverFlamevore = m.flamevoreOnServer;
        synced = true;
        predator = m.predator;
        itemFill = m.itemFill;
        digestProgress = m.digestProgress;
        strip0 = m.strip0;
        strip1 = m.strip1;
        ranks = m.ranks;
        entries = m.entries;
    }

    /** Set by client setup; shows the level-up chat message if the player has it turned on. */
    public static java.util.function.BiConsumer<Integer, Integer> levelUpListener;

    public static void fireLevelUp(int level, int points) {
        if (levelUpListener != null) levelUpListener.accept(level, points);
    }

    /** Shows an action-bar message on this client (unless the player turned them off). Set by client setup. */
    public static java.util.function.Consumer<Component> messageListener;

    public static void fireMessage(Component text) {
        if (messageListener != null) messageListener.accept(text);
    }

    /** Plays a Gulp sound on this client. Set by client setup (see ClientSounds). */
    public interface SoundListener { void play(int type, double x, double y, double z); }

    public static SoundListener soundListener;

    public static void firePlaySound(int type, double x, double y, double z) {
        if (soundListener != null) soundListener.play(type, x, y, z);
    }

    /** Forget everything (called when you leave a world/server so nothing carries over to the next one). */
    public static void reset() {
        hard = false;
        instantDigest = false;
        level = 1;
        xp = 0;
        xpNeeded = Stomach.xpForNext(1);
        points = 0;
        role = Role.PRED;
        swallowSystem = SwallowSystem.UNSET;
        serverFlamevore = false;
        synced = false;
        roles.clear();
        heldMode = 0;
        insideView = false;
        predator = 0;
        itemFill = 0f;
        digestProgress = 0;
        strip0 = net.minecraft.world.item.ItemStack.EMPTY;
        strip1 = net.minecraft.world.item.ItemStack.EMPTY;
        used = 0f;
        cap = 1.75f;
        multiOrgans = false;
        breastMilk = 0;
        breastButter = 0;
        breastChurn = 0f;
        activeOrgan = 0;
        hardMask = 0;
        organUsed = new float[Organ.COUNT];
        ranks = new int[Perk.values().length];
        entries = new ArrayList<>();
        BELLY_TARGET.clear();
        BELLY_SHOWN.clear();
        BELLY_TIME.clear();
    }

    public static boolean isHard() { return hard; }
    public static boolean isInstantDigest() { return instantDigest; }
    public static Role role() { return role; }
    public static SwallowSystem swallowSystem() { return swallowSystem; }

    /** True when Gulp does the swallowing for this player (always without Flamevore). Same rule as Stomach.usesGulpSwallow(). */
    public static boolean usesGulpSwallow() {
        return !serverFlamevore || swallowSystem == SwallowSystem.GULP;
    }

    /** True when the server has Flamevore, so there is a Gulp / Flamevore choice to make. */
    public static boolean flamevoreActive() { return serverFlamevore; }

    /** True when Flamevore is installed and this player hasn't picked a swallowing system yet (and the server has told us so). */
    public static boolean needsSwallowChoice() {
        return synced && serverFlamevore && swallowSystem == SwallowSystem.UNSET;
    }

    public static void setHeld(int mode, String holder, float progress, float chance) {
        if (mode != 0 && heldMode == 0) McaScreenHooks.closeOnHeld(); // just got swallowed: close the villager's menu
        if (mode == 0 || heldMode == 0) insideView = false; // a new swallow starts in the swallower's view
        heldMode = mode;
        heldHolder = holder;
        heldProgress = progress;
        heldChance = chance;
    }
    public static int heldMode() { return heldMode; }
    public static boolean insideView() { return heldMode > 0 && insideView; }
    public static void toggleInsideView() { if (heldMode > 0) insideView = !insideView; }
    public static String heldHolder() { return heldHolder; }
    public static float heldProgress() { return heldProgress; }
    public static float heldChance() { return heldChance; }

    /** Another player's role, or null if the server hasn't told us. */
    public static Role roleOf(java.util.UUID id) { return roles.get(id); }

    public static void setRoles(java.util.Map<java.util.UUID, Integer> map) {
        roles.clear();
        map.forEach((id, r) -> {
            Role role = Role.byId(r);
            if (role != null) roles.put(id, role);
        });
    }
    public static int predator() { return predator; }
    public static float itemFill() { return itemFill; }
    public static int digestProgress() { return digestProgress; }

    /** The Enchant Stripper boxes: 0 = source, 1 = target. */
    public static net.minecraft.world.item.ItemStack stripSlot(int i) { return i == 0 ? strip0 : strip1; }
    public static int level() { return level; }
    public static int xp() { return xp; }
    public static int xpNeeded() { return xpNeeded; }
    public static int points() { return points; }
    public static float used() { return used; }
    public static float cap() { return cap; }
    /** True when the server has Breast / Cock / Unbirth / Tail turned on. */
    public static boolean multiOrgans() { return multiOrgans; }

    /** The CPM animation names that tell us a model has a place for each organ (index = Organ id). The player's model brings the parts. */
    private static final String[][] ORGAN_MODEL_NAMES = {
            {}, {"breast", "breasts"}, {"cock", "balls"}, {"unbirth", "womb"}, {"tail", "tail_vore"}};

    /**
     * Can this player pick this organ? The stomach always. The others need a Custom Player Model that has the matching animation
     * (the model is on your own client, so this is checked here), unless requireCpmModel is off in gulp-client.properties.
     * An organ you are already swallowing into stays available, so you can always get back to it.
     */
    public static boolean organAvailable(int id) {
        if (id <= 0 || id >= Organ.COUNT || !GulpConfig.requireCpmModel || id == activeOrgan) return true;
        for (String name : ORGAN_MODEL_NAMES[id]) {
            if (CpmBridge.maxValue(name) >= 0) return true;
            String capital = Character.toUpperCase(name.charAt(0)) + name.substring(1); // CPM names are case-sensitive
            if (CpmBridge.maxValue(capital) >= 0) return true;
        }
        return false;
    }
    /** The breast storage: Breast Milk, Butter that is ready, and how far the next batch has churned (0..1). */
    public static int breastMilk() { return breastMilk; }
    public static int breastButter() { return breastButter; }
    public static float breastChurn() { return breastChurn; }
    /** The organ new prey goes into (an Organ id). */
    public static int activeOrgan() { return activeOrgan; }
    public static boolean isHard(int organ) { return (hardMask & (1 << organ)) != 0; }
    public static float usedIn(int organ) { return organ >= 0 && organ < organUsed.length ? organUsed[organ] : 0f; }
    public static int rank(Perk p) { return ranks[p.ordinal()]; }
    public static List<EntryInfo> entries() { return Collections.unmodifiableList(entries); }

    /** Changes whenever the screens need to rebuild their buttons. */
    public static String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(hard).append(instantDigest).append(activeOrgan).append(hardMask).append(breastMilk).append(',').append(breastButter).append('|').append(points).append('|').append(Arrays.toString(ranks)).append('|');
        for (EntryInfo e : entries) sb.append(e.uid()).append(':').append(e.organ()).append(',');
        return sb.toString();
    }

    // Belly fullness (0..1) for every player we can see, keyed by UUID.
    // Custom player models can read this too: ClientState.bellyAmount(uuid)
    private static final Map<UUID, Float> BELLY_TARGET = new HashMap<>();
    private static final Map<UUID, Float> BELLY_SHOWN = new HashMap<>();
    private static final Map<UUID, Long> BELLY_TIME = new HashMap<>();

    public static void setBelly(UUID id, float ratio) { BELLY_TARGET.put(id, ratio); }

    public static float bellyAmount(UUID id) { return BELLY_TARGET.getOrDefault(id, 0f); }

    /** Eases the displayed belly toward the real value so it grows/shrinks smoothly. */
    public static float smoothedBelly(UUID id) {
        float target = bellyAmount(id);
        float cur = BELLY_SHOWN.getOrDefault(id, 0f);
        // Ease toward the real value over about a third of a second, the same at any frame rate.
        long now = System.nanoTime();
        Long last = BELLY_TIME.put(id, now);
        float seconds = last == null ? 0.016f : Math.min(0.25f, (now - last) / 1.0e9f);
        cur += (target - cur) * (1f - (float) Math.exp(-seconds / 0.35f));
        if (Math.abs(target - cur) < 0.002f) cur = target;
        BELLY_SHOWN.put(id, cur);
        return cur;
    }
}
