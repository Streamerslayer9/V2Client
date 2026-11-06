package com.example.gulp;

/**
 * A pred or prey villager's temper: SHY or CONFIDENT. Normal villagers have none (NONE) and act as before.
 * It comes from the villager's MCA personality (Shy, Sensitive, Gloomy, Grumpy = shy; Confident, Peppy, Athletic, Odd =
 * confident). Villagers with any other personality get one or the other from their UUID, so it never changes.
 * See McaIntegration.traitOf. The ordinal is what goes over the network (VillagerInfoPacket).
 *
 * Pred villagers (the ones who comfort you):
 *  CONFIDENT  offers sooner (under 60% HP), needs fewer hearts (80%), asks again sooner, heals 15% faster
 *  SHY        offers later (under 40% HP), needs more hearts (125%), asks again later, holds you 15% longer
 * Prey villagers use the trait to decide how often and how readily they ask to be carried (McaIntegration.tickAffection).
 */
public enum VillagerTrait {
    NONE("", 0.5f, 1.0, 1.0, 1.0f, 1.0f),
    SHY("Shy", 0.4f, 1.25, 1.5, 1.0f, 1.15f),
    CONFIDENT("Confident", 0.6f, 0.8, 0.75, 1.15f, 1.0f);

    public final String label;
    /** A hurt player is offered comfort when their health is at or under this share of their max. */
    public final float comfortHpFraction;
    /** Multiplies the hearts needed before they offer comfort. */
    public final double heartsMult;
    /** Multiplies the wait before they offer again. */
    public final double cooldownMult;
    /** Multiplies the HP healed per second. */
    public final float healMult;
    /** Multiplies how long they can hold you. */
    public final float holdMult;

    VillagerTrait(String label, float comfortHpFraction, double heartsMult, double cooldownMult, float healMult, float holdMult) {
        this.label = label;
        this.comfortHpFraction = comfortHpFraction;
        this.heartsMult = heartsMult;
        this.cooldownMult = cooldownMult;
        this.healMult = healMult;
        this.holdMult = holdMult;
    }

