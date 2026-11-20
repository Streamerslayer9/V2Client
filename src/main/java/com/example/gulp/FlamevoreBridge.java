package com.example.gulp;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Gives Flamevore digests Gulp XP and perks. Flamevore announces every finished digest on the normal Forge event bus
 * (its public API: DigestEvent.Digested). We only listen to that; Flamevore itself is never touched.
 * It is all done by name (reflection), so Gulp builds and runs without Flamevore, and if Flamevore changes its API the
 * bridge switches itself off with one line in latest.log instead of crashing.
 *
 * Only players whose swallowing system is Flamevore get this (Gulp-mode players get their XP from Gulp's own digesting).
 *  - a digested mob: Gulp's usual digest XP for that mob's max health, plus the Gourmet perk, and Hearty Meal heals
 *  - a digested player: the same flat XP Gulp gives for finishing a player
 */
public final class FlamevoreBridge {
    private FlamevoreBridge() { }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String API = "com.flame.flamevoremod.api.";

    private static Method getPred;        // VoreEvent.getPred()
    private static Method getPrey;        // DigestEvent.getPrey()
    private static Method preyMaxHealth;  // PreyInfo.maxHealth()
    private static Method preyIsPlayer;   // PreyInfo.player()
    private static boolean broken;

    /** Starts listening, if Flamevore is installed. Called once from Gulp's common setup (on both sides). */
    @SuppressWarnings("unchecked")
    public static void register() {
        if (!FlamevoreCompat.isLoaded()) return;
        try {
            Class<?> voreEvent = Class.forName(API + "event.VoreEvent");
            Class<?> digestEvent = Class.forName(API + "event.DigestEvent");
            Class<?> digested = Class.forName(API + "event.DigestEvent$Digested");
            Class<?> preyInfo = Class.forName(API + "PreyInfo");
            getPred = voreEvent.getMethod("getPred");
            getPrey = digestEvent.getMethod("getPrey");
            preyMaxHealth = preyInfo.getMethod("maxHealth");
            preyIsPlayer = preyInfo.getMethod("player");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class<Event>) digested, FlamevoreBridge::onDigested);
            LOGGER.info("[Gulp] Flamevore digests now give Gulp XP and perks (listening to {}).", digested.getName());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("[Gulp] Couldn't hook into Flamevore's digest events, so Flamevore digests won't give Gulp XP.", e);
        }
    }

    private static void onDigested(Event event) {
        if (broken) return;
        try {
            if (!(getPred.invoke(event) instanceof ServerPlayer pred) || pred.getServer() == null) return;
            Stomach s = StomachManager.get(pred.getServer()).of(pred.getUUID());
            if (s.swallowSystem != SwallowSystem.FLAMEVORE) return; // Gulp-mode players are paid by Gulp's own digesting

            Object prey = getPrey.invoke(event);
            if (Boolean.TRUE.equals(preyIsPlayer.invoke(prey))) {
                StomachLogic.gainXp(pred, s, 100f); // the same as Gulp gives for finishing a player
            } else {
                float maxHealth = ((Number) preyMaxHealth.invoke(prey)).floatValue();
                int hearty = s.rank(Perk.HEARTY);
                if (hearty > 0) pred.heal(0.5f * hearty); // Hearty Meal: 0.5 HP per rank for every mob digested
                StomachLogic.gainXp(pred, s, Stomach.digestXp(maxHealth) * (1f + 0.05f * s.rank(Perk.GOURMET))); // Gourmet: +5% XP per rank
            }
            StomachLogic.sync(pred, s);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true; // don't spam the log on every digest
            LOGGER.warn("[Gulp] Flamevore's digest event looked different than expected, so Flamevore XP is switched off.", e);
        }
    }
}
