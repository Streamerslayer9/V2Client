package com.example.gulp;

import java.util.ArrayList;
import java.util.List;

/**
 * The perks a villager can learn, bought on the MCA villager's Perks screen. Every kind of adult villager has its own list
 * (forType: 2 pred, 1 prey, 0 normal), and every level a villager gains after level 1 gives them one perk point (9 by level 10).
 * Each list has more ranks than points, so a villager has to choose, and only a few different perks fit at once: see slotsFor
 * (perk slots). Passive perks work all the time; active perks fire when something happens (Second Wind when a rest ends well,
 * Parting Gift when a ride ends). The ranks are saved on the villager itself (VillagerHolding, key "gulp_comfort"), so each
 * villager in each world has its own level, points and perks. Add a new perk at the END of this list, give it an effect and a
 * line in the screen. The ordinal is what goes over the network (action 21 and VillagerInfoPacket).
 */
public enum VillagerPerk {
    // ---- pred villagers: they hold and comfort you
    SOOTHING("Soothing Touch", "Heals you +0.5 HP every second per rank.", 3, false, 2),
    EMBRACE("Long Embrace", "Holds you +12 seconds longer per rank.", 3, false, 2),
    WARM_BELLY("Warm Belly", "Feeds you while holding you: more hunger per rank.", 3, false, 2),
    ROOMY_BELLY("Roomy Belly", "Holds one more player at once per rank.", 2, false, 2),
    SECOND_WIND("Second Wind", "When a rest ends well you get Regeneration for 5 seconds per rank.", 3, true, 2),
    // ---- prey villagers: they ride in your stomach
    EAGER_RIDER("Eager Rider", "Asks to be carried more often and needs fewer hearts to trust you.", 3, false, 1),
    SNUG_PASSENGER("Snug Passenger", "Likes you more after every safe ride: +3 hearts per rank.", 3, false, 1),
    PARTING_GIFT("Parting Gift", "After a ride of 30+ seconds they give you XP (once every 10 minutes).", 3, true, 1),
    // ---- normal villagers: neighbours who talk and trade
    HAGGLER("Haggler", "Gives you a better price when you trade: 5% off per rank.", 3, false, 0),
    CHATTY("Chatty", "Earns +1 XP per rank every time you talk to them.", 3, false, 0),
    WARM_WELCOME("Warm Welcome", "Likes you a little more every time you talk: +1 heart per rank.", 3, true, 0);

    public final String title;
    public final String description;
    public final int maxRank;
    /** True for a perk that fires on an event instead of working all the time. */
    public final boolean active;
    /** Which villagers can learn it: 2 pred, 1 prey, 0 normal (the same numbers as VillagerInfoPacket.type). */
    public final int forType;

    VillagerPerk(String title, String description, int maxRank, boolean active, int forType) {
        this.title = title;
        this.description = description;
        this.maxRank = maxRank;
        this.active = active;
        this.forType = forType;
    }

    public static VillagerPerk byIndex(int i) {
        VillagerPerk[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }

    /** The perks a villager of this kind (2 pred, 1 prey, 0 normal) can learn, in the order the screen shows them. */
    public static List<VillagerPerk> perksFor(int type) {
        List<VillagerPerk> list = new ArrayList<>();
        for (VillagerPerk p : values()) if (p.forType == type) list.add(p);
        return list;
    }

    /** Every rank of every perk of one kind added together. */
    public static int totalRanks(int type) {
        int total = 0;
        for (VillagerPerk p : perksFor(type)) total += p.maxRank;
        return total;
    }

    /** Extra HP per second at this rank of Soothing Touch. */
    public static float soothingBonus(int rank) { return 0.5f * rank; }

    /** How many different perks a villager of this level can have ranks in: 1 at levels 1-3, 2 at 4-7, 3 from level 8. */
    public static int slotsFor(int level) { return Math.min(3, 1 + Math.max(1, level) / 4); }

    /** How many players a villager can hold at once: 1, 2 from level 5, plus one per rank of Roomy Belly (never more than 6). */
    public static int capacityFor(int level, int roomyRank) { return Math.min(6, 1 + Math.max(1, level) / 5 + roomyRank); }

    /** How long the Regeneration from Second Wind lasts at this rank, in ticks. */
    public static int secondWindTicks(int rank) { return 5 * 20 * rank; }

    /** Extra holding time, in ticks, at this rank of Long Embrace. */
    public static int embraceBonusTicks(int rank) { return 12 * 20 * rank; }

    /** XP a Parting Gift gives at this rank after a ride of this many ticks (at least 30 seconds). */
    public static int partingGiftXp(int rank, int heldTicks) { return rank * Math.min(10, 2 + heldTicks / 600); }
}
