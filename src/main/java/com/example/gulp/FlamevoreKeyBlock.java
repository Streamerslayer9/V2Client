package com.example.gulp;

import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns off Flamevore's "eat" key (X by default) unless Flamevore is the player's chosen swallowing system.
 * We never edit Flamevore: before it gets a chance to read the key each tick, we simply use up any presses of it.
 * The key is found by looking through the game's key list for a Flamevore key that is X by default (or is named like
 * eat / swallow / devour). What was found is written to latest.log under "[Gulp] Flamevore key".
 */
@Mod.EventBusSubscriber(modid = Gulp.ID, value = Dist.CLIENT)
public final class FlamevoreKeyBlock {
    private FlamevoreKeyBlock() { }

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Flamevore's eat key(s), found the first time we need them. */
    private static List<KeyMapping> eatKeys;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return; // the start of the tick, ahead of anything that reads keys
        // Needs Flamevore here (it has the key) and on the server (the server decides who may use it).
        if (!FlamevoreCompat.isLoaded() || !ClientState.flamevoreActive() || ClientState.swallowSystem() == SwallowSystem.FLAMEVORE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (eatKeys == null) eatKeys = findEatKeys(mc);
        for (KeyMapping key : eatKeys) {
            while (key.consumeClick()) {
                // use up every press
            }
            key.setDown(false); // and don't let a held key count either
        }
    }

    private static List<KeyMapping> findEatKeys(Minecraft mc) {
        List<KeyMapping> found = new ArrayList<>();
        String id = FlamevoreCompat.modId().toLowerCase(Locale.ROOT);
        for (KeyMapping key : mc.options.keyMappings) {
            String name = key.getName().toLowerCase(Locale.ROOT);
            String category = key.getCategory().toLowerCase(Locale.ROOT);
            boolean theirs = name.contains("flamevore") || category.contains("flamevore")
                    || (!id.isEmpty() && (name.contains(id) || category.contains(id)));
            if (!theirs) continue;
            boolean eat = key.getDefaultKey().getValue() == GLFW.GLFW_KEY_X
                    || name.contains("eat") || name.contains("swallow") || name.contains("devour");
            LOGGER.info("[Gulp] Flamevore key '{}' (category '{}', default {}){}", key.getName(), key.getCategory(),
                    key.getDefaultKey().getName(), eat ? " -> switched off while Gulp swallowing is chosen" : "");
            if (eat) found.add(key);
        }
        if (found.isEmpty()) LOGGER.info("[Gulp] No Flamevore eat key found, so nothing is switched off.");
        return found;
    }
}
