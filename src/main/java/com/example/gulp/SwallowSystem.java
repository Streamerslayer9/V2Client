package com.example.gulp;

/**
 * Which mod handles eating mobs and players for one player (only matters when Flamevore is installed too).
 * Chosen on first join and changeable in Settings. Saved with the stomach; the ordinal is what goes over the network.
 *  UNSET     not chosen yet (the player is asked on first join when Flamevore is installed)
 *  GULP      Gulp swallows mobs and players, and the belly grows
 *  FLAMEVORE Flamevore does the swallowing. Gulp's mob/player eating and belly growth are off; item swallowing,
 *            levels, perks and item storage still work
 */
public enum SwallowSystem {
    UNSET("Not chosen"),
    GULP("Gulp"),
    FLAMEVORE("Flamevore");

    public final String label;

    SwallowSystem(String label) {
        this.label = label;
    }

    /** @return the system with this ordinal, or null if it is out of range */
    public static SwallowSystem byId(int id) {
        SwallowSystem[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }

    /** @return the system with this enum name, or null if it isn't one */
    public static SwallowSystem byName(String name) {
        for (SwallowSystem s : values()) {
            if (s.name().equals(name)) return s;
        }
        return null;
    }
}
