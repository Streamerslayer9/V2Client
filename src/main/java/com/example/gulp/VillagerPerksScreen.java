package com.example.gulp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

import java.util.Locale;

/**
 * A villager's perks, opened from the Perks button on the MCA villager screen (every villager has one). Every villager has a
 * level and every adult villager earns XP: from talking, gifts and rides in a stomach (see VillagerXp) and, for preds, from
 * comforting players (see VillagerHolding). Every level after the first gives a pred villager one perk point, spent here with
 * the + buttons on the perks in VillagerPerk (only a few different perks fit at once: the perk slots). The server checks and saves everything on the villager itself (action 21),
 * so each villager in each world has its own level, points and perks. Only preds have perks.
 */
public class VillagerPerksScreen extends Screen {
    private static final int W = 320;
    private int H = 262; // grows with the wrapped perk text (see render)
    private static final int FEED_LEVEL = 5; // the level that starts feeding the player (see VillagerHolding.tick)
    private static final int ROW_H = 30;
    private static final int ROW_STEP = 32;
    private static final int ROWS_Y = 68; // where the first perk row starts, below the level, XP and slots lines

    private final Screen parent;
    private final int entityId;
    private final String villagerName;
    private final Button[] buyButtons = new Button[VillagerPerk.values().length];
    private Button storageButton;
    private int scroll;        // pixels the perk list is scrolled down
    private int contentH;      // total height of the perk rows
    private boolean dragging;  // dragging the scroll bar
    private Button climbButton;
    private Button convertButton;
    private Button backButton;
    private Button closeButton;

