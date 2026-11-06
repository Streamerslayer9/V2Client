package com.example.gulp;

/**
 * The perks. Add a new one at the END of this list, give it an effect in StomachLogic and an icon in PerksScreen.
 * The game hands out exactly totalRanks() perk points by level 99 (see Stomach.cumulativePoints), so a max-level
 * player can buy every rank of every perk, with nothing left over and nothing missing. Add or change a perk's
 * ranks and the points adjust themselves.
 */
public enum Perk {
    ROOMY("Roomy Stomach", "+6% stomach capacity per rank.", 30),
    HEALING("Healing Stomach", "Soft mode: held mobs heal 1% max HP/rank every 5s.", 15),
    RICH("Better Loot", "Looting on digested mobs: +1 level per 2 ranks (max V).", 10),
    QUICK("Quick Gulp", "Swallow cooldown -0.075s/rank, -0.15s looking up.", 10),
    IRON("Iron Stomach", "Struggling mobs hurt 5% less per rank.", 15),
    GOURMET("Gourmet", "+5% XP from digesting mobs per rank.", 20),
    HEARTY("Hearty Meal", "Heal 0.5 HP per rank whenever you digest a mob.", 10),
    /** Iron Gut / Universal Digestion. Rank 0 is free and always active; each rank adds 0.5 hunger bars per 32 items. */
    UNIVERSAL("Iron Gut", "Digest any 32 items stored in your stomach to restore hunger.", 3),
    /** High-level perk. Storage screen: Strip gear into an enchanted book, or Transfer (soft mode) to another item. */
    STRIP("Enchant Stripper", "Strip enchantments off gear in your stomach into a book, or move them to another item (soft mode). Higher ranks lose fewer levels.", 3, 50),
    /** High-level perk. Slowly repairs gear and tools in the item storage. */
    MENDING("Mending Gut", "Gear and tools held in your stomach storage slowly repair themselves.", 5, 60),
    /** High-level perk. Smelts raw ores in the item storage into ingots over time; higher ranks give more ingots per ore. */
    SMELTING("Smelting Gut", "Raw ores in your stomach storage smelt into ingots over time. Higher ranks smelt faster and give extra ingots per ore.", 5, 70),
    /** Armor for every prey you carry: 0.5 armor per rank per prey (up to 8 prey). */
    ARMORED("Armored Gut", "The more prey you carry, the tougher you are: +0.5 armor per rank for each prey inside (up to 8).", 5, 40),
    /** Cock: prey struggling in it hurt you less (-20% per rank, none at rank 5); from rank 3 you also get Resistance while it holds prey. */
    COCK_WARD("Padded Sac", "Prey struggling in your cock hurt you 20% less per rank (not at all at rank 5). From rank 3 you also get Resistance while it holds prey.", 5, 50),
    /** Tail: +0.75 blocks of swallow reach per rank while the tail is the selected vore type. */
    TAIL_REACH("Long Tail", "Your tail swallows from further away: +0.75 blocks of reach per rank while the tail is selected.", 5, 25),
    /** Unbirth: digested mobs drop their spawn egg (40% / 70% / 100%). Without it they give their normal loot. */
    BROOD("Brood Mother", "Mobs digested in your unbirth can drop their spawn egg: 40% at rank 1, 70% at rank 2, always at rank 3.", 3, 60),
    /** Breast: lets the stored Breast Milk churn into Butter (rank 1), faster at ranks 2 and 3. */
    CHURN("Butter Churn", "Lets the Breast Milk in your breast churn into Butter. Rank 2 churns 25% faster, rank 3 twice as fast.", 3, 30);

    public final String title;
    public final String description;
    public final int maxRank;
    /** Stomach level needed before the first rank can be bought (1 = no requirement). */
    public final int minLevel;

    Perk(String title, String description, int maxRank) {
        this(title, description, maxRank, 1);
    }

    Perk(String title, String description, int maxRank, int minLevel) {
        this.title = title;
        this.description = description;
        this.maxRank = maxRank;
        this.minLevel = minLevel;
    }

    /** Every rank of every perk added together. */
    public static int totalRanks() {
        int total = 0;
        for (Perk p : values()) total += p.maxRank;
        return total;
    }

    public static Perk byIndex(int i) {
        Perk[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
