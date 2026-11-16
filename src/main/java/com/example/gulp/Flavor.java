package com.example.gulp;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A pred or prey villager's personality on top of their temper (shy or confident; see VillagerTrait). It comes from the villager's
 * UUID, so it never changes and needs nothing saved (McaIntegration.flavorOf):
 *  ARROGANT    a confident villager who is cocky about it: a pred who brags about eating you, a prey who gloats about being the best meal.
 *  FLUSTERED   a shy villager who is very embarrassed about enjoying it: a pred who blushes bright red, a prey who stutters but is happy.
 *  PROTECTIVE  rare (often a guard): a pred whose belly heals more, a prey who fusses over the player (and while the player has them
 *              inside, the player does not get hungry).
 * Every method returns null for NONE (and for a line this personality has no special version of), so the caller uses the temper's line.
 */
public enum Flavor {
    NONE("", 1.0f),
    ARROGANT("Arrogant", 1.0f),
    FLUSTERED("Flustered", 1.0f),
    PROTECTIVE("Protective", 1.6f);

    public final String label;
    /** Multiplies the HP a pred heals per second. */
    public final float healMult;

    Flavor(String label, float healMult) {
        this.label = label;
        this.healMult = healMult;
    }

    public static Flavor byName(String name) {
        for (Flavor f : values()) if (f.name().equalsIgnoreCase(name)) return f;
        return NONE;
    }

