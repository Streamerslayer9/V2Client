package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server actions (see StomachLogic.handleAction):
 *  0 swallow (arg = target entity id, or -1) | 1 toggle mode | 2 release last | 3 release all
 *  4 release entry (arg = uid) | 5 digest entry now (arg = uid) | 6 buy perk (arg = perk index, plus ranks << 8)
 *  7 open stomach storage (or look at your swallower's) | 8 set role (arg = Role ordinal) | 9 swallow an inventory item
 *  10 digest the first storage slot | 11 struggle | 12 strip enchants | 13 transfer enchants
 *  15 click an Enchant Stripper box | 16 destroy enchants | 17 instant digest button on/off | 18 right-click eat a storage slot
 *  19 set the swallowing system (arg = SwallowSystem ordinal: 1 Gulp, 2 Flamevore)
 *  20 ask for a villager's type and comfort level (arg = villager entity id)
 *  21 buy a villager perk (arg = villager entity id << 4 | VillagerPerk index, up to 16 perks)
 *  22 open a pred villager's item storage (arg = villager entity id)
 *  24 Climb In: force yourself into an adult villager's tummy (arg = villager entity id)
 *  25 Ask to be a Pred: ask a prey or normal villager to become a pred (arg = villager entity id)
 */
public class ActionPacket {
    private final int action;
    private final int arg;

    public ActionPacket(int action, int arg) {
        this.action = action;
        this.arg = arg;
    }

    public static void encode(ActionPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.action);
        buf.writeInt(m.arg);
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        return new ActionPacket(buf.readByte(), buf.readInt());
    }

    public static void handle(ActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer sender = c.getSender();
        c.enqueueWork(() -> StomachLogic.handleAction(sender, m.action, m.arg));
        c.setPacketHandled(true);
    }
}
