package com.example.gulp;

/**
 * Every kind of thing a pred or prey villager says in a set situation. VillagerTrait has a line for each (shy / confident / normal) and
 * Flavor has one for each personality (arrogant / flustered / protective); McaIntegration.talk picks the personality's line when they
 * have one and the temper's line otherwise.
 */
public enum Say {
    COMFORT_OFFER, HOLD, FINISH, TRADE_DISCOUNT, TRADE_SURCHARGE, SNUB, SWALLOWED_WHILE_HOLDING, STORAGE_REFUSE,
    CUDDLE_OFFER, CUDDLE_HOLD, CUDDLE_FINISH, EARLY_EXIT, HOLD_IDLE,
    CONVERT_ACCEPT, CONVERT_REFUSE, CONVERT_ALREADY, CONVERT_AFTER,
    HOSTILE_GRAB, HOSTILE_IDLE, HOSTILE_STRUGGLE, HOSTILE_ESCAPE, HOSTILE_END, HOSTILE_HOLD_REFUSE, HOSTILE_DIGEST, HOSTILE_WARN,
    // a prey villager: asking to be carried, being declined, chatting while inside, being let out
    CARRY_REQUEST, CARRY_REQUEST_AGAIN, CARRY_DECLINED, INSIDE, INSIDE_REGULAR, RELEASED,
    // a pred villager whose comfort or cuddle offer was declined
    COMFORT_DECLINED, CUDDLE_DECLINED,
    // any pred or prey villager who saw the player swallow someone
    WITNESS_SOFT, WITNESS_HARD,
    // a pred whose belly is full, a prey let out after the player slept, and the night: going to bed and waking up
    FULL, NAP, BEDTIME, GOOD_MORNING
}
