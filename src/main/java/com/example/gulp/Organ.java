package com.example.gulp;

import net.minecraft.nbt.CompoundTag;

/**
 * The places prey can be held: the stomach, and the four extra ones. Every entry in a Stomach's contents has an "Organ" number (missing =
 * stomach, which is what older saves are). Each organ has its own soft / hard setting (Stomach.isHard) and its own room (Stomach.usedIn).
 */
public enum Organ {
    STOMACH("Stomach"), BREAST("Breast"), COCK("Cock"), UNBIRTH("Unbirth"), TAIL("Tail");

    public static final int COUNT = values().length;
    public static final int STOMACH_ID = 0;
    public static final int UNBIRTH_ID = 3;
    public static final int TAIL_ID = 4;

    public final String label;

    Organ(String label) { this.label = label; }

    public String lower() { return label.toLowerCase(java.util.Locale.ROOT); }

    public static Organ byId(int id) { return id >= 0 && id < COUNT ? values()[id] : STOMACH; }

    /** Which organ an entry is in. */
    public static int of(CompoundTag entry) {
        int o = entry.getInt("Organ");
        return o >= 0 && o < COUNT ? o : STOMACH_ID;
    }
}