    public static Flavor byId(int id) {
        Flavor[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }

    public String lower() { return label.toLowerCase(Locale.ROOT); }

    private static String pick(String... lines) {
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
    }

    // ------------------------------------------------------------------ a pred holding the player

    public String holdLine(boolean cuddle) {
        return switch (this) {
            case ARROGANT -> cuddle ? "Come here, you. Nobody cuddles like I do, and everybody knows it."
                    : "Ha! Got you. Nobody swallows as smoothly as I do.";
            case FLUSTERED -> cuddle ? "O-okay... you can stay in there... I-I'm not blushing, you are..."
                    : "T-there... you're inside me now... oh no, I'm so red, don't look at me...";
            case PROTECTIVE -> cuddle ? "Relax. Nothing gets to you while you're in here."
                    : "I've got you. You're safe with me now, I promise. Rest.";
            case NONE -> null;
        };
    }

    public String idleLine() {
        return switch (this) {
            case ARROGANT -> pick("Admit it, this is the best seat in the village.", "Look at that, I'm a natural at this!",
                    "You could never find a better tummy than mine.", "Other preds wish they were me. Just saying.",
                    "I could carry you all day, no sweat. Look at these muscles!");
            case FLUSTERED -> pick("U-um... are you comfortable...? S-sorry, I'm so red...", "Please don't tell anyone how much I like this...",
                    "I-I can feel you moving... eep...", "Sorry, I'm all flustered... I just really like having you in there...");
            case PROTECTIVE -> pick("Doing alright? I'll keep watch. Nothing can hurt you in here.", "Take it easy. I've got you.",
                    "Rest as long as you need. I'm not going anywhere.", "You're safe. I won't let anything near you.");
            case NONE -> null;
        };
    }

    public String comfortOfferLine() {
        return switch (this) {
            case ARROGANT -> "You're hurt? Lucky for you, the best tummy in the village is right here. Hop in!";
            case FLUSTERED -> "Y-you're hurt... would you... maybe... rest in my tummy? I-if that's not weird...";
            case PROTECTIVE -> "You're hurt. Come here, I'll keep you safe until you're better.";
            case NONE -> null;
        };
    }

    public String cuddleOfferLine() {
        return switch (this) {
            case ARROGANT -> "Hey. Want to be cuddled by the best in the business? I'll let you in my tummy.";
            case FLUSTERED -> "U-um... would you like a cuddle...? In my tummy...? I-it's fine if not!";
            case PROTECTIVE -> "You look like you could use a safe place to rest. Want to curl up in my tummy?";
            case NONE -> null;
        };
    }

    public String finishLine() {
        return switch (this) {
            case ARROGANT -> "And that's how it's done. Come back anytime, you know where the best tummy is.";
            case FLUSTERED -> "T-thank you for staying... that was... nice... I-I mean, goodbye!";
            case PROTECTIVE -> "Good, you're better. Look after yourself out there, alright?";
            case NONE -> null;
        };
    }

    /** Ticks between the lines a pred says while holding someone (at least 200). */
    public int chatterGap() {
        return this == ARROGANT ? 300 : this == PROTECTIVE ? 350 : 400;
    }

    // ------------------------------------------------------------------ a prey villager inside the player

    public String swallowedLine() {
        return switch (this) {
            case ARROGANT -> pick("Ha! You picked the best one. I bet I'm the finest meal you'll ever have!",
                    "Finally, someone with taste! I'm the best meal in the village, you know.");
            case FLUSTERED -> pick("E-eep! O-oh... I didn't think I'd... l-like it this much...",
                    "I-it's warm in here... p-please don't tell anyone I'm smiling...");
            case PROTECTIVE -> pick("Oh! Alright. Just promise me you'll take care of yourself, okay?",
                    "Fine, fine. But you'd better be eating properly out there.");
            case NONE -> null;
        };
    }

    public String insideLine() {
        return switch (this) {
            case ARROGANT -> pick("You're welcome, by the way. For being the best meal you've ever had.",
                    "Everybody else is jealous right now. You know that, right?",
                    "Take your time. Savour it. There's only one me.",
                    "I'd say I'm worth every bite, but you're not biting. Smart.");
            case FLUSTERED -> pick("Th-this is s-so embarrassing... but I kind of... love it...",
                    "I-I'm not going to say anything... but I'm really happy right now...",
                    "S-sorry, I keep smiling... I can't help it...", "D-don't look at me like that... I'm all red...");
            case PROTECTIVE -> pick("Have you eaten today? You look tired.", "Don't skip meals, please. I worry about you.",
                    "Watch your health out there, okay? I'll be right here.", "Rest when you can. You push yourself too hard.");
            case NONE -> null;
        };
    }

    /** A protective prey notices the player is hungry or hurt. */
    public String careLine() {
        return pick("You're running on empty! Eat something, I'm serious.", "You're hurt and hungry and still out here? Please take care of yourself.",
                "You need to eat more. I'll worry until you do.", "You can't keep doing this to yourself. Sit down and have a proper meal.");
    }

    // ------------------------------------------------------------------ feeding a villager to a pred villager

    /** What a pred says when the player feeds them a villager. */
    public String fedByPlayerLine() {
        return switch (this) {
            case ARROGANT -> "Mmm, delivered right to the best tummy in town. Good choice!";
            case FLUSTERED -> "O-oh! F-for me? ...I'll be careful with them. Thank you...";
            case PROTECTIVE -> "I'll look after them. They'll be safe in here.";
            case NONE -> null;
        };
    }

    /** What the villager who was fed says. */
    public String fedAsPreyLine() {
        return switch (this) {
            case ARROGANT -> "Ha! Passed along like the prized meal I am!";
            case FLUSTERED -> "W-wait, a-another tummy...? O-okay... i-it's kind of nice, actually...";
            case PROTECTIVE -> "Oh! Alright. Just be gentle with me... and look after yourself.";
            case NONE -> null;
        };
    }

    public String spitAcceptLine() {
        return switch (this) {
            case ARROGANT -> "Fine, fine, if you insist. Out they come!";
            case FLUSTERED -> "O-of course... s-sorry... here they come...";
            case PROTECTIVE -> "Of course. Easy now, I've got them. Here.";
            case NONE -> null;
        };
    }

    public String spitRefuseLine() {
        return switch (this) {
            case ARROGANT -> pick("Not yet! I'm enjoying this too much. Ask me later.", "Do you know how long it took me to get comfortable? Later.");
            case FLUSTERED -> null;
            case PROTECTIVE -> null;
            case NONE -> null;
        };
    }

    public String spitDoneLine() {
        return switch (this) {
            case ARROGANT -> "That was a fine ride. You're welcome!";
            case FLUSTERED -> "T-that was... um... thank you... I'm all red now...";
            case PROTECTIVE -> "Thank you for looking after me. Please look after yourself, too.";
            case NONE -> null;
        };
    }
}