    public VillagerPerksScreen(Screen parent, int entityId, String villagerName) {
        super(Component.literal("Villager perks"));
        this.parent = parent;
        this.entityId = entityId;
        this.villagerName = villagerName;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        Net.send(20, entityId); // ask for fresh numbers
        int x = left();
        int y = top();
        backButton = addRenderableWidget(Button.builder(Component.literal("Back"), btn -> this.minecraft.setScreen(parent))
                .bounds(x + 8, y + H - 28, 60, 20).build());
        closeButton = addRenderableWidget(Button.builder(Component.literal("Close"), btn -> this.minecraft.setScreen(null))
                .bounds(x + W - 68, y + H - 28, 60, 20).build());
        storageButton = addRenderableWidget(Button.builder(Component.literal("Storage"), btn -> Net.send(22, entityId))
                .bounds(x + W / 2 - 40, y + H - 28, 80, 20).build());
        storageButton.visible = false;
        climbButton = addRenderableWidget(Button.builder(Component.literal("Climb In"), btn -> Net.send(24, entityId))
                .bounds(x + 8, y + H - 52, 148, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Force yourself into their tummy, whatever kind of villager they are. How they take it depends on their type and temper.")))
                .build());
        climbButton.visible = false;
        convertButton = addRenderableWidget(Button.builder(Component.literal("Ask to be a Pred"), btn -> Net.send(25, entityId))
                .bounds(x + W - 156, y + H - 52, 148, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Ask them to become a pred villager for good. They need to like you enough, and their old perks are forgotten (points refunded).")))
                .build());
        convertButton.visible = false;
        for (VillagerPerk perk : VillagerPerk.values()) {
            int i = perk.ordinal();
            buyButtons[i] = addRenderableWidget(Button.builder(Component.literal("+"), btn -> Net.send(21, entityId << 4 | i))
                    .bounds(x + W - 30, y + ROWS_Y + i * ROW_STEP + 5, 20, 20).build());
            buyButtons[i].visible = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private static String num(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static final int TEXT_W = W - 14 - 8 - 6 - 22 - 6; // row text area: left pad .. keeps clear of the + button
    private static final int LINE = 9;

    private List<FormattedCharSequence> wrap(String text, int maxLines) {
        List<FormattedCharSequence> all = font.split(FormattedText.of(text), TEXT_W);
        if (all.size() <= maxLines) return all;
        // too long even wrapped: cut to maxLines and end the last line with "..."
        String cut = text;
        List<FormattedCharSequence> out = font.split(FormattedText.of(cut), TEXT_W);
        int width = TEXT_W * maxLines - font.width("...") - 6;
        cut = font.plainSubstrByWidth(text, Math.max(10, width)) + "...";
        out = font.split(FormattedText.of(cut), TEXT_W);
        return out.size() > maxLines ? out.subList(0, maxLines) : out;
    }

    private int rowHeight(String now, String next) {
        return 4 + LINE + (wrap(now, 2).size() + wrap(next, 2).size()) * LINE + 2;
    }

    private void perkRow(GuiGraphics g, int x, int y, int h, String name, String now, String next, boolean unlocked) {
        g.fill(x + 8, y, x + W - 8, y + h, 0x40FFFFFF);
        g.drawString(font, font.plainSubstrByWidth(name, TEXT_W), x + 14, y + 4, unlocked ? 0xFFFFFF : 0x888888, true);
        int ty = y + 4 + LINE;
        for (FormattedCharSequence l : wrap(now, 2)) { g.drawString(font, l, x + 14, ty, unlocked ? 0xBBBBBB : 0x777777, false); ty += LINE; }
        for (FormattedCharSequence l : wrap(next, 2)) { g.drawString(font, l, x + 14, ty, 0x888888, false); ty += LINE; }
    }

    private void perkRow(GuiGraphics g, int x, int y, String name, String now, String next, boolean unlocked) {
        perkRow(g, x, y, rowHeight(now, next), name, now, next, unlocked);
    }

    private int viewTop() { return top() + ROWS_Y; }
    private int viewBot() { return top() + H - 58; }

    private void scrollTo(double mouseY) {
        int viewH = Math.max(20, viewBot() - viewTop());
        int maxScroll = Math.max(0, contentH - viewH);
        int thumbH = Math.max(12, viewH * viewH / Math.max(1, contentH));
        double f = (mouseY - viewTop() - thumbH / 2.0) / Math.max(1, viewH - thumbH);
        scroll = (int) Math.max(0, Math.min(maxScroll, f * maxScroll));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - (int) (delta * 14)); // the upper limit is applied in render
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int tx = left() + W - 8;
        if (button == 0 && mx >= tx && mx <= tx + 8 && my >= viewTop() && my <= viewBot()) {
            dragging = true;
            scrollTo(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { scrollTo(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left();
        int y = top();

        g.fill(x, y, x + W, y + H, 0xE6141414);
        g.fill(x, y, x + W, y + 1, 0xFF666666);
        g.fill(x, y + H - 1, x + W, y + H, 0xFF666666);
        g.fill(x, y, x + 1, y + H, 0xFF666666);
        g.fill(x + W - 1, y, x + W, y + H, 0xFF666666);

        g.drawString(font, font.plainSubstrByWidth(villagerName + " - Perks", W - 16), x + 8, y + 8, 0xFFFFFF, true);

        VillagerInfoPacket info = ClientVillagerInfo.get(entityId);
        for (Button b : buyButtons) if (b != null) b.visible = false; // shown again below, only on the real perks page
        boolean adultNow = info != null && info.adult;
        if (climbButton != null && convertButton != null) {
            climbButton.visible = adultNow;
            convertButton.visible = adultNow && info.type != 2;
            if (convertButton.visible) {
                climbButton.setX(x + 8);
            } else {
                climbButton.setX(x + W / 2 - 74); // only Climb In: centred
            }
            convertButton.setX(x + W - 156);
            climbButton.setY(y + H - 52);
            convertButton.setY(y + H - 52);
        }
        if (storageButton != null) {
            storageButton.visible = info != null && info.type == 2;
            if (info != null) storageButton.setMessage(Component.literal("Storage (" + VillagerStorage.rowsFor(info.level) * 9 + ")"));
        }
        if (info == null) {
            H = 120;
            g.drawString(font, "Loading...", x + 8, y + 28, 0xAAAAAA, false);
        } else {
            int level = info.level;
            boolean max = level >= VillagerHolding.MAX_LEVEL;
            g.drawString(font, "Level " + level + (max ? " (max)" : ""), x + 8, y + 24, 0xE6C84A, false);
            VillagerTrait trait = VillagerTrait.byId(info.trait);
            String kindWord = info.type == 2 ? "pred" : info.type == 1 ? "prey" : "villager";
            String note = trait != VillagerTrait.NONE ? trait.label + " " + kindWord : "Normal villager";
            g.drawString(font, note, x + W - 8 - font.width(note), y + 24, 0x999999, false);
            int bx = x + 8;
            int by = y + 36;
            int bw = W - 16;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF000000);
            g.fill(bx, by, bx + bw, by + 6, 0xFF222222);
            float frac = max ? 1f : Math.min(1f, info.xp / (float) Math.max(1, info.xpNeeded));
            g.fill(bx, by, bx + (int) (bw * frac), by + 6, 0xFFE6C84A);
            if (!max) g.drawString(font, info.xp + " / " + info.xpNeeded + " XP to the next level", bx, by + 10, 0x999999, false);

            boolean pred = true; // every adult villager has perks now (their own list, see VillagerPerk.forType)
            if (pred) {
                String pts = info.points + (info.points == 1 ? " perk point" : " perk points");
                g.drawString(font, pts, bx + bw - font.width(pts), by + 10, info.points > 0 ? 0x77DD77 : 0x999999, false);
            }
            int used = 0;
            for (int r : info.ranks) if (r > 0) used++;
            int slots = VillagerPerk.slotsFor(level);
            int roomy = VillagerPerk.ROOMY_BELLY.ordinal() < info.ranks.length ? info.ranks[VillagerPerk.ROOMY_BELLY.ordinal()] : 0;
            if (pred) {
                String line = "Perk slots " + used + "/" + slots + "    Holds up to " + VillagerPerk.capacityFor(level, roomy) + " at once";
                g.drawString(font, line, bx, y + 57, used >= slots ? 0xE6C84A : 0x999999, false);
            }
            int row = y + ROWS_Y;
            int viewTop = y + ROWS_Y;
            int viewBot = y + H - 58;
            int viewH = Math.max(20, viewBot - viewTop);
            int off = -scroll;
            g.enableScissor(x + 8, viewTop, x + W - 8, viewBot); // running offset: rows have different heights now that long text wraps
            for (VillagerPerk perk : VillagerPerk.perksFor(info.type)) {
                int i = perk.ordinal();
                int rank = i < info.ranks.length ? info.ranks[i] : 0;
                String name = perk.title + (perk.active ? " (active)" : "") + "  " + rank + "/" + perk.maxRank;
                String now;
                String next;
                switch (perk) {
                    case SOOTHING -> {
                        now = "Heals you " + num(VillagerHolding.healPerSecond(level, trait, rank)) + " HP every second.";
                        next = rank >= perk.maxRank ? "Fully learned."
                                : "Next rank: " + num(VillagerHolding.healPerSecond(level, trait, rank + 1)) + " HP per second.";
                    }
                    case EMBRACE -> {
                        now = "Holds you for up to " + (VillagerHolding.maxTicks(level, trait, rank) / 20) + " seconds.";
                        next = rank >= perk.maxRank ? "Fully learned."
                                : "Next rank: " + (VillagerHolding.maxTicks(level, trait, rank + 1) / 20) + " seconds.";
                    }
                    case ROOMY_BELLY -> {
                        now = "Holds up to " + VillagerPerk.capacityFor(level, rank) + " players at once.";
                        next = rank >= perk.maxRank ? "Fully learned."
                                : "Next rank: " + VillagerPerk.capacityFor(level, rank + 1) + " at once.";
                    }
                    case EAGER_RIDER -> {
                        now = rank > 0 ? "Asks " + (rank * 35) + "% more often and needs " + (rank * 10) + "% fewer hearts." : perk.description;
                        next = rank >= perk.maxRank ? "Fully learned."
                                : "Next rank: " + ((rank + 1) * 35) + "% more often, " + ((rank + 1) * 10) + "% fewer hearts.";
                    }
                    case SNUG_PASSENGER -> {
                        now = rank > 0 ? "+" + (3 * rank) + " hearts with you after every safe ride." : perk.description;
                        next = rank >= perk.maxRank ? "Fully learned." : "Next rank: +" + (3 * (rank + 1)) + " hearts per ride.";
                    }
                    case PARTING_GIFT -> {
                        now = rank > 0 ? "Up to " + VillagerPerk.partingGiftXp(rank, 6000) + " XP after a ride of 30+ s (every 10 min)." : perk.description;
                        next = rank >= perk.maxRank ? "Fully learned." : "Next rank: up to " + VillagerPerk.partingGiftXp(rank + 1, 6000) + " XP.";
                    }
                    case HAGGLER -> {
                        now = rank > 0 ? (rank * 5) + "% off when you trade with them." : perk.description;
                        next = rank >= perk.maxRank ? "Fully learned." : "Next rank: " + ((rank + 1) * 5) + "% off.";
                    }
                    case CHATTY -> {
                        now = rank > 0 ? "Earns " + (2 + rank) + " XP per talk instead of 2." : perk.description;
                        next = rank >= perk.maxRank ? "Fully learned." : "Next rank: " + (3 + rank) + " XP per talk.";
                    }
                    case WARM_WELCOME -> {
                        now = rank > 0 ? "+" + rank + " heart" + (rank > 1 ? "s" : "") + " with you every time you talk." : perk.description;
                        next = rank >= perk.maxRank ? "Fully learned." : "Next rank: +" + (rank + 1) + " hearts per talk.";
                    }
                    case SECOND_WIND -> {
                        now = rank > 0 ? "A rest that ends well gives you Regeneration for " + (VillagerPerk.secondWindTicks(rank) / 20) + " s."
                                : perk.description;
                        next = rank >= perk.maxRank ? "Fully learned."
                                : "Next rank: " + (VillagerPerk.secondWindTicks(rank + 1) / 20) + " seconds of Regeneration.";
                    }
                    default -> {
                        now = rank > 0 ? "Feeds you " + rank + " hunger every 2 seconds." : perk.description;
                        next = rank >= perk.maxRank ? "Fully learned."
                                : "Next rank: " + (rank + 1) + " hunger every 2 seconds.";
                    }
                }
                if (pred && rank == 0 && used >= slots) next = "No free perk slot (a new one at a higher level).";
                int rh = rowHeight(now, next);
                perkRow(g, x, row + off, rh, name, now, next, pred && rank > 0);
                int by2 = row + off + (rh - 20) / 2;
                buyButtons[i].setY(by2);
                boolean inView = by2 >= viewTop && by2 + 20 <= viewBot;
                buyButtons[i].visible = inView && pred && rank < perk.maxRank;
                buyButtons[i].active = info.points > 0 && (rank > 0 || used < slots);
                off += rh + 2;
            }
            g.disableScissor();
            contentH = off + scroll; // total height of the rows
            int maxScroll = Math.max(0, contentH - viewH);
            scroll = Math.max(0, Math.min(scroll, maxScroll));
            if (maxScroll > 0) { // scroll bar to the right of the list
                int tx = x + W - 6;
                int thumbH = Math.max(12, viewH * viewH / contentH);
                int thumbY = viewTop + (int) ((viewH - thumbH) * (scroll / (float) maxScroll));
                g.fill(tx, viewTop, tx + 4, viewBot, 0xFF222222);
                g.fill(tx, thumbY, tx + 4, thumbY + thumbH, dragging ? 0xFFE6C84A : 0xFF999999);
            }
            int wanted = ROWS_Y + contentH + 36 + 24;
            H = Math.max(120, Math.min(wanted, height - 8)); // the panel never gets taller than the window: the rest scrolls
        }
        if (backButton != null) backButton.setY(y + H - 28);
        if (closeButton != null) closeButton.setY(y + H - 28);
        if (storageButton != null) storageButton.setY(y + H - 28);

        super.render(g, mouseX, mouseY, partialTick);
    }
}
