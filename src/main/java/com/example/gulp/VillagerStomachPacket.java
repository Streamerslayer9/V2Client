package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> a player looking at a villager's stomach: their stomach look, how many players they can hold and are holding, and who is in
 * there (the players they hold, and the villagers they were fed). open = true opens the Stomach screen (the button on the villager's
 * menu was pressed); false just refreshes one that is already open.
 */
public class VillagerStomachPacket {
    public record Row(String name, boolean player) { }

    public final int entityId;
    public final boolean open;
    public final int capacity;
    public final float[] look;
    public final int flags;
    public final List<Row> rows;

    public VillagerStomachPacket(int entityId, boolean open, int capacity, float[] look, int flags, List<Row> rows) {
        this.entityId = entityId;
        this.open = open;
        this.capacity = capacity;
        this.look = look;
        this.flags = flags;
        this.rows = rows;
    }

    public static void encode(VillagerStomachPacket m, FriendlyByteBuf buf) {
        buf.writeInt(m.entityId);
        buf.writeBoolean(m.open);
        buf.writeVarInt(m.capacity);
        for (float f : m.look) buf.writeFloat(f);
        buf.writeByte(m.flags);
        buf.writeVarInt(m.rows.size());
        for (Row r : m.rows) {
            buf.writeUtf(r.name(), 64);
            buf.writeBoolean(r.player());
        }
    }

    public static VillagerStomachPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        boolean open = buf.readBoolean();
        int cap = buf.readVarInt();
        float[] look = new float[LookPacket.FLOATS];
        for (int i = 0; i < look.length; i++) look[i] = buf.readFloat();
        int flags = buf.readByte();
        int n = Math.min(64, buf.readVarInt());
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) rows.add(new Row(buf.readUtf(64), buf.readBoolean()));
        return new VillagerStomachPacket(id, open, cap, look, flags, rows);
    }

    public static void handle(VillagerStomachPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VillagerStomachScreen.receive(m)));
        c.setPacketHandled(true);
    }
}
