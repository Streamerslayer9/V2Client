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
    HOSTILE_GRAB, HOSTILE_IDLE, HOSTILE_STRUGGLE, HOSTILE_ESCAPE, HOSTILE_END, HOSTILE_HOLD_REFUSE, HOSTILE_DIGEST, HOSTILE_WARN
}
