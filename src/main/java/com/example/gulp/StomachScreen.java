package com.example.gulp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Shows what's in your stomach. Release or digest each mob individually. */
public class StomachScreen extends Screen {
    private static final int W = 250;
    private static final int ROWS = 5;

    /** The Breast tab uses the last row for the breast storage (Breast Milk and Butter, with the Take buttons). */
    private static int rows() { return ClientState.activeOrgan() == Organ.BREAST.ordinal() ? ROWS - 1 : ROWS; }
    private static final int ROW_H = 30;
    private static final int HEADER = 62;
    private static final int FOOTER = 56;
    private static final int H = HEADER + ROWS * ROW_H + FOOTER;

    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    /** Every spawn egg in the game keyed by the mob it spawns, built the first time one is needed. */
    private static Map<EntityType<?>, SpawnEggItem> allEggs;

    private int page = 0;
    private String lastSig = "";

    public StomachScreen() {
        super(Component.literal("Stomach"));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private static SpawnEggItem eggFor(EntityType<?> type) {
        SpawnEggItem egg = SpawnEggItem.byId(type);
        if (egg != null) return egg;
        if (allEggs == null) {
            // Some mods register their eggs in unusual ways, so look through every item once.
            allEggs = new HashMap<>();
            for (Item item : ForgeRegistries.ITEMS) {
                if (item instanceof SpawnEggItem e) {
                    try {
                        EntityType<?> t = e.getType(null);
                        if (t != null) allEggs.putIfAbsent(t, e);
                    } catch (Exception ignored) {
                        // a misbehaving modded egg: skip it
                    }
                }
            }
        }
        return allEggs.get(type);
    }

    /** The mob's spawn egg as its icon. If the mob has no spawn egg (many modded mobs), the icon is left empty. */
    private static ItemStack iconFor(String typeId) {
        return ICONS.computeIfAbsent(typeId, id -> {
            if ("minecraft:player".equals(id)) return new ItemStack(Items.PLAYER_HEAD);
            Optional<EntityType<?>> type = EntityType.byString(id);
            if (type.isPresent()) {
                SpawnEggItem egg = eggFor(type.get());
                if (egg != null) return new ItemStack(egg);
            }
            return ItemStack.EMPTY;
        });
    }

    /** The mob's name in the player's own language. A mod with no translation gets a readable name from its id. */
    private static String displayName(ClientState.EntryInfo e) {
        String name = e.name().getString();
        if (!name.isEmpty() && !name.startsWith("entity.")) return name;
        String id = e.typeId();
        String path = id.substring(id.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("[_/]+")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : name;
    }

    /** The entries held in the organ that is shown (the one new prey goes into). */
    private static List<ClientState.EntryInfo> shown() {
        int organ = ClientState.activeOrgan();
        List<ClientState.EntryInfo> out = new java.util.ArrayList<>();
        for (ClientState.EntryInfo e : ClientState.entries()) if (e.organ() == organ) out.add(e);
        return out;
    }

    @Override
    protected void init() {
        ScreenLoop.start();
        List<ClientState.EntryInfo> list = shown();
        int pages = Math.max(1, (list.size() + rows() - 1) / rows());
        page = Math.max(0, Math.min(page, pages - 1));
        lastSig = ClientState.signature() + "#" + page;

        int x = left();
        int y = top();
        int start = page * rows();
        for (int i = 0; i < rows() && start + i < list.size(); i++) {
            ClientState.EntryInfo e = list.get(start + i);
            int ry = y + HEADER + i * ROW_H;
            addRenderableWidget(Button.builder(Component.literal("Release"), btn -> Net.send(4, e.uid()))
                    .bounds(x + W - 108, ry + 4, 50, 20).build());
            if (ClientState.isInstantDigest()) { // off by default, switched on in Settings
                Button digest = Button.builder(Component.literal("Digest"), btn -> Net.send(5, e.uid()))
                        .bounds(x + W - 56, ry + 4, 48, 20).build();
                digest.active = !"minecraft:player".equals(e.typeId()); // swallowed players are only held, never digested
                addRenderableWidget(digest);
            }
        }

        if (ClientState.activeOrgan() == Organ.BREAST.ordinal()) { // the breast storage: take the milk as it is, or the butter that is ready
            int sy = y + HEADER + (ROWS - 1) * ROW_H + 5;
            Button milk = Button.builder(Component.literal("Take Milk"), btn -> Net.send(32))
                    .bounds(x + W - 140, sy, 64, 20).build();
            milk.active = ClientState.breastMilk() > 0;
            addRenderableWidget(milk);
            Button butter = Button.builder(Component.literal("Take Butter"), btn -> Net.send(33))
                    .bounds(x + W - 72, sy, 64, 20).build();
            butter.active = ClientState.breastButter() > 0;
            addRenderableWidget(butter);
        }

        if (ClientState.multiOrgans()) { // one tab per place prey can be held; the tab you pick is where new prey goes
            for (int i = 0; i < Organ.COUNT; i++) {
                final int organ = i;
                Button tab = Button.builder(Component.literal(Organ.byId(i).label), btn -> {
                            page = 0;
                            Net.send(30, organ);
                        })
                        .bounds(x + 8 + i * 47, y + 34, 46, 16).build();
                tab.active = i != ClientState.activeOrgan();
                addRenderableWidget(tab);
            }
        }

        int fy = y + HEADER + ROWS * ROW_H + 6;
        addRenderableWidget(Button.builder(
                        Component.literal(ClientState.isHard() ? "Hard mode" : "Soft mode"),
                        btn -> Net.send(31, ClientState.activeOrgan())) // soft / hard for the organ shown
                .bounds(x + 8, fy, 76, 20).build());
        addRenderableWidget(Button.builder(
                        Component.literal("Perks (" + ClientState.points() + ")"),
                        btn -> this.minecraft.setScreen(PerkMenus.create()))
                .bounds(x + 87, fy, 76, 20).build());
        // The server opens the item storage as a chest-style screen, which replaces this one.
        addRenderableWidget(Button.builder(Component.literal("Storage"), btn -> Net.send(7))
                .bounds(x + 166, fy, 76, 20).build());

        Button prev = Button.builder(Component.literal("<"), btn -> { page--; clearWidgets(); init(); })
                .bounds(x + 8, fy + 26, 20, 20).build();
        Button next = Button.builder(Component.literal(">"), btn -> { page++; clearWidgets(); init(); })
                .bounds(x + 32, fy + 26, 20, 20).build();
        prev.active = page > 0;
        next.active = page < pages - 1;
        addRenderableWidget(prev);
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Settings"), btn -> this.minecraft.setScreen(new SettingsScreen()))
                .bounds(x + 112, fy + 26, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(x + W - 68, fy + 26, 60, 20).build());
    }

    @Override
    public void tick() {
        if (!lastSig.equals(ClientState.signature() + "#" + page)) {
            clearWidgets();
            init();
        }
    }

    /** Keep the world running while this is open so digestion and healing continue. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left();
        int y = top();

        // Panel
        StomachStyle.paintWall(g, x, y, W, H);
        StomachStyle.paintAcid(g, x + 1, y + H / 2, W - 2, H - H / 2 - 1); // the acid pool: bottom half of the box // the stomach wall: your colour and pattern from Settings > Stomach look

        int organ = ClientState.activeOrgan();
        g.drawString(font, Organ.byId(organ).label + "  Lv." + ClientState.level(), x + 8, y + 7, 0xFFFFFF, true);
        String acidLabel = "acid: digestion";
        String legend = "red: health   ";
        int lx = x + W - 8 - font.width(legend) - font.width(acidLabel);
        g.drawString(font, legend, lx, y + 7, 0x999999, false);
        g.drawString(font, acidLabel, lx + font.width(legend), y + 7, StomachStyle.acidArgb(), false);

        // Capacity bar
        int bx = x + 8, bw = W - 16, by = y + 21;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 11, 0xFF000000);
        g.fill(bx, by, bx + bw, by + 10, 0xFF333333);
        int fill = (int) (bw * Math.min(1f, ClientState.usedIn(organ) / ClientState.cap()));
        g.fill(bx, by, bx + fill, by + 10, ClientState.isHard() ? 0xFFC0502E : 0xFF5DB85D);
        g.drawString(font, Stomach.fmtVolume(ClientState.usedIn(organ)) + " / " + Stomach.fmtVolume(ClientState.cap()), bx + 4, by + 1, 0xFFFFFF, true);

        // MCA: shown only while villagers are afraid of you (it fades with time)
        if (ClientState.predator() > 0) {
            g.drawString(font, "Villagers fear you: " + ClientState.predator(), x + 8, y + 52,
                    ClientState.predator() >= 30 ? 0xFFFF6B6B : 0xFFE0A050, false);
        }

        // Rows
        if (organ == Organ.BREAST.ordinal()) { // the breast storage in the last row
            int sy = y + HEADER + (ROWS - 1) * ROW_H;
            g.fill(x + 6, sy + 1, x + W - 6, sy + ROW_H - 1, 0x40FFC040);
            g.drawString(font, "Milk " + ClientState.breastMilk() + "   Butter " + ClientState.breastButter(), x + 10, sy + 5, 0xFFE9A0, false);
            g.fill(x + 10, sy + 18, x + 106, sy + 22, 0xFF222222); // how far the next batch has churned into butter
            g.fill(x + 10, sy + 18, x + 10 + (int) (96 * ClientState.breastChurn()), sy + 22, 0xFFE6C84A);
        }
        List<ClientState.EntryInfo> list = shown();
        int start = page * rows();
        if (list.isEmpty()) {
            g.drawCenteredString(font, Component.literal("Nothing in your " + Organ.byId(organ).lower() + "."), x + W / 2, y + HEADER + 60, 0xAAAAAA);
        }
        for (int i = 0; i < rows() && start + i < list.size(); i++) {
            ClientState.EntryInfo e = list.get(start + i);
            int ry = y + HEADER + i * ROW_H;
            g.fill(x + 6, ry + 1, x + W - 6, ry + ROW_H - 1, 0x40FFFFFF);
            ItemStack icon = iconFor(e.typeId());
            if (!icon.isEmpty()) g.renderItem(icon, x + 10, ry + 7); // no spawn egg = no icon
            g.drawString(font, font.plainSubstrByWidth(displayName(e), 80), x + 32, ry + 4, 0xFFFFFF, true);

            // Health bar
            float hpRatio = e.maxHp() > 0 ? Math.min(1f, e.hp() / e.maxHp()) : 0f;
            g.fill(x + 32, ry + 15, x + 112, ry + 19, 0xFF222222);
            g.fill(x + 32, ry + 15, x + 32 + (int) (80 * hpRatio), ry + 19, 0xFFD04040);
            // Digestion bar
            g.fill(x + 32, ry + 21, x + 112, ry + 24, 0xFF222222);
            g.fill(x + 32, ry + 21, x + 32 + (int) (80 * e.digest()), ry + 24, StomachStyle.acidArgb());
            if (e.organ() == Organ.TAIL_ID) { // tail: how far along the way to the stomach
                g.fill(x + 32, ry + 26, x + 112, ry + 28, 0xFF222222);
                g.fill(x + 32, ry + 26, x + 32 + (int) (80 * e.transit()), ry + 28, 0xFF4A90E2);
            }
        }

        // Page text
        int pages = Math.max(1, (list.size() + rows() - 1) / rows());
        g.drawString(font, "Page " + (page + 1) + "/" + pages, x + 58, y + H - 20, 0xAAAAAA, true);

        super.render(g, mouseX, mouseY, partialTick);
    }
}
