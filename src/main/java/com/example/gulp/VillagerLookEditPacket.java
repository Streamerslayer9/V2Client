package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player changed a villager's stomach look on the villager's Stomach screen (Settings). Saved with the villager. */
public class VillagerLookEditPacket {
    public final int entityId;
    public final float[] look;
    public final int flags;

    public VillagerLookEditPacket(int entityId, float[] look, int flags) {
        this.entityId = entityId;
        this.look = look;
        this.flags = flags;
    }

    public static void encode(VillagerLookEditPacket m, FriendlyByteBuf buf) {
        buf.writeInt(m.entityId);
        for (float f : m.look) buf.writeFloat(f);
        buf.writeByte(m.flags);
    }

    public static VillagerLookEditPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        float[] look = new float[LookPacket.FLOATS];
        for (int i = 0; i < look.length; i++) look[i] = buf.readFloat();
        return new VillagerLookEditPacket(id, look, buf.readByte() & 0x7F);
    }

    public static void handle(VillagerLookEditPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer sender = c.getSender();
        c.enqueueWork(() -> {
            if (sender == null) return;
            Entity e = sender.level().getEntity(m.entityId);
            if (e == null || !McaIntegration.isVillager(e) || sender.distanceToSqr(e) > 64.0) return;
            VillagerLooks.save(e, m.look, m.flags); // clamped inside
        });
        c.setPacketHandled(true);
    }
}
