package com.example.gulp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Asked once on first join when Flamevore is installed (V and Settings > Swallowing reopen it):
 * two big boxes side by side, Gulp on the left and Flamevore on the right. Click one to choose. Esc decides later.
 */
public class SwallowChoiceScreen extends Screen {
    private static final int W = 320;
    private static final int H = 180;
    private static final int BOX_W = 140;
    private static final int BOX_H = 120;
    private static final int BOX_TOP = 30; // distance from the top of the window to the boxes

    private static final String GULP_TEXT =
            "Press V to swallow mobs and players. Your belly grows, and your stomach level and perks apply.";
    private static final String FLAMEVORE_TEXT =
            "Flamevore does the swallowing. Gulp's mob eating and belly are off, but items, levels, perks and storage stay.";

    private List<FormattedCharSequence> gulpLines = List.of();
    private List<FormattedCharSequence> flamevoreLines = List.of();

    public SwallowChoiceScreen() {
        super(Component.literal("Swallowing system"));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    /** Left edge of a box: 0 = Gulp (left), 1 = Flamevore (right). */
    private int boxX(int i) { return i == 0 ? left() + 15 : left() + W - 15 - BOX_W; }
    private int boxY() { return top() + BOX_TOP; }

    private boolean over(int i, double mx, double my) {
        return mx >= boxX(i) && mx < boxX(i) + BOX_W && my >= boxY() && my < boxY() + BOX_H;
    }

    @Override
    protected void init() {
        gulpLines = font.split(Component.literal(GULP_TEXT), BOX_W - 16);
        flamevoreLines = font.split(Component.literal(FLAMEVORE_TEXT), BOX_W - 16);
    }

    private void choose(SwallowSystem system) {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        Net.send(19, system.ordinal());
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (over(0, mouseX, mouseY)) {
                choose(SwallowSystem.GULP);
                return true;
            }
            if (over(1, mouseX, mouseY)) {
                choose(SwallowSystem.FLAMEVORE);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawBox(GuiGraphics g, int i, String label, List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        int bx = boxX(i);
        int by = boxY();
        boolean hover = over(i, mouseX, mouseY);
        g.fill(bx, by, bx + BOX_W, by + BOX_H, hover ? 0xFF3A3A3A : 0xFF222222);
        int edge = hover ? 0xFFFFFFFF : 0xFF666666;
        g.fill(bx, by, bx + BOX_W, by + 1, edge);
        g.fill(bx, by + BOX_H - 1, bx + BOX_W, by + BOX_H, edge);
        g.fill(bx, by, bx + 1, by + BOX_H, edge);
        g.fill(bx + BOX_W - 1, by, bx + BOX_W, by + BOX_H, edge);

        // The name, drawn at double size.
        g.pose().pushPose();
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, label, (int) ((bx + BOX_W / 2f) / 2f - font.width(label) / 2f), (by + 8) / 2, 0xFFFFFF, true);
        g.pose().popPose();

        for (int l = 0; l < lines.size(); l++) {
            g.drawString(font, lines.get(l), bx + 8, by + 34 + l * 9, hover ? 0xDDDDDD : 0xAAAAAA, false);
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

        g.drawCenteredString(font, "Which swallowing system would you like to use?", x + W / 2, y + 12, 0xFFFFFF);
        drawBox(g, 0, "Gulp", gulpLines, mouseX, mouseY);
        drawBox(g, 1, "Flamevore", flamevoreLines, mouseX, mouseY);
        g.drawCenteredString(font, "Can be changed later in Settings", x + W / 2, y + H - 20, 0x999999);

        super.render(g, mouseX, mouseY, partialTick);
    }
}
