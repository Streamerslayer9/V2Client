package com.example.gulp;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Adds Gulp's bits to MCA's villager screen without touching MCA: a "Type: ..." box and a small level bar in the bottom-left
 * corner (below the chat lines, left of the hotbar), and a Perks button just left of the Talk button, for every villager.
 * The Perks button is added as an event listener only (clickable but not drawn by the screen), so it is drawn here by hand.
 * The positions are measured from MCA's own Talk / Interact buttons, so tweak the offsets below if they land a little off.
 * Anything unexpected on the screen just means nothing is added.
 */
@Mod.EventBusSubscriber(modid = Gulp.ID, value = Dist.CLIENT)
public final class McaScreenHooks {
    private McaScreenHooks() { }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean logged;

    private static final int LEFT_X = 18;   // left edge of MCA's info boxes
    private static final int BOTTOM_DY = 36; // our Type box's top edge sits this far above the bottom of the window: below the chat lines
    private static final int BAR_DY = 18;   // from the Type box down to the level bar
    private static final int BAR_H = 9;     // the bar is as wide as the Type box (see onRender), this is how tall
    private static final int PERKS_GAP = 3; // between the Perks button and Talk

    /** Shows a red diagnostic line on every screen that looks like MCA's (and logs every init). Set to false when it's sorted out. */
    private static final boolean DEBUG = false;
    private static String debugLine = "";
    private static Screen debugScreen;

    private static Screen shownScreen;
    private static LivingEntity villager;
    private static int topY;
    private static Button perksButton;

    /** You were just swallowed (by a villager or anyone else): the villager's menu and Perks screen close, you can't use them from inside. */
    static void closeOnHeld() {
        Minecraft mc = Minecraft.getInstance();
        Screen cur = mc.screen;
        if (cur != null && (cur instanceof VillagerPerksScreen || isInteractScreen(cur))) mc.setScreen(null);
    }

    /** MCA's villager screen: its class is called InteractScreen and lives in an mca package. */
    static boolean isInteractScreen(Screen screen) {
        String name = screen.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("mca") && name.endsWith("interactscreen");
    }

