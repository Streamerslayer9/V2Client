package com.example.gulp;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only game events (Forge event bus): key presses. */
@Mod.EventBusSubscriber(modid = Gulp.ID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() { }

    /** The swallowing-system choice screen is opened by itself once per login (V and Settings reopen it). */
    private static boolean choicePrompted;

    /** Leaving a world or server: stop the music and forget the old stomach so it doesn't leak into the next one. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientState.reset();
        ClientVillagerInfo.clear();
        choicePrompted = false;
        CpmDriver.reset();
        ScreenLoop.stop();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        CpmDriver.tick(); // Custom Player Models belly sliders (does nothing without CPM)

        // Flamevore installed and no choice made yet: ask which mod should do the swallowing.
        if (!choicePrompted && mc.screen == null && ClientState.needsSwallowChoice()) {
            choicePrompted = true;
            mc.setScreen(new SwallowChoiceScreen());
        }

        while (ClientModEvents.SWALLOW_KEY.consumeClick()) {
            if (ClientState.needsSwallowChoice()) { // nothing to swallow with until they choose
                if (mc.screen == null) mc.setScreen(new SwallowChoiceScreen());
                continue;
            }
            // Use what the player actually sees on their screen, so wandering mobs and lag don't cause misses.
            Entity target = TargetFinder.find(mc.player, TargetFinder.REACH);
            Net.send(0, target == null ? -1 : target.getId());
        }
        while (ClientModEvents.STRUGGLE_KEY.consumeClick()) Net.send(11);
        while (ClientModEvents.MODE_KEY.consumeClick()) Net.send(1);
        while (ClientModEvents.RELEASE_KEY.consumeClick()) Net.send(mc.player.isShiftKeyDown() ? 3 : 2);
        while (ClientModEvents.SCREEN_KEY.consumeClick()) {
            if (ClientState.heldMode() > 0) { // swallowed: look at the swallower's item storage instead
                if (mc.screen == null) Net.send(7);
                continue;
            }
            // Pred / Both get the stomach menu. Prey / Neither have no stomach, so the key opens Settings (to change role).
            if (mc.screen == null) mc.setScreen(ClientState.role().canEat() ? new StomachScreen() : new SettingsScreen());
        }
    }
}
