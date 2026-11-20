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

    // ------------------------------------------------------------------ every situation (see Say)

    /** This personality's line for a situation, or null (use the temper's line). Mirrors VillagerTrait: the same situations, in their own voice. */
    public String say(Say kind) {
        return switch (this) {
            case ARROGANT -> arrogant(kind);
            case FLUSTERED -> flustered(kind);
            case PROTECTIVE -> protective(kind);
            case NONE -> null;
        };
    }

    private String arrogant(Say kind) {
        return switch (kind) {
            case HOLD -> holdLine(false);
            case CUDDLE_HOLD -> holdLine(true);
            case HOLD_IDLE -> idleLine();
            case COMFORT_OFFER -> comfortOfferLine();
            case CUDDLE_OFFER -> cuddleOfferLine();
            case FINISH -> finishLine();
            case CUDDLE_FINISH -> "Hope that was cosy enough for you. Best cuddle in the village, am I right?";
            case TRADE_DISCOUNT -> "Ha, a special price, just for you! Best deals from the best villager around!";
            case TRADE_SURCHARGE -> "Quality costs extra, friend. I'm not cheap, and neither is this.";
            case SNUB -> "Hurt? Ha! Maybe if you weren't such a pain I'd care. Not happening.";
            case SWALLOWED_WHILE_HOLDING -> "Hey! Do you know who you're swallowing?! And I've got someone in here!";
            case STORAGE_REFUSE -> "My stuff? Dream on. You haven't earned a peek at my collection.";
            case EARLY_EXIT -> "Leaving already? Your loss. Nobody tops this ride.";
            case CONVERT_ACCEPT -> "Me? A pred? Obviously! I was born for it. Get ready for the best tummy around!";
            case CONVERT_REFUSE -> "Hah, no. A legend like me doesn't change for just anyone. Earn it first.";
            case CONVERT_ALREADY -> "I'm already the best pred in the village, remember? Hop in any time!";
            case CONVERT_AFTER -> "Pred life suits me. Of course it does. Everyone, get in line!";
            case HOSTILE_GRAB -> "That's it! You've insulted me for the last time. In you go!";
            case HOSTILE_IDLE -> pick("Squirm all you want. Nobody escapes the best.", "Still think you're so tough? Look where you are.",
                    "Nobody crosses me and walks away.");
            case HOSTILE_STRUGGLE -> "Ha! Is that your best? Pathetic!";
            case HOSTILE_ESCAPE -> "Tch! Lucky break. Don't think it'll happen twice!";
            case HOSTILE_END -> "That's enough. Learn some respect, and next time none of this happens.";
            case HOSTILE_HOLD_REFUSE -> "Ha! You're not going anywhere until I say so.";
            case HOSTILE_DIGEST -> pick("And that's what happens. Everything you had is mine now. You're welcome!",
                    "Told you not to mess with the best. Thanks for the loot!");
            case HOSTILE_WARN -> "Running out of time in there! Struggle harder or you'll be part of my legend.";
            case CARRY_REQUEST -> "Hey! The best meal in the village is offering a ride. Carry me in your tummy for a while? You won't regret it!";
            case CARRY_REQUEST_AGAIN -> "Back for the best ride in town? Carry me in your tummy again, you know you want to!";
            case CARRY_DECLINED -> "Hmph! Your loss. A meal like me doesn't come around twice... okay, maybe it does. Ask me later.";
            case INSIDE -> insideLine();
            case INSIDE_REGULAR -> "You carry me all the time, you know. Can't get enough of the best, huh?";
            case RELEASED -> "And that's how a legend rides! Thanks for the lift!";
            case COMFORT_DECLINED -> "Your loss! The offer stands, though. Best tummy in town, whenever you want it.";
            case CUDDLE_DECLINED -> "Suit yourself! The offer stands. Nobody cuddles like me.";
            case WITNESS_SOFT -> "Pfft. Call that a swallow? Come see me and I'll show you how it's done.";
            case WITNESS_HARD -> "Whoa, okay, that's... fine. I could do better, though. Obviously.";
            case FULL -> "Packed full right now! Even the best tummy has limits. Give me a minute!";
            case NAP -> "Slept like a champion in there. Best nap of my life, naturally.";
            case BEDTIME -> "Bedtime! Even legends need their beauty sleep. Come on, I know the best bed in town.";
            case GOOD_MORNING -> "Rise and shine! Slept like a champ, and so did you, thanks to me.";
            case HARD_ACCEPT -> "You want the full experience? Ha! Nobody does it better. Get ready!";
            case HARD_REFUSE -> "Even I have standards. Prove yourself first, then we'll talk.";
        };
    }

    private String flustered(Say kind) {
        return switch (kind) {
            case HOLD -> holdLine(false);
            case CUDDLE_HOLD -> holdLine(true);
            case HOLD_IDLE -> idleLine();
            case COMFORT_OFFER -> comfortOfferLine();
            case CUDDLE_OFFER -> cuddleOfferLine();
            case FINISH -> finishLine();
            case CUDDLE_FINISH -> "T-thank you for staying... that was... I-I'm blushing, please don't look...";
            case TRADE_DISCOUNT -> "U-um... I lowered the prices... for you... p-please don't make it weird...";
            case TRADE_SURCHARGE -> "S-sorry... it's a bit more... I'm too nervous to haggle... please don't look at me...";
            case SNUB -> "I-I'm sorry... you look hurt, but I'm too upset... p-please go...";
            case SWALLOWED_WHILE_HOLDING -> "E-eep!! You swallowed me?! And I have someone in here! P-please be careful!!";
            case STORAGE_REFUSE -> "N-no... p-please don't look through my things... it's embarrassing...";
            case EARLY_EXIT -> "O-oh... you're leaving... d-did I do something wrong? I-it's okay...";
            case CONVERT_ACCEPT -> "Y-you want me to... for real?! O-okay... I'm so embarrassed... but... y-yes... I'll try...";
            case CONVERT_REFUSE -> "I-I'm sorry!! I don't know you well enough... my face is so red... maybe later...";
            case CONVERT_ALREADY -> "U-um... I already am... you know that... you're always welcome in there...";
            case CONVERT_AFTER -> "I-I'm a pred now... I keep blushing... but I think I like it...";
            case HOSTILE_GRAB -> "I-I'm so sorry... but I'm so upset... please stay in there... I'm shaking...";
            case HOSTILE_IDLE -> pick("I-I didn't want to do this... I'm sorry...", "P-please stay still... I'm so embarrassed it came to this...",
                    "I'm so red... I'm just so angry...");
            case HOSTILE_STRUGGLE -> "E-eek! D-don't... I won't let go...";
            case HOSTILE_ESCAPE -> "O-oh... you're out... I-I'm sorry... p-please don't look at me...";
            case HOSTILE_END -> "O-okay... you can go... I'm sorry I got so angry...";
            case HOSTILE_HOLD_REFUSE -> "N-no!! S-sorry... I can't let you out yet...";
            case HOSTILE_DIGEST -> pick("I-I'm sorry... I didn't mean to... your stuff is mine now... I'm so red...",
                    "Oh no... oh no... I'll keep your things safe... s-sorry...");
            case HOSTILE_WARN -> "P-please get out soon... I-I don't want to... you know... p-please...";
            case CARRY_REQUEST -> "U-um... c-could I... maybe... ride in your tummy for a bit? I've been wondering what it's like...";
            case CARRY_REQUEST_AGAIN -> "U-um... c-could you carry me again...? It was so nice last time... please don't tell anyone I asked...";
            case CARRY_DECLINED -> "O-oh... o-okay! S-sorry I asked... I'm so embarrassed... maybe another time...";
            case INSIDE -> insideLine();
            case INSIDE_REGULAR -> "Y-you carry me so often now... p-people are going to notice... n-not that I mind...";
            case RELEASED -> "T-thank you... that was... n-nice... I'm all red now...";
            case COMFORT_DECLINED -> "O-oh... o-okay! S-sorry, I didn't mean to be pushy... the offer stands...";
            case CUDDLE_DECLINED -> "O-okay... s-sorry for asking... I'm so red... the offer stands...";
            case WITNESS_SOFT -> "E-eep! D-did you just...? I-I didn't see anything, I swear!";
            case WITNESS_HARD -> "O-oh no... oh no oh no... p-please don't look at me!!";
            case FULL -> "I-I'm so full... s-sorry... p-please wait a moment...";
            case NAP -> "I-I fell asleep in there... sorry... it was just so cozy...";
            case BEDTIME -> "I-it's late... m-maybe we should sleep now... i-if that's okay...";
            case GOOD_MORNING -> "G-good morning... I hope you slept well... I-I did, it was very cozy...";
            case HARD_ACCEPT -> "Y-you... r-really? O-okay... I'll... I'll try... p-please don't hate me...";
            case HARD_REFUSE -> "T-that's... I-I couldn't... I'm too embarrassed... maybe when I trust you more...";
        };
    }

    private String protective(Say kind) {
        return switch (kind) {
            case HOLD -> holdLine(false);
            case CUDDLE_HOLD -> holdLine(true);
            case HOLD_IDLE -> idleLine();
            case COMFORT_OFFER -> comfortOfferLine();
            case CUDDLE_OFFER -> cuddleOfferLine();
            case FINISH -> finishLine();
            case CUDDLE_FINISH -> "Feeling rested? Good. You can come back any time you need to.";
            case TRADE_DISCOUNT -> "A little off for you. Look after yourself, alright?";
            case TRADE_SURCHARGE -> "I'm sorry, it costs a bit more. I have to be careful with prices to keep the village safe.";
            case SNUB -> "You're hurt, but I can't help you until you've earned it. I do hope you get well.";
            case SWALLOWED_WHILE_HOLDING -> "Careful! I have someone in there! Don't let anything happen to them!";
            case STORAGE_REFUSE -> "I keep the village's supplies safe. I can't let you look through them yet.";
            case EARLY_EXIT -> "Alright, take care. Don't push yourself too hard out there.";
            case CONVERT_ACCEPT -> "If it lets me protect more people, yes. I'll be a pred. You can trust me.";
            case CONVERT_REFUSE -> "I'd want to protect you properly. I need to know you better first.";
            case CONVERT_ALREADY -> "I already am one. And my tummy is always a safe place for you.";
            case CONVERT_AFTER -> "I'll make a safe place for anyone who needs it. Thank you for trusting me.";
            case HOSTILE_GRAB -> "I'm sorry, but you left me no choice. You stay in there until you calm down.";
            case HOSTILE_IDLE -> pick("Take your time to calm down. I'm not going to hurt you, but you're staying.", "Breathe. We'll talk when you're ready.",
                    "I'd rather not do this, but you need to stop.");
            case HOSTILE_STRUGGLE -> "Stop, you'll hurt yourself. Just stay still.";
            case HOSTILE_ESCAPE -> "...Fine. Please go home and think about it, and look after yourself.";
            case HOSTILE_END -> "That's enough. You can go. Please do better next time.";
            case HOSTILE_HOLD_REFUSE -> "Not yet. I'm sorry. Stay still and let me keep you safe.";
            case HOSTILE_DIGEST -> pick("I'm sorry it came to this. I'll keep your things safe, at least.",
                    "It's done. I... didn't want this. I'll take care of your belongings.");
            case HOSTILE_WARN -> "Please get out soon. I really don't want to have to digest you.";
            case CARRY_REQUEST -> "You look like you've been working too hard. Would you carry me in your tummy for a while? I'd like to keep you company.";
            case CARRY_REQUEST_AGAIN -> "I'd like to keep you company again. Would you carry me in your tummy for a while? Just don't skip meals.";
            case CARRY_DECLINED -> "That's alright. Just take care of yourself, okay? I'm here if you change your mind.";
            case INSIDE -> insideLine();
            case INSIDE_REGULAR -> "You carry me a lot. I think you just want company, and that's fine. Just eat something, alright?";
            case RELEASED -> "Thank you for letting me keep you company. Take care of yourself out there.";
            case COMFORT_DECLINED -> "Alright. But please look after those wounds. My offer stands.";
            case CUDDLE_DECLINED -> "No problem. If you ever need a safe place, I'm here.";
            case WITNESS_SOFT -> "Make sure they're comfortable in there, alright? Be gentle.";
            case WITNESS_HARD -> "Stop! Let them out! That's not right!";
            case FULL -> "I'm holding someone already, and I can't risk squeezing you in. Give me a moment.";
            case NAP -> "Thank you for letting me rest. Now go and get some rest yourself, alright?";
            case BEDTIME -> "It's late and you need rest. I'll keep you safe while we sleep.";
            case GOOD_MORNING -> "Good morning. I hope you feel rested. Eat something proper today, alright?";
            case HARD_ACCEPT -> "I don't like this, but it's your choice. I'll be careful. Struggle if you want out.";
            case HARD_REFUSE -> "No. I won't hurt you. Ask me when I'm sure you really mean it.";
        };
    }

    // ------------------------------------------------------------------ hearts (like the shy / confident numbers in McaIntegration)

    /**
     * Hearts with the player when this villager is swallowed, instead of the shy / confident (attitude) numbers.
     * @param preyKind a prey villager, who wanted this (otherwise a pred, who is insulted or embarrassed)
     */
    public int swallowHearts(int base, boolean preyKind, boolean invited, boolean hard) {
        return switch (this) {
            case ARROGANT -> preyKind ? (hard ? -10 : invited ? 18 : 20) : (hard ? -25 : -15);   // thrilled to be the meal / insulted to be eaten
            case FLUSTERED -> preyKind ? (hard ? -35 : invited ? 10 : 12) : (hard ? -50 : -30);  // embarrassed but happy / mortified
            case PROTECTIVE -> preyKind ? (hard ? -20 : 10) : (hard ? -30 : -10);                 // fine with a safe ride / needs to stay on guard
            case NONE -> base;
        };
    }

    /** Added to the hearts a villager of this personality gives for watching someone get swallowed. */
    public int witnessDelta(boolean hard) {
        return switch (this) {
            case ARROGANT -> hard ? 3 : 2;      // unimpressed, not scared
            case FLUSTERED -> hard ? -3 : -2;   // embarrassed to have seen it
            case PROTECTIVE -> hard ? -8 : 1;   // disapproves of a hard swallow
            case NONE -> 0;
        };
    }

    /** Extra hearts when a villager of this personality is let out of a soft stomach (on top of the usual). */
    public int releaseDelta(boolean preyKind) {
        return switch (this) {
            case ARROGANT -> preyKind ? 3 : 0;
            case FLUSTERED -> preyKind ? 3 : 1;
            case PROTECTIVE -> 2;
            case NONE -> 0;
        };
    }
}
