package com.example.gulp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Settings > Stomach look. Pick the colour of the stomach wall and of the digestive acid with a hue slider, a shade slider
 * and a row of quick colour swatches, and choose a pattern for the wall. The preview on the right updates as you drag.
 * Everything is saved to config/gulp-client.properties and is client-side only.
 */
public class StyleScreen extends Screen {
    private static final int W = 250;
    private static final int H = 266;

    /** The quick colour picker: click a swatch to jump the hue slider to it. */
    private static final float[] SWATCH_HUES = {0.0f, 0.06f, 0.12f, 0.33f, 0.50f, 0.60f, 0.75f, 0.90f};

    private final int[] swatchX = new int[SWATCH_HUES.length];
    private Button patternButton;
    /** Which place is being edited: an Organ id (stomach, breast, cock or unbirth). */
    private int organ = 0;

    private Look look() { return GulpConfig.look(organ); }

    public StyleScreen() {
        super(Component.literal("Stomach look"));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    /** A 0-1 slider. The hue one draws a rainbow behind its handle. */
    private static final class Slider extends AbstractSliderButton {
        private final String name;
        private final boolean rainbow;
        private final Consumer<Float> setter;

        Slider(int x, int y, int w, String name, boolean rainbow, float start, Consumer<Float> setter) {
            super(x, y, w, 14, Component.empty(), start);
            this.name = name;
            this.rainbow = rainbow;
            this.setter = setter;
            updateMessage();
        }

        void set(float v) {
            this.value = Math.max(0f, Math.min(1f, v));
            applyValue();
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(name + ": " + Math.round(this.value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            setter.accept((float) this.value);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            GulpConfig.save();
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
            if (rainbow) {
                for (int i = 0; i < w; i++) g.fill(x + i, y, x + i + 1, y + h, 0xFF000000 | StomachStyle.hueRgb(i / (float) w));
            } else {
                g.fill(x, y, x + w, y + h, 0xFF2A2A2A);
                g.fill(x, y, x + (int) (w * this.value), y + h, 0xFF707070);
            }
            int hx = x + (int) ((w - 4) * this.value);
            g.fill(hx - 1, y - 2, hx + 5, y + h + 2, 0xFF000000);
            g.fill(hx, y - 1, hx + 4, y + h + 1, isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFCCCCCC);
            Font f = Minecraft.getInstance().font;
            g.drawCenteredString(f, getMessage(), x + w / 2, y + 3, 0xFFFFFF);
        }
    }

    private Slider wallHue, wallShade, acidHue, acidShade, acidLevel;

    @Override
    protected void init() {
        ScreenLoop.start();
        int x = left();
        int top0 = top();
        int y = top0 + 24; // everything below the tabs
        int sw = 150; // slider width
        int sx = x + 15;

        // --- One tab per place that has its own look (the tail leads to the stomach, so it has none)
        int tabW = 53;
        int slot = 0;
        for (int i = 0; i < Organ.COUNT; i++) {
            if (i == Organ.TAIL_ID || (i != organ && !ClientState.organAvailable(i))) continue;
            final int id = i;
            Button tab = Button.builder(Component.literal(Organ.byId(i).label), btn -> { organ = id; rebuildWidgets(); })
                    .bounds(x + 15 + slot++ * (tabW + 2), top0 + 20, tabW, 16).build();
            tab.active = i != organ;
            addRenderableWidget(tab);
        }

        // --- Wall
        wallHue = new Slider(sx, y + 34, sw, "Hue", true, look().wallHue, v -> { look().wallHue = v; look().wallCustom = true; });
        wallShade = new Slider(sx, y + 52, sw, "Shade", false, look().wallShade, v -> { look().wallShade = v; look().wallCustom = true; });
        addRenderableWidget(wallHue);
        addRenderableWidget(wallShade);
        patternButton = Button.builder(Component.empty(), btn -> {
            look().pattern = look().pattern.next();
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 72, sw, 18).build();
        addRenderableWidget(patternButton);

        // --- Digestive acid
        acidHue = new Slider(sx, y + 114, sw, "Hue", true, look().acidHue, v -> { look().acidHue = v; look().acidCustom = true; });
        acidShade = new Slider(sx, y + 132, sw, "Shade", false, look().acidShade, v -> { look().acidShade = v; look().acidCustom = true; });
        addRenderableWidget(acidHue);
        addRenderableWidget(acidShade);
        acidLevel = new Slider(sx, y + 166, sw, "Fullness", false, look().acidLevel, v -> look().acidLevel = v);
        addRenderableWidget(acidLevel);

        // Quick swatches: the colour picker. Click one for the wall (top row) or acid (bottom row).
        int swatch = 14, gap = 2;
        for (int i = 0; i < SWATCH_HUES.length; i++) {
            final float hue = SWATCH_HUES[i];
            swatchX[i] = x + 15 + i * (swatch + gap);
            addRenderableWidget(Button.builder(Component.empty(), btn -> { wallHue.set(hue); GulpConfig.save(); })
                    .bounds(swatchX[i], y + 94, swatch, 8).build());
            addRenderableWidget(Button.builder(Component.empty(), btn -> { acidHue.set(hue); GulpConfig.save(); })
                    .bounds(swatchX[i], y + 150, swatch, 8).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Reset"), btn -> {
            look().reset();
            GulpConfig.save();
            rebuildWidgets();
        }).bounds(x + 15, top0 + H - 54, 60, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> this.minecraft.setScreen(new SettingsScreen()))
                .bounds(x + 8, top0 + H - 28, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(x + W - 68, top0 + H - 28, 60, 20).build());
        refresh();
    }

    private void refresh() {
        patternButton.setMessage(Component.literal("Wall pattern: " + look().pattern.label));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left();
        int t = top();
        int y = t + 24; // content starts below the tabs

        g.fill(x, t, x + W, t + H, 0xE6141414);
        g.fill(x, t, x + W, t + 1, 0xFF666666);
        g.fill(x, t + H - 1, x + W, t + H, 0xFF666666);
        g.fill(x, t, x + 1, t + H, 0xFF666666);
        g.fill(x + W - 1, t, x + W, t + H, 0xFF666666);

        g.drawString(font, "Stomach look", x + 8, t + 8, 0xFFFFFF, true);
        g.drawString(font, Organ.byId(organ).label + " wall", x + 15, y + 22, 0xFFCCCCCC, false);
        g.drawString(font, "Digestive acid", x + 15, y + 102 + 0, 0xFFCCCCCC, false);

        // Live preview of the box: wall and pattern on top, the animated acid pool in the bottom half.
        int px = x + 15 + 150 + 12, pw = W - 177 - 10;
        StomachStyle.paintWall(g, px, y + 30, pw, 120, look());
        int previewAcid = Math.round(118 * look().acidLevel);
        if (previewAcid > 4) StomachStyle.paintAcid(g, px + 1, y + 149 - previewAcid, pw - 2, previewAcid, look());
        g.drawCenteredString(font, "Wall", px + pw / 2, y + 50, 0xFFFFFFFF);
        g.drawCenteredString(font, "Acid", px + pw / 2, y + 135, 0xFFFFFFFF);

        // Swatch colours (drawn over the blank buttons).
        super.render(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < SWATCH_HUES.length; i++) {
            int c = 0xFF000000 | StomachStyle.hueRgb(SWATCH_HUES[i]);
            g.fill(swatchX[i] + 1, y + 95, swatchX[i] + 13, y + 101, c);
            g.fill(swatchX[i] + 1, y + 151, swatchX[i] + 13, y + 157, c);
        }
    }
}
