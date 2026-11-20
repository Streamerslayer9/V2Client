package com.example.gulp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> client: everything the HUD and the stomach/perk screens need. */
public class SyncPacket {
    public boolean hard;
    public boolean instantDigest;
    public int level, xp, xpNeeded, points;
    public int role;
    public int swallowSystem;
    public boolean flamevoreOnServer; // the server decides whether the Gulp / Flamevore choice exists
    public int predator;
    public float itemFill;
    public int digestProgress;
    public ItemStack strip0 = ItemStack.EMPTY;
    public ItemStack strip1 = ItemStack.EMPTY;
    public float used, cap;
    public boolean multiOrgans;
    public int breastMilk, breastButter;
    public float breastChurn;
    public int activeOrgan, hardMask;
    public float[] organUsed = new float[Organ.COUNT];
    public int[] ranks = new int[Perk.values().length];
    public List<ClientState.EntryInfo> entries = new ArrayList<>();

    public static SyncPacket from(Stomach s) {
        SyncPacket m = new SyncPacket();
        m.hard = s.isHard(s.organ()) && GulpServerConfig.HARD_MODE_ENABLED.get(); // the organ you swallow into; SOFT if the server has hard mode off
        m.multiOrgans = GulpServerConfig.MULTI_ORGANS_ENABLED.get();
        m.breastMilk = s.breastMilk;
        m.breastButter = s.breastButter;
        m.breastChurn = Math.min(1f, s.breastChurn / (float) Math.max(1, GulpServerConfig.BREAST_CHURN_SECONDS.get() * 20));
        m.activeOrgan = s.organ();
        for (int i = 0; i < Organ.COUNT; i++) {
            if (s.isHard(i) && GulpServerConfig.HARD_MODE_ENABLED.get()) m.hardMask |= 1 << i;
            m.organUsed[i] = (float) s.usedIn(i);
        }
        m.instantDigest = s.instantDigest;
        m.level = s.level;
        m.xp = s.xp;
        m.xpNeeded = Stomach.xpForNext(s.level);
        m.used = (float) s.used();
        m.cap = (float) s.capacity();
        m.points = s.points;
        m.role = s.role.ordinal();
        m.swallowSystem = s.swallowSystem.ordinal();
        m.flamevoreOnServer = FlamevoreCompat.isLoaded();
        m.predator = s.predator;
        m.itemFill = s.usesGulpSwallow() ? s.itemFill() : 0f; // no belly growth when Flamevore does the swallowing
        m.digestProgress = s.digestProgress;
        m.strip0 = s.stripSlots.get(0).copy();
        m.strip1 = s.stripSlots.get(1).copy();
        for (Perk p : Perk.values()) m.ranks[p.ordinal()] = s.rank(p);
        for (CompoundTag e : s.contents) {
            CompoundTag ent = e.getCompound("Entity");
            int dt = Math.max(1, e.getInt("DigestTime"));
            m.entries.add(new ClientState.EntryInfo(
                    e.getInt("Uid"), StomachLogic.entryName(e), ent.getString("id"),
                    e.getFloat("Size"), ent.getFloat("Health"), e.getFloat("MaxHealth"),
                    Math.min(1f, e.getInt("Digest") / (float) dt),
                    Organ.of(e), Math.min(1f, e.getInt("Transit") / (float) Math.max(1, GulpServerConfig.TAIL_TRANSIT_SECONDS.get() * 20))));
        }
        return m;
    }

    public static void encode(SyncPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.hard);
        buf.writeBoolean(m.instantDigest);
        buf.writeInt(m.level);
        buf.writeInt(m.xp);
        buf.writeInt(m.xpNeeded);
        buf.writeFloat(m.used);
        buf.writeFloat(m.cap);
        buf.writeBoolean(m.multiOrgans);
        buf.writeVarInt(m.breastMilk);
        buf.writeVarInt(m.breastButter);
        buf.writeFloat(m.breastChurn);
        buf.writeByte(m.activeOrgan);
        buf.writeByte(m.hardMask);
        for (float f : m.organUsed) buf.writeFloat(f);
        buf.writeInt(m.points);
        buf.writeByte(m.role);
        buf.writeByte(m.swallowSystem);
        buf.writeBoolean(m.flamevoreOnServer);
        buf.writeInt(m.predator);
        buf.writeFloat(m.itemFill);
        buf.writeInt(m.digestProgress);
        buf.writeItem(m.strip0);
        buf.writeItem(m.strip1);
        buf.writeInt(m.ranks.length);
        for (int r : m.ranks) buf.writeInt(r);
        buf.writeInt(m.entries.size());
        for (ClientState.EntryInfo e : m.entries) {
            buf.writeInt(e.uid());
            buf.writeUtf(Component.Serializer.toJson(e.name()));
            buf.writeUtf(e.typeId());
            buf.writeFloat(e.size());
            buf.writeFloat(e.hp());
            buf.writeFloat(e.maxHp());
            buf.writeFloat(e.digest());
            buf.writeByte(e.organ());
            buf.writeFloat(e.transit());
        }
    }

    public static SyncPacket decode(FriendlyByteBuf buf) {
        SyncPacket m = new SyncPacket();
        m.hard = buf.readBoolean();
        m.instantDigest = buf.readBoolean();
        m.level = buf.readInt();
        m.xp = buf.readInt();
        m.xpNeeded = buf.readInt();
        m.used = buf.readFloat();
        m.cap = buf.readFloat();
        m.multiOrgans = buf.readBoolean();
        m.breastMilk = buf.readVarInt();
        m.breastButter = buf.readVarInt();
        m.breastChurn = buf.readFloat();
        m.activeOrgan = buf.readByte();
        m.hardMask = buf.readByte();
        for (int i = 0; i < m.organUsed.length; i++) m.organUsed[i] = buf.readFloat();
        m.points = buf.readInt();
        m.role = buf.readByte();
        m.swallowSystem = buf.readByte();
        m.flamevoreOnServer = buf.readBoolean();
        m.predator = buf.readInt();
        m.itemFill = buf.readFloat();
        m.digestProgress = buf.readInt();
        m.strip0 = buf.readItem();
        m.strip1 = buf.readItem();
        int perkCount = buf.readInt();
        for (int i = 0; i < perkCount; i++) {
            int v = buf.readInt();
            if (i < m.ranks.length) m.ranks[i] = v;
        }
        int n = buf.readInt();
        for (int i = 0; i < n; i++) {
            int uid = buf.readInt();
            Component name = Component.Serializer.fromJson(buf.readUtf());
            if (name == null) name = Component.empty();
            String type = buf.readUtf();
            float size = buf.readFloat();
            float hp = buf.readFloat();
            float maxHp = buf.readFloat();
            float dig = buf.readFloat();
            int organ = buf.readByte();
            float transit = buf.readFloat();
            m.entries.add(new ClientState.EntryInfo(uid, name, type, size, hp, maxHp, dig, organ, transit));
        }
        return m;
    }

    public static void handle(SyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> ClientState.apply(m));
        c.setPacketHandled(true);
    }
}
