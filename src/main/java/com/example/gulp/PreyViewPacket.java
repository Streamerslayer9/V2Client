package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> a swallowed player (twice a second): what the inside of their swallower's stomach looks like. The swallower's own look
 * (colours and pattern), which organ they are in, and the other prey in that same organ with how far along their digestion is.
 * It drives the inside view (InsideView), which only draws it. Nothing in it is a secret: it is what you would see in there.
 */
public class PreyViewPacket {
    public record Fellow(int uid, String type, float digest, boolean player) { }

    public final int organ;
    public final boolean hasLook;
    public final float[] look;
    public final int flags;
    public final List<Fellow> fellows;

    public PreyViewPacket(int organ, float[] look, int flags, List<Fellow> fellows) {
        this.organ = organ;
        this.hasLook = look != null;
        this.look = look == null ? new float[LookPacket.FLOATS] : look;
        this.flags = flags;
        this.fellows = fellows;
    }

    public static void encode(PreyViewPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.organ);
        buf.writeBoolean(m.hasLook);
        if (m.hasLook) {
            for (float f : m.look) buf.writeFloat(f);
            buf.writeByte(m.flags);
        }
        buf.writeVarInt(m.fellows.size());
        for (Fellow f : m.fellows) {
            buf.writeInt(f.uid());
            buf.writeUtf(f.type());
            buf.writeFloat(f.digest());
            buf.writeBoolean(f.player());
        }
    }

    public static PreyViewPacket decode(FriendlyByteBuf buf) {
        int organ = buf.readByte();
        boolean hasLook = buf.readBoolean();
        float[] look = null;
        int flags = 0;
        if (hasLook) {
            look = new float[LookPacket.FLOATS];
            for (int i = 0; i < look.length; i++) look[i] = buf.readFloat();
            flags = buf.readByte();
        }
        int n = Math.min(64, buf.readVarInt());
        List<Fellow> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new Fellow(buf.readInt(), buf.readUtf(), buf.readFloat(), buf.readBoolean()));
        return new PreyViewPacket(organ, look, flags, list);
    }

    public static void handle(PreyViewPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> InsideView.receive(m)));
        c.setPacketHandled(true);
    }
}
