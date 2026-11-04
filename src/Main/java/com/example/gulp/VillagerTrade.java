package com.example.gulp;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trade prices that follow how much an MCA villager likes you (their MCA hearts with you). MCA is never touched: when a
 * player opens a trading screen with an adult MCA villager, we add to the "special price" of every offer, the same thing
 * vanilla does for reputation. Vanilla clears it again when the trade ends.
 *
 *  - Lots of hearts: a discount. It starts at mcaTradeDiscountStartHearts and grows to mcaTradeMaxDiscountPercent at
 *    mcaTradeDiscountFullHearts.
 *  - Few hearts (below mcaLowAffectionHearts): a surcharge that grows over the next 40 hearts down, up to
 *    mcaTradeMaxSurchargePercent.
 * The villager says a line about it (in their own shy or confident way) when the prices are not normal, at most once every
 * two minutes per villager and player. Switch: mcaTradeEnabled.
 */
@Mod.EventBusSubscriber(modid = Gulp.ID)
public final class VillagerTrade {
    private VillagerTrade() { }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SURCHARGE_RANGE = 40;      // hearts below the low mark where the surcharge reaches its maximum
    private static final long LINE_COOLDOWN = 2 * 60 * 20L;

    /** What we added to one offer's price (negative = discount), to take it back out if vanilla didn't clear it. */
    private record Applied(MerchantOffer offer, int diff) { }

    private static final Map<UUID, List<Applied>> APPLIED = new HashMap<>();
    private static final Map<String, Long> LINE_SPOKEN = new HashMap<>();

    /**
     * The price change for this many hearts as a share of the base price: negative is a discount, positive a surcharge, 0 normal.
     */
    static float priceChange(int hearts) {
        int start = GulpServerConfig.MCA_TRADE_DISCOUNT_START_HEARTS.get();
        int full = GulpServerConfig.MCA_TRADE_DISCOUNT_FULL_HEARTS.get();
        int low = GulpServerConfig.MCA_LOW_AFFECTION_HEARTS.get();
        if (hearts > start) {
            float share = Math.min(1f, (hearts - start) / (float) Math.max(1, full - start));
            return -share * GulpServerConfig.MCA_TRADE_MAX_DISCOUNT_PERCENT.get() / 100f;
        }
        if (hearts < low) {
            float share = Math.min(1f, (low - hearts) / (float) SURCHARGE_RANGE);
            return share * GulpServerConfig.MCA_TRADE_MAX_SURCHARGE_PERCENT.get() / 100f;
        }
        return 0f;
    }

    /** "12% cheaper" / "8% dearer" / "normal", for /gulp villagerinfo. */
    static String describe(int hearts) {
        if (!GulpServerConfig.MCA_TRADE_ENABLED.get()) return "off (mcaTradeEnabled is false)";
        float change = priceChange(hearts);
        int percent = Math.round(Math.abs(change) * 100f);
        if (percent == 0) return "normal";
        return percent + (change < 0 ? "% cheaper" : "% dearer");
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getContainer() instanceof MerchantMenu menu)) return;
        undoPrevious(p);
        if (!GulpServerConfig.MCA_TRADE_ENABLED.get() || !McaIntegration.active()) return;
        Entity trader = traderOf(menu, p);
        if (trader == null) {
            LOGGER.info("[Gulp] Trade screen opened, but I could not tell which villager it belongs to, so prices are normal.");
            return;
        }
        if (!McaIntegration.isAdultVillager(trader)) {
            LOGGER.info("[Gulp] Trade with {} ({}): not an adult MCA villager, so prices are normal.", trader.getName().getString(), trader.getClass().getSimpleName());
            return;
        }

        int hearts = McaIntegration.heartsOf(trader, p);
        float change = priceChange(hearts) - 0.05f * VillagerHolding.rankOf(trader, VillagerPerk.HAGGLER); // Haggler: 5% off per rank
        if (change == 0f) {
            LOGGER.info("[Gulp] Trade with {}: {} hearts is in the normal price range (discount from {} hearts, surcharge below {}).",
                    trader.getName().getString(), hearts, GulpServerConfig.MCA_TRADE_DISCOUNT_START_HEARTS.get(), GulpServerConfig.MCA_LOW_AFFECTION_HEARTS.get());
            return;
        }
        MerchantOffers offers = menu.getOffers();
        if (offers == null) return;

        List<Applied> done = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            int base = offer.getBaseCostA().getCount();
            int diff;
            if (change < 0) {
                diff = -(int) Math.max(base >= 2 ? 1 : 0, Math.floor(base * -change)); // never below 1 emerald in the end: vanilla keeps it at 1
            } else {
                diff = (int) Math.floor(base * change + 0.5);
            }
            if (diff == 0) continue;
            offer.addToSpecialPriceDiff(diff);
            done.add(new Applied(offer, diff));
        }
        if (!done.isEmpty()) APPLIED.put(p.getUUID(), done);
        LOGGER.info("[Gulp] Trade with {}: {} hearts -> prices {}", trader.getName().getString(), hearts, describe(hearts));
        speak(p, trader, change);
    }

    /**
     * The villager this trading menu belongs to. The menu holds its merchant in a field (found by its type, so it works whatever the
     * field is called at runtime); if that fails, fall back to the nearby merchant whose trading player is this player.
     */
    private static Entity traderOf(MerchantMenu menu, ServerPlayer p) {
        try {
            for (Field f : MerchantMenu.class.getDeclaredFields()) {
                if (!Merchant.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                if (f.get(menu) instanceof Entity en) return en;
            }
        } catch (Throwable t) {
            LOGGER.warn("[Gulp] Could not read the trader from the trading menu: {}", t.toString());
        }
        return findTrader(p);
    }

    /** The villager the player is trading with: the merchant whose trading player is this player. */
    private static Entity findTrader(ServerPlayer p) {
        for (Entity en : p.level().getEntities(p, p.getBoundingBox().inflate(16), x -> x instanceof Merchant m && m.getTradingPlayer() == p)) {
            return en;
        }
        return null;
    }

    /** If our last change is still on those offers (vanilla didn't clear it), take it out so it never piles up. */
    private static void undoPrevious(ServerPlayer p) {
        List<Applied> old = APPLIED.remove(p.getUUID());
        if (old == null) return;
        for (Applied a : old) {
            int now = a.offer().getSpecialPriceDiff();
            boolean stillThere = a.diff() < 0 ? now <= a.diff() : now >= a.diff();
            if (stillThere) a.offer().addToSpecialPriceDiff(-a.diff());
        }
    }

    private static void speak(ServerPlayer p, Entity trader, float change) {
        long now = p.getServer().getTickCount();
        String key = p.getUUID() + "/" + trader.getUUID();
        Long until = LINE_SPOKEN.get(key);
        if (until != null && until > now) return;
        LINE_SPOKEN.put(key, now + LINE_COOLDOWN);
        LINE_SPOKEN.values().removeIf(t -> t <= now);
        VillagerTrait trait = McaIntegration.traitOf(trader);
        String line = change < 0 ? trait.tradeDiscountLine() : trait.tradeSurchargeLine();
        int percent = Math.round(Math.abs(change) * 100f);
        p.sendSystemMessage(Component.literal("<").append(trader.getName()).append("> " + line
                + " (" + (change < 0 ? "-" : "+") + percent + "% prices)"));
    }
}
