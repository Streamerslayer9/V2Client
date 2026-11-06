package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server: how this player's stomach looks (the stomach, breast, cock and unbirth looks from Settings > Stomach look).
 * The server only keeps it in memory so that a player who is swallowed can be shown the swallower's own look (see PreyViewPacket).
 * Nothing here changes any rule of the game, and every value is clamped.
 */
public class LookPacket {
    /** Stomach, breast, cock, unbirth (the tail uses the stomach's). */
    public static final int COUNT = 4;
    public static final int FLOATS = 9; // wallHue, wallShade, acidHue, acidShade, acidLevel, patternStrength, patternSize, patternHue, wallPulse

    public final float[][] values = new float[COUNT][FLOATS];
    /** bit 0 = wallCustom, bit 1 = acidCustom, bits 2-5 = the pattern number, bit 6 = patternCustom. */
    public final int[] flags = new int[COUNT];

    private static final Map<UUID, LookPacket> LOOKS = new HashMap<>();

    /** This client's own looks. */
    public static LookPacket fromConfig() {
        LookPacket m = new LookPacket();
        for (int i = 0; i < COUNT; i++) {
            Look l = GulpConfig.look(i);
            m.values[i][0] = l.wallHue;
            m.values[i][1] = l.wallShade;
            m.values[i][2] = l.acidHue;
            m.values[i][3] = l.acidShade;
            m.values[i][4] = l.acidLevel;
            m.values[i][5] = l.patternStrength;
            m.values[i][6] = l.patternSize;
            m.values[i][7] = l.patternHue;
            m.values[i][8] = l.wallPulse;
            m.flags[i] = (l.wallCustom ? 1 : 0) | (l.acidCustom ? 2 : 0) | (l.pattern.ordinal() & 15) << 2 | (l.patternCustom ? 64 : 0);
        }
        return m;
    }

    public static void encode(LookPacket m, FriendlyByteBuf buf) {
        for (int i = 0; i < COUNT; i++) {
            for (int j = 0; j < FLOATS; j++) buf.writeFloat(m.values[i][j]);
            buf.writeByte(m.flags[i]);
        }
    }

    public static LookPacket decode(FriendlyByteBuf buf) {
        LookPacket m = new LookPacket();
        for (int i = 0; i < COUNT; i++) {
            for (int j = 0; j < FLOATS; j++) m.values[i][j] = clamp01(buf.readFloat());
            m.flags[i] = buf.readByte() & 0x7F;
        }
        return m;
    }

    private static float clamp01(float f) {
        return f != f ? 0f : Math.max(0f, Math.min(1f, f)); // f != f is true only for NaN
    }

    public static void handle(LookPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer sender = c.getSender();
        c.enqueueWork(() -> {
            if (sender != null) LOOKS.put(sender.getUUID(), m);
        });
        c.setPacketHandled(true);
    }

    /** The looks this player sent, or null (a villager, or a player whose client has not sent them yet). */
    public static LookPacket of(UUID player) { return LOOKS.get(player); }

    public static void forget(UUID player) { LOOKS.remove(player); }
}
