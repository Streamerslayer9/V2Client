package com.example.gulp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The ways a villager earns XP besides comforting (that one lives in VillagerHolding). All of them are server side,
 * only ever for living ADULT villagers (McaIntegration.isAdultVillager checks MCA's age every time), and together they are
 * limited to mcaVillagerXpDailyCap XP per villager per Minecraft day (see VillagerHolding.addXp).
 *
 *   Talking: right-clicking a villager gives 2 XP, once every 5 minutes per villager and player.
 *   Gifts:   MCA's gift button uses the item in your hand and raises their hearts. So we remember what you held when you
 *            opened the villager, and if within a minute the item was used up and their hearts went up, that was a gift:
 *            3 XP, plus 1 for every 4 hearts it gave (at most 7 in all).
 *   Rides:   a soft ride in a stomach that ends with the villager let out gives 3 XP, plus 1 for every 30 seconds
 *            (at most 10 in all). Hard mode and digestion never give XP.
 */
public final class VillagerXp {
    private VillagerXp() { }

    private static final int TALK_XP = 2;
    private static final long TALK_COOLDOWN = 5 * 60 * 20L;
    private static final long GIFT_WINDOW = 60 * 20L;

    private record Talk(UUID player, UUID villager) { }

    /** What a player was holding when they opened a villager, to spot a gift afterwards. */
    private record Pending(UUID villager, ItemStack held, int hearts, long expires) { }

    private static final Map<Talk, Long> TALKED = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private static boolean enabled() {
        return GulpServerConfig.MCA_VILLAGER_XP_ENABLED.get();
    }

    /** The player right-clicked something (the caller has checked it is the main hand on the server and the villager isn't refusing). */
    static void onInteract(ServerPlayer p, Entity target) {
        if (McaIntegration.isAdultVillager(target)) McaIntegration.stampKind(target, p); // so the "Tell me about yourself" dialogue knows who is talking
        if (!enabled() || !McaIntegration.isAdultVillager(target)) return;
        long now = p.getServer().getTickCount();

        Talk key = new Talk(p.getUUID(), target.getUUID());
        Long next = TALKED.get(key);
        if (next == null || next <= now) {
            TALKED.put(key, now + TALK_COOLDOWN);
            VillagerHolding.addXp(target, p, TALK_XP + VillagerHolding.rankOf(target, VillagerPerk.CHATTY), true); // Chatty: +1 XP per rank
            int warm = VillagerHolding.rankOf(target, VillagerPerk.WARM_WELCOME);
            if (warm > 0) McaIntegration.addHearts(target, p, warm); // Warm Welcome: +1 heart per rank
        }

        ItemStack inHand = p.getMainHandItem();
        if (!inHand.isEmpty()) {
            PENDING.put(p.getUUID(), new Pending(target.getUUID(), inHand.copy(), McaIntegration.heartsOf(target, p), now + GIFT_WINDOW));
        } else {
            PENDING.remove(p.getUUID());
        }
    }

    /** Once a second: did anyone who opened a villager with an item in hand just give it away as a gift? */
    static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        TALKED.values().removeIf(t -> t <= now);
        if (PENDING.isEmpty()) return;
        var it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Pending pend = en.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null || pend.expires() <= now || !enabled()) {
                it.remove();
                continue;
            }
            Entity v = p.serverLevel().getEntity(pend.villager());
            if (!McaIntegration.isAdultVillager(v) || p.distanceToSqr(v) > 32.0 * 32.0) {
                it.remove();
                continue;
            }
            ItemStack held = p.getMainHandItem();
            boolean used = held.isEmpty() || !ItemStack.isSameItem(held, pend.held()) || held.getCount() < pend.held().getCount();
            if (!used) continue; // still holding it: they may be about to give it
            int gained = McaIntegration.heartsOf(v, p) - pend.hearts();
            it.remove();
            if (gained > 0) {
                VillagerHolding.addXp(v, p, Math.min(7, 3 + gained / 4), true);
            }
        }
    }

    /** A villager was let out of the player's stomach after a soft ride of this many ticks. */
    static void onRide(ServerPlayer p, Entity villager, int heldTicks) {
        if (!enabled() || !McaIntegration.isAdultVillager(villager)) return;
        VillagerHolding.addXp(villager, p, Math.min(10, 3 + heldTicks / 600), true);
    }
}
