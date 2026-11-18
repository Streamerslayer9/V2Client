package com.example.gulp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The Eaten tab of the stomach screen: every villager you have swallowed and how many times, in soft mode and in hard mode.
 * Filter by All / Soft / Hard, sort by most eaten, name, soft or hard, and page with the buttons or the mouse wheel.
 * Drawn on your own stomach wall (your colour and pattern from Settings > Stomach look). Read only: nothing here changes anything.
 */
public class EatenScreen extends Screen {
    private static final int W = 250;
    private static final int ROWS = 7;
    private static final int ROW_H = 22;
    private static final int HEADER = 62;
    private static final int FOOTER = 56;
    private static final int H = HEADER + ROWS * ROW_H + FOOTER;

    private final List<EatenPacket.Row> all;
    private int page;
    private int filter;   // 0 all, 1 soft only, 2 hard only
    private int sort;     // 0 most eaten, 1 name, 2 most soft, 3 most hard
    private static final String[] SORT_NAMES = {"Most eaten", "Name", "Most soft", "Most hard"};

    private EatenScreen(List<EatenPacket.Row> rows) {
        super(Component.literal("Villagers eaten"));
        this.all = rows;
    }

    /** Opens the screen (called when the server's answer arrives). */
    public static void show(List<EatenPacket.Row> rows) {
        Minecraft.getInstance().setScreen(new EatenScreen(rows));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private List<EatenPacket.Row> shown() {
        List<EatenPacket.Row> list = new ArrayList<>();
        for (EatenPacket.Row r : all) {
            if (filter == 1 && r.soft() <= 0) continue;
            if (filter == 2 && r.hard() <= 0) continue;
            list.add(r);
        }
        Comparator<EatenPacket.Row> byName = Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT));
        switch (sort) {
            case 1 -> list.sort(byName);
            case 2 -> list.sort(Comparator.comparingInt(EatenPacket.Row::soft).reversed().thenComparing(byName));
            case 3 -> list.sort(Comparator.comparingInt(EatenPacket.Row::hard).reversed().thenComparing(byName));
            default -> list.sort(Comparator.comparingInt((EatenPacket.Row r) -> r.soft() + r.hard()).reversed().thenComparing(byName));
        }
        return list;
    }

    @Override
    protected void init() {
        int x = left();
        int y = top();
        int pages = Math.max(1, (shown().size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(page, pages - 1));

        String[] filterNames = {"All", "Soft", "Hard"};
        for (int i = 0; i < 3; i++) {
            final int f = i;
            Button b = Button.builder(Component.literal(filterNames[i]), btn -> { filter = f; page = 0; rebuildWidgets(); })
                    .bounds(x + 8 + i * 54, y + 30, 52, 20).build();
            b.active = filter != i;
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.literal("Sort: " + SORT_NAMES[sort]), btn -> { sort = (sort + 1) % SORT_NAMES.length; page = 0; rebuildWidgets(); })
                .bounds(x + W - 88, y + 30, 80, 20).build());

        int fy = y + H - 52;
        Button prev = Button.builder(Component.literal("<"), btn -> { page--; rebuildWidgets(); }).bounds(x + 8, fy, 24, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), btn -> { page++; rebuildWidgets(); }).bounds(x + 34, fy, 24, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> this.minecraft.setScreen(new StomachScreen()))
                .bounds(x + 8, y + H - 28, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(x + W - 68, y + H - 28, 60, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int pages = Math.max(1, (shown().size() + ROWS - 1) / ROWS);
        int np = Math.max(0, Math.min(pages - 1, page - (int) Math.signum(delta)));
        if (np != page) {
            page = np;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left();
        int y = top();
        StomachStyle.paintWall(g, x, y, W, H, GulpConfig.look(0));
        g.fill(x - 1, y - 1, x + W + 1, y, 0xFF888888);
        g.fill(x - 1, y + H, x + W + 1, y + H + 1, 0xFF888888);
        g.fill(x - 1, y, x, y + H, 0xFF888888);
        g.fill(x + W, y, x + W + 1, y + H, 0xFF888888);

        int softTotal = 0, hardTotal = 0;
        for (EatenPacket.Row r : all) { softTotal += r.soft(); hardTotal += r.hard(); }
        g.drawString(font, "Villagers eaten", x + 8, y + 7, 0xFFFFFFFF, true);
        g.drawString(font, softTotal + " soft, " + hardTotal + " hard, " + all.size() + " different", x + W - 8 - font.width(softTotal + " soft, " + hardTotal + " hard, " + all.size() + " different"), y + 7, 0xFFCCCCCC, false);

        List<EatenPacket.Row> list = shown();
        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        g.drawString(font, "Villager", x + 12, y + HEADER - 10, 0xFFAAAAAA, false);
        g.drawString(font, "Soft", x + W - 104, y + HEADER - 10, 0xFF7CDB7C, false);
        g.drawString(font, "Hard", x + W - 52, y + HEADER - 10, 0xFFE06060, false);
        if (list.isEmpty()) {
            String msg = all.isEmpty() ? "You haven't eaten any villagers yet." : "None match this filter.";
            g.drawCenteredString(font, msg, x + W / 2, y + HEADER + 50, 0xFFDDDDDD);
        }
        int start = page * ROWS;
        for (int i = 0; i < ROWS && start + i < list.size(); i++) {
            EatenPacket.Row r = list.get(start + i);
            int ry = y + HEADER + i * ROW_H;
            if (i % 2 == 0) g.fill(x + 6, ry, x + W - 6, ry + ROW_H - 2, 0x22000000);
            g.drawString(font, font.plainSubstrByWidth(r.name(), W - 130), x + 12, ry + 7, 0xFFFFFFFF, true);
            g.drawString(font, Integer.toString(r.soft()), x + W - 104, ry + 7, r.soft() > 0 ? 0xFF7CDB7C : 0xFF777777, false);
            g.drawString(font, Integer.toString(r.hard()), x + W - 52, ry + 7, r.hard() > 0 ? 0xFFE06060 : 0xFF777777, false);
        }
        g.drawString(font, "Page " + (page + 1) + "/" + pages, x + 64, y + H - 46, 0xFFCCCCCC, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
