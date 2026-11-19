package com.example.gulp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/**
 * A villager's own stomach look (the same wall, pattern and acid settings a player has), saved with the villager (VillagerData) and
 * changed from the Settings button of their Stomach screen. Held players see it from inside (PreyViewPacket). Values are always
 * clamped. The numbers are in the same order as LookPacket: wallHue, wallShade, acidHue, acidShade, acidLevel, patternStrength,
 * patternSize, patternHue, wallPulse; the flags are packed as there too (bit 0 wallCustom, 1 acidCustom, 2-5 pattern, 6 patternCustom).
 */
final class VillagerLooks {
    private VillagerLooks() { }

    static final String KEY = "gulp_look";

    private static float clamp01(float f) {
        return f != f ? 0f : Math.max(0f, Math.min(1f, f));
    }

    /** The default look as numbers. */
    static float[] defaultFloats() {
        Look d = new Look();
        return new float[]{d.wallHue, d.wallShade, d.acidHue, d.acidShade, d.acidLevel, d.patternStrength, d.patternSize, d.patternHue, d.wallPulse};
    }

    static int defaultFlags() {
        return pack(new Look());
    }

    /** A look's flags. */
    static int pack(Look l) {
        return (l.wallCustom ? 1 : 0) | (l.acidCustom ? 2 : 0) | (l.pattern.ordinal() & 15) << 2 | (l.patternCustom ? 64 : 0);
    }

    /** A look's numbers. */
    static float[] floatsOf(Look l) {
        return new float[]{l.wallHue, l.wallShade, l.acidHue, l.acidShade, l.acidLevel, l.patternStrength, l.patternSize, l.patternHue, l.wallPulse};
    }

    /** Numbers and flags back into a Look (the pattern number is cut to the patterns that exist). */
    static Look toLook(float[] f, int flags) {
        Look l = new Look();
        l.wallHue = clamp01(f[0]);
        l.wallShade = clamp01(f[1]);
        l.acidHue = clamp01(f[2]);
        l.acidShade = clamp01(f[3]);
        l.acidLevel = clamp01(f[4]);
        l.patternStrength = clamp01(f[5]);
        l.patternSize = clamp01(f[6]);
        l.patternHue = clamp01(f[7]);
        l.wallPulse = clamp01(f[8]);
        l.wallCustom = (flags & 1) != 0;
        l.acidCustom = (flags & 2) != 0;
        l.patternCustom = (flags & 64) != 0;
        StomachStyle.Pattern[] patterns = StomachStyle.Pattern.values();
        l.pattern = patterns[Math.min(patterns.length - 1, (flags >> 2) & 15)];
        return l;
    }

    // ------------------------------------------------------------------ saved with the villager

    static float[] floatsOf(Entity villager) {
        CompoundTag t = VillagerData.getCompound(villager, KEY);
        float[] d = defaultFloats();
        if (t.isEmpty()) return d;
        for (int i = 0; i < d.length; i++) d[i] = t.contains("f" + i) ? clamp01(t.getFloat("f" + i)) : d[i];
        return d;
    }

    static int flagsOf(Entity villager) {
        CompoundTag t = VillagerData.getCompound(villager, KEY);
        return t.isEmpty() ? defaultFlags() : t.getInt("Flags") & 0x7F;
    }

    static void save(Entity villager, float[] f, int flags) {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i < Math.min(9, f.length); i++) t.putFloat("f" + i, clamp01(f[i]));
        t.putInt("Flags", flags & 0x7F);
        VillagerData.put(villager, KEY, t);
    }
}
