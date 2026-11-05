package com.example.gulp;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server settings for multiplayer admins (config file: serverconfig/gulp-server.toml inside each world,
 * or the world's serverconfig folder on a dedicated server). Only the server reads these.
 */
public final class GulpServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue HARD_MODE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue STRUGGLE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue ITEM_STORAGE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue PLAYER_SWALLOWING_ENABLED;
    public static final ForgeConfigSpec.IntValue PLAYER_MAX_HOLD_SECONDS;
    public static final ForgeConfigSpec.BooleanValue PLAYER_HARD_VORE_ENABLED;
    public static final ForgeConfigSpec.IntValue PLAYER_DIGEST_SECONDS;
    public static final ForgeConfigSpec.DoubleValue PLAYER_STRUGGLE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue PLAYER_DIGESTION_KILLS;
    public static final ForgeConfigSpec.IntValue PLAYER_WALL_RUB_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue NESTED_WEIGHT_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue HAZARD_SELF_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue SHARED_STORAGE_VIEW_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ITEM_DIGESTION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TRASH_DISSOLVE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DECONSTRUCT_ENCHANTED;
    public static final ForgeConfigSpec.BooleanValue UNIVERSAL_DIGESTION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SMELTING_GUT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ARMORED_GUT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MCA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue MCA_REACTION_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue MCA_LOCKOUT_ENABLED;
    public static final ForgeConfigSpec.IntValue MCA_PREDATOR_LOCK;
    public static final ForgeConfigSpec.IntValue MCA_HEARTS_LOCK;
    public static final ForgeConfigSpec.IntValue MCA_PREDATOR_DECAY_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MCA_PANIC;
    public static final ForgeConfigSpec.BooleanValue MCA_MEMORY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MCA_AFFECTION_ENABLED;
    public static final ForgeConfigSpec.IntValue MCA_AFFECTION_HEARTS;
    public static final ForgeConfigSpec.IntValue MCA_REQUEST_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MCA_COMFORT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MCA_CUDDLE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue NESTED_SWALLOW_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MULTI_ORGANS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ORGAN_LOOT_ENABLED;
    public static final ForgeConfigSpec.IntValue BREAST_CHURN_SECONDS;
    public static final ForgeConfigSpec.IntValue TAIL_TRANSIT_SECONDS;
    public static final ForgeConfigSpec.IntValue UNBIRTH_NAUSEA_MAX_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MCA_HOSTILE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MCA_HOSTILE_KILLS;
    public static final ForgeConfigSpec.IntValue MCA_HOSTILE_COOLDOWN;
    public static final ForgeConfigSpec.IntValue MCA_HOSTILE_HOLD_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MCA_FORCE_SWALLOW_ENABLED;
    public static final ForgeConfigSpec.IntValue MCA_FORCE_SWALLOW_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue MCA_CONVERT_ENABLED;
    public static final ForgeConfigSpec.IntValue MCA_CONVERT_HEARTS;
    public static final ForgeConfigSpec.IntValue MCA_COMFORT_DECLINE_PAUSE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MCA_VILLAGER_XP_ENABLED;
    public static final ForgeConfigSpec.IntValue MCA_VILLAGER_XP_DAILY_CAP;
    public static final ForgeConfigSpec.BooleanValue MCA_TRADE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MCA_VILLAGER_STORAGE_ENABLED;
    public static final ForgeConfigSpec.IntValue MCA_TRADE_DISCOUNT_START_HEARTS;
    public static final ForgeConfigSpec.IntValue MCA_TRADE_DISCOUNT_FULL_HEARTS;
    public static final ForgeConfigSpec.IntValue MCA_TRADE_MAX_DISCOUNT_PERCENT;
    public static final ForgeConfigSpec.IntValue MCA_LOW_AFFECTION_HEARTS;
    public static final ForgeConfigSpec.IntValue MCA_TRADE_MAX_SURCHARGE_PERCENT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        HARD_MODE_ENABLED = b
                .comment("If false, nobody can digest mobs: hard mode and the Digest button are turned off (soft mode only).")
                .define("hardModeEnabled", true);
        STRUGGLE_DAMAGE_MULTIPLIER = b
                .comment("Multiplies the damage swallowed mobs deal to you in hard mode. 0 = they never hurt you, 1 = normal.")
                .defineInRange("struggleDamageMultiplier", 1.0, 0.0, 10.0);
        XP_MULTIPLIER = b
                .comment("Multiplies all stomach XP gained. 0.5 = half as fast, 2 = twice as fast.")
                .defineInRange("xpMultiplier", 1.0, 0.0, 10.0);
        ITEM_STORAGE_ENABLED = b
                .comment("If false, the stomach can't store items (no item swallowing and no Storage screen).")
                .define("itemStorageEnabled", true);
        PLAYER_SWALLOWING_ENABLED = b
                .comment("If false, players can't swallow each other at all.",
                        "Even when true, only players whose role is Prey or Both can be swallowed, and only after they accept a request;",
                        "swallowed players can leave any time with the Release key, unless it is hard mode (see playerHardVoreEnabled).")
                .define("playerSwallowingEnabled", true);
        PLAYER_MAX_HOLD_SECONDS = b
                .comment("Longest a swallowed player is held before they are let out automatically.")
                .defineInRange("playerMaxHoldSeconds", 300, 10, 3600);
        PLAYER_HARD_VORE_ENABLED = b
                .comment("If false, players are always held softly, even when the swallower is in hard mode.",
                        "If true, a player swallowed in hard mode is digested over playerDigestSeconds and can only get out",
                        "by struggling (the Release key does not work for them). The request they accept says which mode it is.")
                .define("playerHardVoreEnabled", true);
        PLAYER_DIGEST_SECONDS = b
                .comment("Hard mode only: how long a swallowed player takes to be digested.")
                .defineInRange("playerDigestSeconds", 180, 30, 3600);
        PLAYER_STRUGGLE_CHANCE = b
                .comment("Hard mode only: percent chance that one struggle (once per second at most) frees a swallowed player.",
                        "It drops to half of this as digestion nears 100%.")
                .defineInRange("playerStruggleChancePercent", 12.0, 1.0, 100.0);
        PLAYER_DIGESTION_KILLS = b
                .comment("Hard mode only: what happens when the digestion timer of a swallowed player runs out.",
                        "false (default) = they are let out, weakened and slowed for a while. true = they die.",
                        "Players are always warned in the request they accept.")
                .define("playerDigestionKills", false);
        PLAYER_WALL_RUB_COOLDOWN_SECONDS = b
                .comment("Soft mode: how often a swallowed player can rub the stomach walls (gives the swallower food and buffs).")
                .defineInRange("playerWallRubCooldownSeconds", 10, 1, 300);
        NESTED_WEIGHT_MULTIPLIER = b
                .comment("Shulker boxes and bundles weigh what is inside them: each item counts like a stack in a slot,",
                        "times this number, on top of the box itself. 0 = boxes always weigh one slot. This drives the item belly size.")
                .defineInRange("nestedWeightMultiplier", 1.0, 0.0, 2.0);
        HAZARD_SELF_DAMAGE = b
                .comment("Health points (2 = one heart) you take when you swallow something sharp or hazardous (swords, axes, cactus, lava ...),",
                        "and whenever you move one between your inventory and the stomach storage. It never takes you below 2 hearts and creative players take none. 0 = no damage.",
                        "Edit the item tag gulp:stomach_hazardous to change which items count (swords, axes, tridents, shears and arrows always do).")
                .defineInRange("hazardSelfDamage", 2.0, 0.0, 10.0);
        SHARED_STORAGE_VIEW_ENABLED = b
                .comment("If true, a swallowed player can look at (not touch) the item storage of whoever swallowed them.")
                .define("sharedStorageViewEnabled", true);
        ITEM_DIGESTION_ENABLED = b
                .comment("Hard mode only: gear in the item storage (tools, weapons, armor) is broken down into its crafting ingredients over time.",
                        "If false, stored items are never touched by hard mode.")
                .define("itemDigestionEnabled", true);
        TRASH_DISSOLVE_ENABLED = b
                .comment("Hard mode only: items in the tag gulp:stomach_trash (cobblestone, dirt, seeds ...) dissolve over time.")
                .define("trashDissolveEnabled", true);
        DECONSTRUCT_ENCHANTED = b
                .comment("If true, enchanted gear is broken down too (the enchantments are lost). If false it is left alone.")
                .define("deconstructEnchanted", false);
        UNIVERSAL_DIGESTION_ENABLED = b
                .comment("If false, the Eat buttons on the storage screen only eat food (Iron Gut / Universal Digestion does nothing).",
                        "Otherwise any item can be eaten: every 32 items restores hunger (see the Iron Gut perk).")
                .define("universalDigestionEnabled", true);
        ARMORED_GUT_ENABLED = b
                .comment("If false, the Armored Gut perk gives no armor.")
                .define("armoredGutEnabled", true);
        SMELTING_GUT_ENABLED = b
                .comment("If false, the Smelting Gut perk does nothing (raw ores in the item storage are left alone).")
                .define("smeltingGutEnabled", true);
        MCA_ENABLED = b
                .comment("Minecraft Comes Alive reactions (only does anything if MCA is installed): swallowing villagers changes",
                        "how they feel about you, and a 'predator' status makes frightened villagers refuse to talk to you.")
                .define("mcaIntegrationEnabled", true);
        MCA_REACTION_MULTIPLIER = b
                .comment("Scales every change to villagers' hearts and mood. 0 = villagers never react, 2 = twice as strongly.")
                .defineInRange("mcaReactionMultiplier", 1.0, 0.0, 5.0);
        MCA_LOCKOUT_ENABLED = b
                .comment("If false, villagers never refuse to talk, trade or marry because of your predator status.")
                .define("mcaLockoutEnabled", true);
        MCA_PREDATOR_LOCK = b
                .comment("Predator status at which frightened villagers refuse to talk to you.",
                        "Swallowing a villager in hard mode adds about 15, digesting one about 25.")
                .defineInRange("mcaPredatorLockThreshold", 30, 1, 200);
        MCA_HEARTS_LOCK = b
                .comment("A villager whose hearts for you are at or below this also refuses to talk while your predator status is above 0.")
                .defineInRange("mcaHeartsLockThreshold", -30, -500, 0);
        MCA_PREDATOR_DECAY_SECONDS = b
                .comment("Seconds for the predator status to drop by 1 (at the default 20, a status of 40 fades in about 13 minutes).")
                .defineInRange("mcaPredatorDecaySeconds", 20, 1, 3600);
        MCA_PANIC = b
                .comment("If true, villagers who see you swallow or digest a villager run from you for a few seconds.")
                .define("mcaVillagersPanic", true);
        MCA_MEMORY_ENABLED = b
                .comment("Villagers remember being swallowed, released, or seeing it happen: written to the villager's own saved data",
                        "and into MCA's long-term memory, where MCA dialogue and the MCA: Conversations add-on can read it.")
                .define("mcaMemoryEnabled", true);
        MCA_AFFECTION_ENABLED = b
                .comment("Villagers who like you enough may ask to be carried safely (soft mode only, they can never be digested),",
                        "and comment on being inside. Children are never swallowed, whatever this is set to.")
                .define("mcaAffectionEnabled", true);
        MCA_AFFECTION_HEARTS = b
                .comment("MCA hearts a villager needs with you before they will ask to be carried or comment from inside.",
                        "Brand-new villagers have 0 hearts with you, so set this to 0 to test. Type /gulp villagerinfo next to a villager to see their hearts.")
                .defineInRange("mcaAffectionHearts", 60, 0, 500);
        MCA_REQUEST_COOLDOWN_SECONDS = b
                .comment("How long a villager waits before asking to be carried again.")
                .defineInRange("mcaRequestCooldownSeconds", 600, 30, 36000);
        MCA_COMFORT_ENABLED = b
                .comment("A pred villager who likes you enough (mcaAffectionHearts) may offer to hold a hurt player in their tummy until they are healed.",
                        "Only players whose role is Prey or Both are ever asked, they have to press Accept, and they can climb out any time (soft mode only).")
                .define("mcaComfortEnabled", true);
        MCA_COMFORT_DECLINE_PAUSE_SECONDS = b
                .comment("A hurt player is offered comfort by a pred villager straight away, with no waiting between offers.",
                        "The only pause: this many seconds without offers after you press Decline or climb out early.")
                .defineInRange("mcaComfortDeclinePauseSeconds", 30, 0, 3600);
        MULTI_ORGANS_ENABLED = b
                .comment("Breast, Cock, Unbirth and Tail vore: besides the stomach, a player can swallow into four more places. Each one holds its own prey,",
                        "has its own room and its own soft / hard setting, so the stomach can be soft while the cock is hard. Pick the place on the stomach screen.",
                        "If off, everything goes into the stomach as before.")
                .define("multiOrgansEnabled", true);
        ORGAN_LOOT_ENABLED = b
                .comment("What hard mode makes in the extra places: Breast completely digests prey into Breast Milk in the breast storage (which churns into Butter), Cock gives Cum (a milk bucket) and no loot, Unbirth makes digested mobs drop their spawn egg.",
                        "If off, those places give the normal digestion result.")
                .define("organLootEnabled", true);
        BREAST_CHURN_SECONDS = b
                .comment("Breast in hard mode: seconds the stored Breast Milk takes to digest, one batch at a time, into Butter.")
                .defineInRange("breastChurnSeconds", 60, 5, 3600);
        TAIL_TRANSIT_SECONDS = b
                .comment("Tail vore: seconds prey spend travelling through the tail before they reach the stomach (they wait there if the stomach is full).")
                .defineInRange("tailTransitSeconds", 30, 1, 3600);
        UNBIRTH_NAUSEA_MAX_SECONDS = b
                .comment("Unbirth: when prey struggles, they get nausea for 1 to this many seconds (a swallowed mob has no one to feel it, so the one carrying it does).")
                .defineInRange("unbirthNauseaMaxSeconds", 2, 1, 10);
        NESTED_SWALLOW_ENABLED = b
                .comment("When a player swallows a villager who is holding a player (comforting or cuddling them), the player inside comes along, like a",
                        "russian doll: they stay held by the villager, are healed from inside, and can still climb out any time with the Release key.",
                        "Needs room for both, and playerSwallowingEnabled. If off, the held player is simply let out beside the villager.")
                .define("nestedSwallowEnabled", true);
        MCA_HOSTILE_ENABLED = b
                .comment("A pred villager whose hearts for you are at or below mcaLowAffectionHearts (shy ones: twice as low) may grab and swallow a player (role Prey or Both) without asking.",
                        "The player can't just leave with Release, they have to struggle (same chance as struggling out of a player: playerStruggleChance). If they don't get out in time, see mcaHostileKills.")
                .define("mcaHostileSwallowEnabled", true);
        MCA_HOSTILE_KILLS = b
                .comment("If a player does not break free from an angry villager before mcaHostileHoldSeconds run out, the villager digests them: the player dies, their whole inventory goes into the villager's storage (what does not fit drops at the villager), and the villager comments on it.",
                        "Only for players whose role is still Prey or Both. If this is off, the villager just lets them go.")
                .define("mcaHostileKills", true);
        MCA_HOSTILE_COOLDOWN = b
                .comment("Seconds before the same player can be grabbed again, and before the same villager grabs again.")
                .defineInRange("mcaHostileCooldownSeconds", 300, 10, 7200);
        MCA_HOSTILE_HOLD_SECONDS = b
                .comment("How long an angry villager keeps you if you don't break free first.")
                .defineInRange("mcaHostileHoldSeconds", 45, 10, 600);
        MCA_FORCE_SWALLOW_ENABLED = b
                .comment("Climb In: a player (role Prey or Both) can climb into any adult villager's tummy from the Perks screen, whether the villager is a pred, prey or normal.",
                        "What happens (hearts, how long they put up with it, healing, panic, their lines) depends on the villager's type and shy/confident temper.")
                .define("mcaForceSwallowEnabled", true);
        MCA_FORCE_SWALLOW_COOLDOWN = b
                .comment("Seconds a player has to wait between two Climb Ins.")
                .defineInRange("mcaForceSwallowCooldownSeconds", 120, 0, 3600);
        MCA_CONVERT_ENABLED = b
                .comment("Ask to be a Pred: a prey or normal villager who likes you enough can be asked (Perks screen) to become a pred villager for good.")
                .define("mcaConvertEnabled", true);
        MCA_CONVERT_HEARTS = b
                .comment("MCA hearts a prey or normal villager needs with you before they agree to become a pred. Shy villagers need 125% of this, confident ones 80%.")
                .defineInRange("mcaConvertHearts", 80, 0, 500);
        MCA_CUDDLE_ENABLED = b
                .comment("A pred villager who really likes you (and has no bad history with you) may offer a cosy cuddle in their tummy even when you are not hurt.",
                        "Same rules as comforting: only Prey or Both players, they have to press Accept, soft only, and they can climb out any time.")
                .define("mcaCuddleEnabled", true);
        MCA_VILLAGER_XP_ENABLED = b
                .comment("Adult MCA villagers earn XP (and levels) from talking to you, from gifts and from a ride in a stomach, not only from comforting.",
                        "Children never earn XP.")
                .define("mcaVillagerXpEnabled", true);
        MCA_VILLAGER_XP_DAILY_CAP = b
                .comment("The most XP one villager can earn per Minecraft day from talking, gifts and rides together (comforting is not counted).")
                .defineInRange("mcaVillagerXpDailyCap", 30, 1, 1000);
        MCA_VILLAGER_STORAGE_ENABLED = b
                .comment("Pred villagers have an item storage you open from the Storage button on their Perks screen. It grows with their level",
                        "(1 row at level 1 up to 4 rows at level 9) and the items drop if the villager dies.")
                .define("mcaVillagerStorageEnabled", true);
        MCA_TRADE_ENABLED = b
                .comment("Trade prices with adult MCA villagers follow their hearts with you: a discount for villagers who like you a lot,",
                        "a surcharge for villagers who don't. Type /gulp villagerinfo next to a villager to see their current price change.")
                .define("mcaTradeEnabled", true);
        MCA_TRADE_DISCOUNT_START_HEARTS = b
                .comment("Hearts above which a villager starts giving you a discount.")
                .defineInRange("mcaTradeDiscountStartHearts", 40, 0, 500);
        MCA_TRADE_DISCOUNT_FULL_HEARTS = b
                .comment("Hearts at which the discount reaches its maximum.")
                .defineInRange("mcaTradeDiscountFullHearts", 100, 1, 500);
        MCA_TRADE_MAX_DISCOUNT_PERCENT = b
                .comment("The biggest discount, in percent of the normal price. 0 = no discounts.")
                .defineInRange("mcaTradeMaxDiscountPercent", 20, 0, 90);
        MCA_LOW_AFFECTION_HEARTS = b
                .comment("Hearts below which a villager is cold to you: they charge extra and pred villagers turn down comforting you.",
                        "The surcharge reaches its maximum 40 hearts below this.")
                .defineInRange("mcaLowAffectionHearts", -10, -500, 0);
        MCA_TRADE_MAX_SURCHARGE_PERCENT = b
                .comment("The biggest surcharge, in percent of the normal price. 0 = no surcharges.")
                .defineInRange("mcaTradeMaxSurchargePercent", 25, 0, 200);
        SPEC = b.build();
    }

    private GulpServerConfig() { }
}