    public static VillagerTrait byId(int id) {
        VillagerTrait[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }

    /** What a pred says when they offer to comfort a hurt player. */
    public String comfortOfferLine() {
        return switch (this) {
            case CONFIDENT -> "Whoa, you're hurt! Come here, let me patch you up in my tummy!";
            case SHY -> "Um... you look hurt. Would you like to rest inside me for a bit? I'll be careful...";
            case NONE -> "You look hurt. Would you like to rest in my tummy until you feel better?";
        };
    }

    /** What a pred says when they start holding the player. */
    public String holdLine() {
        return switch (this) {
            case CONFIDENT -> "Got you! Just relax, I've done this before.";
            case SHY -> "Okay... there. Is it alright in there? Tell me if you want out...";
            case NONE -> "There you go. Rest up.";
        };
    }

    /** What a pred says when the rest ends on its own (healed or time up). */
    public String finishLine() {
        return switch (this) {
            case CONFIDENT -> "Good as new! Anytime you need a tummy, you know where to find me.";
            case SHY -> "Oh, you're better... I'm glad. Um, thank you for trusting me.";
            case NONE -> "All done. Take care of yourself.";
        };
    }

    /** What a villager says when you open a trade and their prices are lower for you. */
    public String tradeDiscountLine() {
        return switch (this) {
            case CONFIDENT -> "For you? Best prices in the village!";
            case SHY -> "Um... I made it a little cheaper for you. Since you're nice to me...";
            case NONE -> "Friends get a better price.";
        };
    }

    /** What a villager says when you open a trade and their prices are higher for you. */
    public String tradeSurchargeLine() {
        return switch (this) {
            case CONFIDENT -> "Prices are what they are. Take it or leave it.";
            case SHY -> "S-sorry... it costs a bit more for people I don't know well.";
            case NONE -> "I don't know you well enough for the usual price.";
        };
    }

    /** What a pred says instead of offering comfort when they don't like the hurt player. */
    public String snubLine() {
        return switch (this) {
            case CONFIDENT -> "Hurt? Sounds like your problem. Maybe be nicer to people next time.";
            case SHY -> "Um... you look hurt, but... I don't really want to be near you right now.";
            case NONE -> "I'm not helping you.";
        };
    }

    /** What a pred says when someone swallows them while they are holding a player in their tummy. */
    public String swallowedWhileHoldingLine() {
        return switch (this) {
            case CONFIDENT -> "Whoa! Hey! I was in the middle of something here!";
            case SHY -> "E-eep! I had someone in there! Please be careful with them!";
            case NONE -> "Wait, I wasn't done! I had someone in there!";
        };
    }

    /** What a villager says when they won't let a player they don't like look through their storage. */
    public String storageRefuseLine() {
        return switch (this) {
            case CONFIDENT -> "Hmm, no. I don't know you well enough to let you rummage through my things.";
            case SHY -> "Um... I'd rather you didn't look through my things. Sorry...";
            case NONE -> "I'd rather keep my things to myself for now.";
        };
    }

    /** What a pred says when they offer a cosy cuddle to a player who is not hurt (they like you a lot). */
    public String cuddleOfferLine() {
        return switch (this) {
            case CONFIDENT -> "Hey you! Feel like a cuddle in my tummy? I'm in a great mood!";
            case SHY -> "Um... would you like to... stay in my tummy for a little while? Only if you want to...";
            case NONE -> "Would you like a cosy rest in my tummy?";
        };
    }

    /** What a pred says when a cuddle starts. */
    public String cuddleHoldLine() {
        return switch (this) {
            case CONFIDENT -> "Come here, you. There. Nice and cosy, right?";
            case SHY -> "T-there... is that okay? I'm happy you said yes...";
            case NONE -> "There you go. Nice and cosy.";
        };
    }

    /** What a pred says when a cuddle ends on its own. */
    public String cuddleFinishLine() {
        return switch (this) {
            case CONFIDENT -> "That was great! We should do that again soon.";
            case SHY -> "Thank you for staying... that meant a lot to me.";
            case NONE -> "That was nice. Thanks for the company.";
        };
    }

    /** What a pred says when the player climbs out early. */
    public String earlyExitLine() {
        return switch (this) {
            case CONFIDENT -> "Already? Fine, but don't push yourself!";
            case SHY -> "O-okay, I hope I didn't do anything wrong...";
            case NONE -> "Okay, take it easy.";
        };
    }

    /** What a pred says now and then while they hold you (about every 20 seconds). */
    public String holdIdleLine() {
        String[] lines = switch (this) {
            case CONFIDENT -> new String[] {"Doing okay in there? Good.", "You're my favourite passenger, you know that?",
                    "Don't worry about a thing. I've got you.", "Stay as long as you need!"};
            case SHY -> new String[] {"Is it okay in there...? I hope it's okay...", "I'm trying to be gentle... tell me if I'm not...",
                    "Um... I'm glad you trust me.", "You can stay as long as you like... I don't mind..."};
            case NONE -> new String[] {"Doing alright in there?", "Rest as long as you need to.", "Nearly there."};
        };
        return lines[java.util.concurrent.ThreadLocalRandom.current().nextInt(lines.length)];
    }

    /** Asked to become a pred, and they trust you enough: they agree. */
    public String convertAcceptLine() {
        return switch (this) {
            case CONFIDENT -> "Me, a pred? Heh, I thought you'd never ask! Come here, I'll take good care of you.";
            case SHY -> "Y-you want me to... be a pred? For you? ...O-okay. I trust you. I'll try my best, I promise.";
            case NONE -> "A pred, huh? Sure, why not. I trust you, so let's give it a try.";
        };
    }

    /** Asked to become a pred, but they don't know you well enough yet. */
    public String convertRefuseLine() {
        return switch (this) {
            case CONFIDENT -> "Whoa there. We're not close enough for that. Come back when I know you better.";
            case SHY -> "U-um... I'm sorry, I don't know you well enough for that yet... maybe later?";
            case NONE -> "That's a big thing to ask. Let's get to know each other better first.";
        };
    }

    /** Asked to become a pred when they already are one. */
    public String convertAlreadyLine() {
        return switch (this) {
            case CONFIDENT -> "Ha! I'm already one, remember? Hop in any time.";
            case SHY -> "U-um... I already am one, remember? ...You're always welcome.";
            case NONE -> "I'm already a pred, you know.";
        };
    }

    /** Said just after they became a pred (a little later, once it sinks in). */
    public String convertAfterLine() {
        return switch (this) {
            case CONFIDENT -> "Pred life! I've got a good feeling about this. Don't be a stranger!";
            case SHY -> "I... I'm a pred now. It feels strange. But a good strange... thank you for believing in me.";
            case NONE -> "It feels a little different already. Thanks for trusting me with this.";
        };
    }

    /** What a pred says when they grab and swallow a player they can't stand. */
    public String hostileGrabLine() {
        return switch (this) {
            case CONFIDENT -> "I've had it with you! Get in here and think about what you've done!";
            case SHY -> "I-I'm sorry, I can't take it anymore... you hurt me, so... please stay in there for a bit...";
            case NONE -> "That's enough out of you. In you go.";
        };
    }

    /** Said now and then while an angry pred keeps a player. */
    public String hostileIdleLine() {
        String[] lines = switch (this) {
            case CONFIDENT -> new String[] {"Squirm all you like. You're staying until I say so.", "Maybe next time you'll be nicer to people.",
                    "Oh, you don't like it in there? Good."};
            case SHY -> new String[] {"I'm sorry... I just needed you to stop...", "Please stop moving... I don't want to hurt you, I just... I'm upset...",
                    "I hope you understand why I did this..."};
            case NONE -> new String[] {"You're staying put for a while.", "Think about it.", "I'm not letting you out just yet."};
        };
        return lines[java.util.concurrent.ThreadLocalRandom.current().nextInt(lines.length)];
    }

    /** What they say when the player struggles and doesn't get out. */
    public String hostileStruggleLine() {
        return switch (this) {
            case CONFIDENT -> "Ha! Is that all you've got?";
            case SHY -> "E-ow! Please don't... I'm not letting go yet...";
            case NONE -> "Stop wriggling!";
        };
    }

    /** What they say when the player breaks free. */
    public String hostileEscapeLine() {
        return switch (this) {
            case CONFIDENT -> "Tch! Fine, get out of my sight. And don't come back until you've learned some manners!";
            case SHY -> "Oh... you got out... I-I'm sorry, I just... please stay away from me for a while...";
            case NONE -> "Hmph. Fine. Just leave me alone.";
        };
    }

    /** What they say when they finally let the player go on their own. */
    public String hostileEndLine() {
        return switch (this) {
            case CONFIDENT -> "That's enough. Out. Next time, treat me better and none of this happens.";
            case SHY -> "O-okay... you can go now... I hope we can be friends again some day...";
            case NONE -> "Alright, you can go. Don't make me do that again.";
        };
    }

    /** What they say when the player presses Release while they are angry. */
    public String hostileHoldRefuseLine() {
        return switch (this) {
            case CONFIDENT -> "Nice try. You're not leaving until I'm done with you.";
            case SHY -> "S-sorry! I can't let you out yet...";
            case NONE -> "No. You stay.";
        };
    }

    /** What they say after digesting a player they couldn't stand and taking everything they had on them. */
    public String hostileDigestLine() {
        String[] lines = switch (this) {
            case CONFIDENT -> new String[] {"Well, well. Now all of this is mine. Shouldn't have been so rude!",
                    "Delicious. And look at all this loot! Well now, it's my stuff.",
                    "That's what you get. Thanks for the donation, it's all mine now."};
            case SHY -> new String[] {"I-I'm sorry... I didn't want it to end like this... I guess your things are mine now...",
                    "Oh no... it's over... I'll... I'll look after your things. They're mine now, I suppose...",
                    "I told you to stay away... Um, I'll keep your belongings safe. They're mine now, sorry..."};
            case NONE -> new String[] {"Well, that's that. Your things are mine now.", "You brought this on yourself. I'll take your stuff.",
                    "Done. Well, now it's my stuff."};
        };
        return lines[java.util.concurrent.ThreadLocalRandom.current().nextInt(lines.length)];
    }

    /** A warning while the time runs out. */
    public String hostileWarnLine() {
        return switch (this) {
            case CONFIDENT -> "Getting tired already? Struggle harder, or I'll make you part of me.";
            case SHY -> "P-please get out soon... I don't want to have to... digest you...";
            case NONE -> "You're running out of time in there.";
        };
    }
}
