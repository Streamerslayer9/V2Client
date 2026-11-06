package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: what kind of villager this is and their comfort level, for the MCA villager screen (asked for with action 20). */
public class VillagerInfoPacket {
    public final int entityId;
    /** False for children and teens: the villager screen shows nothing of ours for them. */
    public final boolean adult;
    /** 0 normal, 1 prey, 2 pred. */
    public final int type;
    /** VillagerTrait ordinal: 0 none, 1 shy, 2 confident. */
    public final int trait;
    /** The rank in each VillagerPerk, by ordinal. */
    public final int[] ranks;
    /** Perk points not spent yet. */
    public final int points;
    public final int level;
    public final int xp;
    public final int xpNeeded;

    public VillagerInfoPacket(int entityId, boolean adult, int type, int trait, int[] ranks, int points, int level, int xp, int xpNeeded) {
        this.entityId = entityId;
        this.adult = adult;
        this.type = type;
        this.trait = trait;
        this.ranks = ranks;
        this.points = points;
        this.level = level;
        this.xp = xp;
        this.xpNeeded = xpNeeded;
    }

    public static void encode(VillagerInfoPacket m, FriendlyByteBuf buf) {
        buf.writeInt(m.entityId);
        buf.writeBoolean(m.adult);
        buf.writeByte(m.type);
        buf.writeByte(m.trait);
        for (int r : m.ranks) buf.writeByte(r);
        buf.writeByte(m.points);
        buf.writeByte(m.level);
        buf.writeInt(m.xp);
        buf.writeInt(m.xpNeeded);
    }

    public static VillagerInfoPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        boolean adult = buf.readBoolean();
        int type = buf.readByte();
        int trait = buf.readByte();
        int[] ranks = new int[VillagerPerk.values().length];
        for (int i = 0; i < ranks.length; i++) ranks[i] = buf.readByte();
        int points = buf.readByte();
        return new VillagerInfoPacket(entityId, adult, type, trait, ranks, points, buf.readByte(), buf.readInt(), buf.readInt());
    }

    public static void handle(VillagerInfoPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> ClientVillagerInfo.put(m));
        c.setPacketHandled(true);
    }
}
