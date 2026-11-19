package com.example.gulp;

import java.util.concurrent.ThreadLocalRandom;

/**
 * What happens when a player climbs into a villager's tummy uninvited (Perks screen > Climb In). It depends on the villager's base
 * type (normal 0, prey 1, pred 2) and their temper (shy / confident; NONE = easygoing, which only normal villagers can be).
 * Every combination has its own heart change, how long they put up with it, how much they heal you, whether they run off in a
 * panic, and its own lines. See McaIntegration.forceSwallow (it also bends the hearts by how well the villager already knows you).
 *
 * @param hearts      heart change with the player before it is scaled by how well they know you (negative = a relationship hit)
 * @param holdSeconds how long the villager puts up with you; 0 = the normal cuddle length (preds)
 * @param healScale   share of the villager's normal healing (and feeding, from 1.0) you get while inside
 * @param scare       the villager runs off in a panic afterwards
 * @param rewards     the usual end-of-cuddle rewards (hearts, XP, perks): only villagers who like it
 */
record ForcedOutcome(int hearts, int holdSeconds, float healScale, boolean scare, boolean rewards,
                     String enterLine, String endLine, String exitLine, String[] idleLines) {

    String idleLine() {
        return idleLines[ThreadLocalRandom.current().nextInt(idleLines.length)];
    }

    private static String pick(String... lines) {
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
    }

    /** A pred or prey villager with a personality (arrogant, flustered, protective) reacts in their own way; everyone else as the temper says. */
    static ForcedOutcome of(int type, VillagerTrait temper, Flavor flavor) {
        if (type == 0 || flavor == Flavor.NONE) return of(type, temper);
        if (type == 2) {
            return switch (flavor) {
                case ARROGANT -> new ForcedOutcome(5, 0, 1.0f, false, true,
                        pick("Ha! Climbing in uninvited? Bold! You couldn't resist the best tummy in town!",
                                "Well well, someone knows quality when they see it. Make yourself at home, champ!"),
                        pick("Out you go! Tell everyone whose tummy that was!", "Hope that lived up to the legend. Come back any time!"),
                        pick("Leaving already? Nobody leaves the best tummy that early!", "Aw, leaving? Fine, but you'll miss me!"),
                        new String[] {"Comfy? Of course it is. I run the best tummy in the village.", "Feel those muscles? That's all pred power.",
                                "You picked the best. Don't pretend you didn't."});
                case FLUSTERED -> new ForcedOutcome(1, 0, 1.0f, false, true,
                        pick("E-eep! Y-you just climbed in?! O-oh... I-I don't mind, really... I'm so red...",
                                "W-wait, you didn't ask... b-but you can stay... please don't look at me..."),
                        pick("T-that was... nice... I hope it was alright in there... come back if you like...",
                                "U-um... thank you for visiting... I'm still blushing..."),
                        pick("O-oh... leaving already? I-it's okay... come back any time...", "W-was it too much? S-sorry..."),
                        new String[] {"Is it comfy...? P-please say it's comfy... I'm so embarrassed...",
                                "I can feel you moving... eep... it's kind of nice, actually...", "D-don't tell anyone I like this..."});
                default -> new ForcedOutcome(3, 0, 1.2f, false, true,
                        pick("You climbed in? Alright. You're safe in here. Let me look after you.",
                                "No need to sneak in, you know. But you're welcome. Rest."),
                        pick("There. Rested? Please look after yourself out there.", "Out you go. Eat something and take it easy, alright?"),
                        pick("Leaving already? Alright. Take care of yourself.", "Okay. Come back if you need a safe place."),
                        new String[] {"Rest. I'll keep watch.", "Nothing can hurt you in here.", "Have you eaten today? You look tired."});
            };
        }
        return switch (flavor) { // a prey villager who did not ask for this
            case ARROGANT -> new ForcedOutcome(-7, 20, 0.5f, false, false,
                    pick("Excuse me?! I'm the best meal in the village, I'm the one who gets eaten! Out! Out of my tummy!",
                            "Hey! That's backwards! Who climbs INTO the best meal?!"),
                    pick("Right, that's enough. Rules are rules: I'm the meal, you're not. Out you go!", "Time's up. Ask nicely next time, like everyone else!"),
                    pick("Finally. Some respect for the best meal around, please.", "Good. And remember who you're dealing with."),
                    new String[] {"This is NOT how this works. I'm the meal here.", "You're lucky I'm so magnificent that I put up with this.",
                            "Ten more seconds, then you're out."});
            case FLUSTERED -> new ForcedOutcome(-8, 12, 0f, true, false,
                    pick("E-eep!! Y-you climbed IN?! I'm the one who's supposed to... o-oh no, I'm so embarrassed!!",
                            "W-wait, w-wrong way around! P-please get out, I'm blushing so much!"),
                    pick("P-please don't do that again... I'm still shaking...", "That was... u-um... too much... I need to sit down..."),
                    pick("Th-thank goodness... o-okay...", "O-oh... you're going... I-I mean, good... good..."),
                    new String[] {"P-please come out soon...", "I-I'm so red... this is so embarrassing...", "I don't know what to do..."});
            default -> new ForcedOutcome(-5, 20, 0.7f, false, false,
                    pick("You climbed into me? Are you okay? ...Alright, but only for a moment.",
                            "That's not how this works, you know. Are you hurt? Okay... a moment."),
                    pick("That's enough. Please look after yourself, and ask next time.", "Out you go. Have something to eat and rest, alright?"),
                    pick("Good. Take care of yourself, please.", "Thank you for getting out. Look after yourself."),
                    new String[] {"Are you alright? You didn't need to climb in...", "A moment longer, then out.", "Please, take care of yourself."});
        };
    }

    /** @param type 0 normal, 1 prey, 2 pred (VillagerInfoPacket.type); @param temper SHY, CONFIDENT or NONE (easygoing) */
    static ForcedOutcome of(int type, VillagerTrait temper) {
        boolean shy = temper == VillagerTrait.SHY;
        boolean bold = temper == VillagerTrait.CONFIDENT;
        if (type == 2) {
            if (shy) {
                return new ForcedOutcome(1, 0, 1.0f, false, true,
                        pick("O-oh! You just... climbed in? Um... okay... I-I don't mind, really...",
                                "Eep! Y-you could have asked first... but you're welcome in here..."),
                        pick("I-I hope it was alright in there... you can visit again, if you like...",
                                "Um... that was nice. You're always welcome..."),
                        pick("Oh... was it too much? Sorry...", "Already? O-okay... come back any time..."),
                        new String[] {"Is it comfy in there? ...Please say it's comfy...", "I'm trying not to wriggle. Sorry if I do...",
                                "I can feel you moving... it's kind of nice, actually..."});
            }
            return new ForcedOutcome(bold ? 4 : 2, 0, 1.0f, false, true,
                    pick("Ha! Someone's eager! Fine by me, make yourself at home!",
                            "Oh, you just hop in uninvited? I like your style!"),
                    pick("Out you go! The door's always open, you know that.", "That was fun! Come back any time."),
                    pick("Leaving already? You barely got comfy!", "Aw, so soon? Fine, fine. Off you go."),
                    new String[] {"Comfy? Of course it is. I run a good tummy.", "Don't mind the gurgling, that's just me being happy.",
                            "Take your time in there. I've got nowhere to be."});
        }
        if (type == 1) {
            if (shy) {
                return new ForcedOutcome(-12, 10, 0f, true, false,
                        pick("E-eeek! Y-you can't just... get out, get out, get out!", "N-no no no! I didn't say yes! Please, please get out!"),
                        pick("P-please don't ever do that again, I was so scared...", "I... I need to sit down. That was too much..."),
                        pick("Y-you're leaving? Thank goodness...", "O-okay... okay... I'm okay..."),
                        new String[] {"Please... please come out soon...", "I-I can't breathe properly...", "This is not okay... this is not okay..."});
            }
            return new ForcedOutcome(-8, 20, 0.5f, false, false,
                    pick("Hey! I'm not that kind of villager! Who said you could climb in?!", "Excuse me?! Out! Out of my tummy!"),
                    pick("That's enough. Out. And ask next time!", "Right, time's up. Don't push your luck."),
                    pick("Good. Don't try that again.", "Finally. Some manners, please."),
                    new String[] {"I'm giving you ten more seconds, then you're out.", "You're lucky I'm in a good mood. Barely.",
                            "This is NOT how you ask someone."});
        }
        if (shy) {
            return new ForcedOutcome(-9, 12, 0f, true, false,
                    pick("W-what?! Why are you in my... oh no, oh no, oh no...", "H-hey! W-what are you doing in there?!"),
                    pick("Please... don't ever do that again...", "I-I'm shaking... please don't..."),
                    pick("U-um... okay, good... bye...", "Th-thank you for getting out..."),
                    new String[] {"Is this... is this really happening?", "P-please hurry...", "I don't know what to do..."});
        }
        if (bold) {
            return new ForcedOutcome(-5, 25, 0.5f, false, false,
                    pick("Well, that's... certainly new. Make it quick, friend.", "You climbed into me. Seriously? Okay. Quickly."),
                    pick("Alright, that's enough of that. Out you go.", "Time's up, adventurer. Out."),
                    pick("Fine, off you go. Weird way to say hello.", "Huh. Don't make it a habit."),
                    new String[] {"I have errands, you know.", "This is not on my schedule today.", "Are you done yet?"});
        }
        return new ForcedOutcome(-6, 20, 0.5f, false, false,
                pick("Um. You can't just climb in there without asking. This is strange.", "Excuse me? That's... not normal. At all."),
                pick("Okay, that's enough. Please don't do that again.", "Right. Out. Let's pretend this didn't happen."),
                pick("Right. Let's pretend that didn't happen.", "Okay... off you go."),
                new String[] {"How long is this going to take?", "This is really awkward.", "I'm not sure how I feel about this."});
    }
}
