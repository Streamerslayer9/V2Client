package com.example.gulp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * A villager's Stomach screen: a compact version of the player's stomach screen that every villager has (the villager menu's Stomach
 * button opens it). It shows their stomach in their own look (wall, pattern, animated acid), how many they hold and who is inside: the
 * players they hold and the villagers they were fed, each with its type in the middle of the row and, for a fed villager, its own Release
 * button on the right. One row of buttons: Perks (their perks screen), Settings (their stomach look), Climb In, Storage (preds), Back and
 * Close. The server keeps the numbers (VillagerStomachPacket).
 */
public class VillagerStomachScreen extends Screen {
    private static final int W = 320;
    private static final int H = 250;
    private static final int ROW_H = 14;

    private static Screen pendingParent;

    private final Screen parent;
    private final int entityId;
    private final String villagerName;
    private Look look;
    private int capacity;
    private List<VillagerStomachPacket.Row> rows;
    private int ticks;
    private Button perksButton, settingsButton, climbButton, storageButton, backButton, closeButton;

    private VillagerStomachScreen(Screen parent, VillagerStomachPacket m) {
        super(Component.literal("Villager stomach"));
        this.parent = parent;
        this.entityId = m.entityId;
        Entity e = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getEntity(m.entityId) : null;
        this.villagerName = e != null ? e.getName().getString() : "Villager";
        this.look = VillagerLooks.toLook(m.look, m.flags);
        this.capacity = Math.max(1, m.capacity);
        this.rows = m.rows;
    }

    /** The Stomach button on the villager's menu was pressed: ask the server, which answers by opening the screen. */
    static void request(Screen from, int entityId) {
        pendingParent = from;
        Net.send(43, entityId);
    }

