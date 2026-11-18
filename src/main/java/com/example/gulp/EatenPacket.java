package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> a player who opened the Eaten tab: every villager they have swallowed, with soft and hard counts (EatenScreen shows it). */
public class EatenPacket {
    public record Row(String name, int soft, int hard) { }

    public final List<Row> rows;

    public EatenPacket(List<Row> rows) {
        this.rows = rows;
    }

    public static void encode(EatenPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.rows.size());
        for (Row r : m.rows) {
            buf.writeUtf(r.name(), 64);
            buf.writeVarInt(r.soft());
            buf.writeVarInt(r.hard());
        }
    }

    public static EatenPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(600, buf.readVarInt());
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) rows.add(new Row(buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));
        return new EatenPacket(rows);
    }

    public static void handle(EatenPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> EatenScreen.show(m.rows)));
        c.setPacketHandled(true);
    }
}
