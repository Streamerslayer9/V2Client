package com.example.gulp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/** Placeholder HUD built from plain rectangles. Swap these for your pixel art textures later. */
public final class GulpHud {
    private GulpHud() { }

    /** Draws text lined up against the right edge so long numbers grow to the left instead of off the screen. */
    private static void right(GuiGraphics g, Minecraft mc, String text, int rightEdge, int y, int color) {
        g.drawString(mc.font, text, rightEdge - mc.font.width(text), y, color);
    }

    /** What a swallowed player sees: who holds them, and what the Struggle key does right now. */
    private static void renderHeldOverlay(GuiGraphics g, Minecraft mc, int screenWidth) {
        int mode = ClientState.heldMode();
        if (mode == 0) return;
        String key = ClientModEvents.STRUGGLE_KEY.getTranslatedKeyMessage().getString();
        int cx = screenWidth / 2;
        int y = 24;
        if (mode == 1) {
            g.drawCenteredString(mc.font, "Swallowed by " + ClientState.heldHolder() + " [SOFT]", cx, y, 0xFFFFFF55);
            g.drawCenteredString(mc.font, "[" + key + "] rub the walls   [" + ClientModEvents.RELEASE_KEY
                    .getTranslatedKeyMessage().getString() + "] leave   [" + ClientModEvents.SCREEN_KEY
                    .getTranslatedKeyMessage().getString() + "] view storage", cx, y + 11, 0xFFDDDDDD);
            return;
        }
        if (mode == 3) { // a villager who doesn't like you grabbed you: struggle to get out
            g.drawCenteredString(mc.font, "Swallowed by " + ClientState.heldHolder() + " [ANGRY]", cx, y, 0xFFFF8855);
            g.drawCenteredString(mc.font, String.format("[%s] struggle (%.0f%% to break free)   They won't let you just leave",
                    key, ClientState.heldChance()), cx, y + 11, 0xFFDDDDDD);
            if (ClientState.heldProgress() > 0f) { // the server says this one can end in being digested
                g.drawCenteredString(mc.font, "Break free in time, or they will digest you!", cx, y + 22, 0xFFFF5555);
            }
            return;
        }
        g.drawCenteredString(mc.font, "Swallowed by " + ClientState.heldHolder() + " [HARD]", cx, y, 0xFFFF5555);
        int w = 120;
        int x = cx - w / 2;
        g.fill(x - 1, y + 11, x + w + 1, y + 21, 0xFF000000);
        g.fill(x, y + 12, x + w, y + 20, 0xFF333333);
        g.fill(x, y + 12, x + (int) (w * Math.min(1f, ClientState.heldProgress())), y + 20, 0xFFC0502E);
        g.drawCenteredString(mc.font, String.format("Digested %.0f%%   [%s] struggle (%.0f%% to break free)",
                ClientState.heldProgress() * 100f, key, ClientState.heldChance()), cx, y + 24, 0xFFDDDDDD);
        g.drawCenteredString(mc.font, "[" + ClientModEvents.SCREEN_KEY.getTranslatedKeyMessage().getString()
                + "] view their storage", cx, y + 35, 0xFFAAAAAA);
    }

    /** The inside of the stomach, for a swallowed player: the wall (your colour and pattern) with the acid pool at the bottom. */
    private static void renderInside(GuiGraphics g, int screenWidth, int screenHeight) {
        Look look = GulpConfig.look(0); // the server doesn't say which organ you are in, so this is the stomach's look
        StomachStyle.paintWall(g, 0, 0, screenWidth, screenHeight, look);
        int acidH = Math.round((screenHeight - 2) * look.acidLevel);
        if (acidH > 4) StomachStyle.paintAcid(g, 0, screenHeight - acidH, screenWidth, acidH, look);
    }

    public static void render(GuiGraphics g, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;

        if (ClientState.heldMode() > 0) {
            if (ClientState.insideView()) renderInside(g, screenWidth, screenHeight);
            String view = ClientModEvents.VIEW_KEY.getTranslatedKeyMessage().getString();
            g.drawCenteredString(mc.font, "[" + view + "] " + (ClientState.insideView() ? "see what they see" : "look inside the stomach"),
                    screenWidth / 2, screenHeight - 62, 0xFFDDDDDD);
        }
        renderHeldOverlay(g, mc, screenWidth);
        if (!ClientState.role().canEat()) return; // Prey / Neither: no stomach, so no stomach HUD

        int w = 100;
        int rightEdge = screenWidth - 8;
        int x = rightEdge - w;
        int y = screenHeight - 34;

        if (ClientState.points() > 0) {
            right(g, mc, "Perk points: " + ClientState.points()
                    + " [" + ClientModEvents.SCREEN_KEY.getTranslatedKeyMessage().getString() + "]", rightEdge, y - 24, 0xFFE64A);
        }
        if (!ClientState.usesGulpSwallow()) {
            // Flamevore is doing the swallowing: no capacity bar and no Soft / Hard mode, just the level and its XP bar.
            right(g, mc, "Stomach Lv." + ClientState.level(), rightEdge, y - 12, 0xFFFFFF);
            int xp = (int) (w * (ClientState.xp() / (float) Math.max(1, ClientState.xpNeeded())));
            g.fill(x - 1, y - 1, x + w + 1, y + 4, 0xFF000000);
            g.fill(x, y, x + w, y + 3, 0xFF222222);
            g.fill(x, y, x + xp, y + 3, 0xFFE6C84A);
            return;
        }
        int organ = ClientState.activeOrgan();
        right(g, mc, Organ.byId(organ).label + " Lv." + ClientState.level() + (ClientState.isHard() ? "  [HARD]" : "  [SOFT]"), rightEdge, y - 12, 0xFFFFFF);

        g.fill(x - 1, y - 1, x + w + 1, y + 9, 0xFF000000);
        g.fill(x, y, x + w, y + 8, 0xFF333333);
        int fill = (int) (w * Math.min(1f, ClientState.usedIn(organ) / ClientState.cap()));
        g.fill(x, y, x + fill, y + 8, ClientState.isHard() ? 0xFFC0502E : 0xFF5DB85D);
        right(g, mc, Stomach.fmtVolume(ClientState.usedIn(organ)) + " / " + Stomach.fmtVolume(ClientState.cap())
                + "  (" + ClientState.entries().size() + " held)", rightEdge, y + 11, 0xDDDDDD);

        int xpFill = (int) (w * (ClientState.xp() / (float) Math.max(1, ClientState.xpNeeded())));
        g.fill(x, y + 22, x + w, y + 25, 0xFF222222);
        g.fill(x, y + 22, x + xpFill, y + 25, 0xFFE6C84A);
    }
}