    /** The villager the screen is about: the first living entity in one of the screen's fields that isn't the player. */
    private static LivingEntity findVillager(Screen screen, Minecraft mc) {
        for (Class<?> c = screen.getClass(); c != null && c != Screen.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    if (f.get(screen) instanceof LivingEntity le && le != mc.player) return le;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // not readable: try the next field
                }
            }
        }
        return mc.crosshairPickEntity instanceof LivingEntity le ? le : null; // what you right-clicked
    }

    private static void dbg(Screen screen, String msg) {
        debugScreen = screen;
        debugLine = msg;
        if (DEBUG) LOGGER.info("[Gulp] perks-debug: {} (screen {})", msg, screen.getClass().getName());
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post e) {
        Screen screen = e.getScreen();
        if (!isInteractScreen(screen)) {
            if (screen == shownScreen) shownScreen = null;
            String n = screen.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (DEBUG && n.contains("mca")) dbg(screen, "NOT matched as MCA villager screen: " + screen.getClass().getName());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LivingEntity v = findVillager(screen, mc);
        shownScreen = null;
        perksButton = null;
        lastRight = -1;
        if (v == null) { dbg(screen, "FAIL: no villager found on screen"); return; }
        villager = v;
        shownScreen = screen;
        Net.send(20, v.getId()); // ask the server for their type and level

        // MCA's right-hand column of buttons, top to bottom: Talk, Interact, (room for ours), Family Tree
        List<AbstractWidget> right = new ArrayList<>();
        int total = 0;
        StringBuilder all = new StringBuilder();
        for (GuiEventListener l : e.getListenersList()) {
            if (l instanceof AbstractWidget w) {
                total++;
                if (all.length() < 150) all.append(w.getX()).append(',').append(w.getY()).append(w.visible ? "" : "(hid)").append(' ');
                if (w.visible && w.getX() >= screen.width * 0.6) right.add(w);
            }
        }
        right.sort(Comparator.comparingInt(AbstractWidget::getY));
        if (DEBUG) LOGGER.info("[Gulp] perks-debug: widgets {} window {}x{} right-side {} :: {}", total, screen.width, screen.height, right.size(), all);

        int bx, by, bw, bh;
        String why;
        boolean mainMenu = false;
        int step = 0;
        if (right.size() >= 2) {
            AbstractWidget talk = right.get(0);
            step = right.get(1).getY() - talk.getY();
            topY = talk.getY();
            for (int i = 1; i < right.size(); i++) {
                if (right.get(i).getY() - right.get(i - 1).getY() >= step * 2) mainMenu = true;
            }
            bw = talk.getWidth() * 3 / 4;
            bx = talk.getX() - bw - PERKS_GAP;
            by = talk.getY();
            bh = talk.getHeight();
            why = mainMenu ? "OK: anchored to Talk" : "sub-menu (no big gap): button hidden";
        } else {
            // couldn't find MCA's buttons: put it somewhere visible anyway so it can be seen
            bw = 60; bh = 18;
            bx = screen.width - 90 - PERKS_GAP - bw; // MCA's Talk sits at x = width - 90, y = 17, 80 wide (measured from a screenshot)
            by = 17;
            mainMenu = true;
            why = "FALLBACK: only " + right.size() + " right-side buttons of " + total + " widgets";
        }
        if (DEBUG && mainMenu == false) why += " (forcing it anyway for debugging)";
        { // always create it: reanchor() shows it only on the main page (a married villager has a different button layout)
            int id = v.getId();
            String name = v.getName().getString();
            perksButton = Button.builder(Component.literal("Perks"),
                            btn -> mc.setScreen(new VillagerPerksScreen(screen, id, name)))
                    .bounds(bx, by, bw, bh).build();
            perksButton.visible = mainMenu || DEBUG;
            e.addListener(perksButton);
        }
        dbg(screen, why + " | " + total + " widgets at init");
    }

    private static int lastRight = -1;

    /**
     * MCA's screen doesn't pass clicks on to buttons that were only added as listeners, so the Perks button never heard them.
     * We catch the click ourselves before MCA's screen does: if it lands on the visible Perks button, press it and use up the click.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre e) {
        if (e.getButton() != 0 || e.getScreen() != shownScreen || perksButton == null || villager == null) return;
        if (!perksButton.visible || !perksButton.active || !perksButton.isMouseOver(e.getMouseX(), e.getMouseY())) return;
        LOGGER.info("[Gulp] Perks button clicked for villager {}", villager.getName().getString());
        perksButton.playDownSound(Minecraft.getInstance().getSoundManager());
        perksButton.onPress();
        e.setCanceled(true);
    }

    /** True for MCA's own "Back" button, in any language (its text key or its text). */
    private static boolean isBackButton(AbstractWidget w) {
        Component msg = w.getMessage();
        if (msg.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && tc.getKey().toLowerCase(java.util.Locale.ROOT).contains("back")) return true;
        return msg.getString().trim().equalsIgnoreCase("back");
    }

    private static String lastState = "";

    /**
     * MCA adds its buttons after the init event, and rebuilds them when you open the Talk or Interact pages, so look again every
     * frame: snap the Perks button to Talk, and hide it whenever this is not MCA's main page. The Talk and Interact pages have
     * a Back button (the main page never does), which is how they are told apart from the main page.
     */
    private static void reanchor(Screen screen) {
        if (perksButton == null) return;
        List<AbstractWidget> right = new ArrayList<>();
        int total = 0;
        boolean back = false;
        StringBuilder labels = new StringBuilder();
        for (GuiEventListener l : screen.children()) {
            if (l instanceof AbstractWidget w && w != perksButton) {
                total++;
                if (!w.visible) continue;
                if (labels.length() < 200) labels.append('[').append(w.getMessage().getString()).append("] ");
                if (isBackButton(w)) back = true;
                if (w.getX() >= screen.width * 0.6) right.add(w);
            }
        }
        if (right.size() < 2) { perksButton.visible = DEBUG; return; }
        right.sort(Comparator.comparingInt(AbstractWidget::getY));
        AbstractWidget talk = right.get(0);
        int step = right.get(1).getY() - talk.getY();
        // The main page is the one without a Back button. (It used to also need a gap in the button column above Family Tree, but a
        // married villager's page has a different set of buttons and no such gap, so the Perks button never showed for them.)
        boolean mainMenu = !back;
        int bw = talk.getWidth() * 3 / 4;
        perksButton.setWidth(bw);
        perksButton.setX(talk.getX() - bw - PERKS_GAP);
        perksButton.setY(talk.getY());
        perksButton.setHeight(talk.getHeight());
        perksButton.visible = mainMenu || DEBUG;
        String state = (mainMenu ? "main" : "sub-page") + " | back=" + back + " | " + labels;
        if (!state.equals(lastState)) { // one line each time the page changes, so a wrong guess can be fixed from latest.log
            lastState = state;
            LOGGER.info("[Gulp] MCA villager screen is now: {}", state);
        }
        dbg(screen, "anchored to Talk at " + talk.getX() + "," + talk.getY() + " | " + total + " widgets, " + right.size() + " right, mainMenu=" + mainMenu);
    }

    /** Draws the dark purple label box and returns its width. */
    private static int box(GuiGraphics g, Font font, String text, int x, int y) {
        int w = font.width(text) + 8;
        g.fill(x, y, x + w, y + 16, 0xF0100010);
        g.fill(x, y, x + w, y + 1, 0xFF3C1A78);
        g.fill(x, y + 15, x + w, y + 16, 0xFF3C1A78);
        g.fill(x, y, x + 1, y + 16, 0xFF3C1A78);
        g.fill(x + w - 1, y, x + w, y + 16, 0xFF3C1A78);
        g.drawString(font, text, x + 4, y + 4, 0xFFFFFF, false);
        return w;
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post e) {
        if (DEBUG && e.getScreen() == debugScreen && !debugLine.isEmpty()) {
            e.getGuiGraphics().drawString(Minecraft.getInstance().font, "[Gulp debug] " + debugLine, 4, 4, 0xFF5555, true);
        }
        if (e.getScreen() != shownScreen || villager == null) return;
        reanchor(e.getScreen());
        VillagerInfoPacket info = ClientVillagerInfo.get(villager.getId());
        Font font = Minecraft.getInstance().font;
        GuiGraphics g = e.getGuiGraphics();
        // Children and teens have no levels or perks: hide the button and the type box (until the server has said they are adults)
        if (perksButton != null && (info == null || !info.adult)) perksButton.visible = false;
        if (perksButton != null) {
            perksButton.render(g, e.getMouseX(), e.getMouseY(), e.getPartialTick());
        }
        if (info == null || !info.adult) return;
        String kind = info.type == 2 ? "Pred" : info.type == 1 ? "Prey" : "Normal";
        VillagerTrait trait = VillagerTrait.byId(info.trait);
        if (trait != VillagerTrait.NONE) kind += " (" + trait.label.toLowerCase(java.util.Locale.ROOT) + ")";
        int typeY = e.getScreen().height - BOTTOM_DY;
        int boxW = box(g, font, "Type: " + kind, LEFT_X, typeY);
        // every villager has a level (1 to 10) and a bar
        int by = typeY + BAR_DY;
        boolean max = info.level >= VillagerHolding.MAX_LEVEL;
        float frac = max ? 1f : Math.min(1f, info.xp / (float) Math.max(1, info.xpNeeded));
        g.fill(LEFT_X - 1, by - 1, LEFT_X + boxW + 1, by + BAR_H + 1, 0xFF000000);
        g.fill(LEFT_X, by, LEFT_X + boxW, by + BAR_H, 0xFF222222);
        g.fill(LEFT_X, by, LEFT_X + (int) (boxW * frac), by + BAR_H, 0xFFE6C84A);
        // the level sits inside the bar (it used to sit to its right, on top of the hotbar)
        String lv = "Lv " + info.level;
        g.drawString(font, lv, LEFT_X + (boxW - font.width(lv)) / 2, by + 1, 0xFFFFFF, true);
    }
}