    /** The server's answer: open the screen, or refresh the one that is open. */
    static void receive(VillagerStomachPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.open) {
            Screen p = pendingParent;
            pendingParent = null;
            mc.setScreen(new VillagerStomachScreen(p, m));
        } else if (mc.screen instanceof VillagerStomachScreen s && s.entityId == m.entityId) {
            s.update(m);
        }
    }

    private void update(VillagerStomachPacket m) {
        boolean sameRows = rows.size() == m.rows.size();
        for (int i = 0; sameRows && i < rows.size(); i++) {
            sameRows = rows.get(i).name().equals(m.rows.get(i).name()) && rows.get(i).player() == m.rows.get(i).player();
        }
        this.look = VillagerLooks.toLook(m.look, m.flags);
        this.capacity = Math.max(1, m.capacity);
        this.rows = m.rows;
        if (!sameRows) rebuildWidgets(); // somebody went in or out: the Release buttons of the rows change
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    // the stomach panel: from just under the capacity bar to just above the button row
    private int panelX() { return left() + 8; }
    private int panelY() { return top() + 40; }
    private int panelW() { return W - 16; }
    private int panelH() { return H - 40 - 36; }

    /** Sends this villager's look to the server (after each change on the Settings screen). */
    private void sendLook() {
        Net.CHANNEL.sendToServer(new VillagerLookEditPacket(entityId, VillagerLooks.floatsOf(look), VillagerLooks.pack(look)));
    }

    @Override
    protected void init() {
        Net.send(20, entityId); // fresh level, type and age
        int x = left();
        int y = top();
        // One row along the bottom; render() spreads whichever of them apply evenly across it.
        perksButton = addRenderableWidget(Button.builder(Component.literal("Perks"), btn -> this.minecraft.setScreen(new VillagerPerksScreen(this, entityId, villagerName)))
                .bounds(x + 8, y + H - 28, 40, 20).build());
        settingsButton = addRenderableWidget(Button.builder(Component.literal("Settings"), btn -> this.minecraft.setScreen(
                        new StyleScreen(this, look, this::sendLook, villagerName + "'s stomach look")))
                .bounds(x + 8, y + H - 28, 40, 20)
                .tooltip(Tooltip.create(Component.literal("Change the colours and pattern of this villager's stomach. Anyone they hold sees it from inside.")))
                .build());
        climbButton = addRenderableWidget(Button.builder(Component.literal("Climb In"), btn -> Net.send(24, entityId))
                .bounds(x + 8, y + H - 28, 40, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Force yourself into their tummy, whatever kind of villager they are. How they take it depends on their type and temper.")))
                .build());
        storageButton = addRenderableWidget(Button.builder(Component.literal("Storage"), btn -> Net.send(22, entityId))
                .bounds(x + 8, y + H - 28, 40, 20).build());
        backButton = addRenderableWidget(Button.builder(Component.literal("Back"), btn -> this.minecraft.setScreen(parent))
                .bounds(x + 8, y + H - 28, 40, 20).build());
        closeButton = addRenderableWidget(Button.builder(Component.literal("Close"), btn -> this.minecraft.setScreen(null))
                .bounds(x + 8, y + H - 28, 40, 20).build());

        // A Release button on the right of every fed villager's row (where their type used to be).
        int players = 0;
        for (VillagerStomachPacket.Row r : rows) if (r.player()) players++;
        int shown = Math.min(rows.size(), (panelH() - 6) / ROW_H);
        for (int i = 0; i < shown; i++) {
            if (rows.get(i).player()) continue;
            final int fedIndex = i - players; // the server lists players first, then the fed villagers
            addRenderableWidget(Button.builder(Component.literal("Release"), btn -> Net.send(45, entityId << 6 | fedIndex))
                    .bounds(panelX() + panelW() - 4 - 48, panelY() + 4 + i * ROW_H, 48, 12)
                    .tooltip(Tooltip.create(Component.literal("Ask this pred to let " + rows.get(i).name() + " out. Some preds will say no.")))
                    .build());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 40 == 0) Net.send(44, entityId); // keep who is inside up to date
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Spreads the buttons that apply (Climb In only for adults, Storage only for preds) evenly along the bottom row. */
    private void layoutButtons(boolean adult, boolean pred) {
        climbButton.visible = adult;
        storageButton.visible = pred;
        List<Button> row = new ArrayList<>();
        for (Button b : new Button[]{perksButton, settingsButton, climbButton, storageButton, backButton, closeButton}) {
            if (b.visible) row.add(b);
        }
        int gap = 3;
        int bw = (W - 16 - gap * (row.size() - 1)) / row.size();
        int bx = left() + 8;
        for (Button b : row) {
            b.setX(bx);
            b.setY(top() + H - 28);
            b.setWidth(bw);
            bx += bw + gap;
        }
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

        VillagerInfoPacket info = ClientVillagerInfo.get(entityId);
        layoutButtons(info != null && info.adult, info != null && info.type == 2);

        g.drawString(font, font.plainSubstrByWidth(villagerName + "'s stomach", W - 70), x + 8, y + 8, 0xFFFFFF, true);
        if (info != null) {
            String lv = "Lv." + info.level;
            g.drawString(font, lv, x + W - 8 - font.width(lv), y + 8, 0xFFCCCCCC, false);
        }

        // how full: the people inside against how many they can hold
        int held = rows.size();
        int bx = x + 8, by = y + 22, bw = W - 16;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 13, 0xFF000000);
        g.fill(bx, by, bx + bw, by + 12, 0xFF2A2A2A);
        g.fill(bx, by, bx + Math.min(bw, bw * held / capacity), by + 12, 0xFF9B59B6);
        g.drawCenteredString(font, "Holding " + held + " / " + capacity, bx + bw / 2, by + 2, 0xFFFFFFFF);

        // the stomach: their wall and pattern, the animated acid pool at the bottom, and who is inside
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        StomachStyle.paintWall(g, px, py, pw, ph, look);
        int acidH = Math.round((ph - 2) * look.acidLevel);
        if (acidH > 4) StomachStyle.paintAcid(g, px + 1, py + ph - 1 - acidH, pw - 2, acidH, look);
        if (rows.isEmpty()) {
            g.drawCenteredString(font, "Nothing in their stomach.", px + pw / 2, py + ph / 2 - 4, 0xFFDDDDDD);
        } else {
            int shown = Math.min(rows.size(), (ph - 6) / ROW_H);
            for (int i = 0; i < shown; i++) {
                VillagerStomachPacket.Row r = rows.get(i);
                int ry = py + 4 + i * ROW_H;
                g.fill(px + 4, ry, px + pw - 4, ry + 12, 0x66000000);
                g.drawString(font, font.plainSubstrByWidth(r.name(), pw / 2 - 40), px + 8, ry + 2, 0xFFFFFFFF, true);
                String kind = r.player() ? "player" : "villager";
                g.drawString(font, kind, px + pw / 2 - font.width(kind) / 2, ry + 2, r.player() ? 0xFF7CDB7C : 0xFFE0C070, false); // in the middle
            }
            if (rows.size() > shown) g.drawString(font, "+" + (rows.size() - shown) + " more", px + 8, py + ph - 12, 0xFFDDDDDD, true);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
