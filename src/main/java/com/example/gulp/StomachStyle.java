package com.example.gulp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * Client-side look of the stomach screen: the colour of the stomach wall (the panel), the colour of the digestive
 * acid (the digestion bars) and an optional pattern drawn over the wall. All of it is picked in Settings > Stomach look
 * and saved in config/gulp-client.properties (see GulpConfig). Purely cosmetic, nothing is sent to the server.
 */
public final class StomachStyle {
    public enum Pattern {
        NONE("None"),
        STARS("Stars"),
        WAVES("Wavy Stripes"),
        STRIPES("Stripes"),
        DOTS("Dots"),
        CHECKER("Checkerboard");

        public final String label;
        Pattern(String label) { this.label = label; }

        public Pattern next() {
            Pattern[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    // Wall: hue 0-1 and shade 0-1 (how bright). The wall stays dark so the text on top of it stays readable.
    public static final float WALL_SAT = 0.55f;
    public static final float ACID_SAT = 0.85f;

    private static final int STAR_COUNT = 40;
    private static final int[] STAR_X = new int[STAR_COUNT];
    private static final int[] STAR_Y = new int[STAR_COUNT];
    static {
        Random r = new Random(11); // a fixed sprinkle, as fractions of the panel size (x1000)
        for (int i = 0; i < STAR_COUNT; i++) {
            STAR_X[i] = r.nextInt(1000);
            STAR_Y[i] = r.nextInt(1000);
        }
    }

    private StomachStyle() { }

    /** The wall colour as opaque RGB. Shade 0-1 maps to a dark-to-medium colour (never so bright the white text is lost). */
    public static int wallRgb(Look l) {
        return Mth.hsvToRgb(l.wallHue, WALL_SAT, 0.10f + 0.40f * l.wallShade);
    }

    /** The acid colour as opaque RGB. Shade 0-1 maps from dim to fully bright. */
    public static int acidRgb(Look l) {
        return Mth.hsvToRgb(l.acidHue, ACID_SAT, 0.45f + 0.55f * l.acidShade);
    }

    private static int wallBase(Look l) { return l.wallCustom ? wallRgb(l) : DEFAULT_WALL; }

    /** A colour picker swatch for a hue (full brightness), used by the hue sliders. */
    public static int hueRgb(float hue) {
        return Mth.hsvToRgb(hue, 0.85f, 1f);
    }

    private static final int DEFAULT_WALL = 0x141414;
    private static final int DEFAULT_ACID = 0xE09030;

    /** The wall colour in use: the original neutral dark one until the player picks their own. */
    public static int wallArgb(Look l) { return 0xE6000000 | wallBase(l); }
    /** The acid colour in use: the original orange until the player picks their own. */
    public static int acidArgb(Look l) { return 0xFF000000 | (l.acidCustom ? acidRgb(l) : DEFAULT_ACID); }

    /** Draws the stomach wall: the coloured panel (with its border) and then the pattern on top. */
    public static void paintWall(GuiGraphics g, int x, int y, int w, int h, Look l) {
        g.fill(x, y, x + w, y + h, wallArgb(l));
        drawPattern(g, x + 1, y + 1, w - 2, h - 2, l);
        int border = l.wallCustom ? 0xFF000000 | lighten(wallBase(l), 0.45f) : 0xFF666666;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    /**
     * Draws the digestive acid: an animated wavy pool filling the area (x, y, w, h), meant for the bottom half of the box.
     * Two waves drift at different speeds, the surface is a brighter line, and a few pixel bubbles rise through it.
     */
    public static void paintAcid(GuiGraphics g, int x, int y, int w, int h, Look l) {
        if (w <= 0 || h <= 4) return;
        int rgb = acidArgb(l) & 0xFFFFFF;
        int body = 0x88000000 | rgb;
        int deep = 0x55000000 | (rgb >> 1 & 0x7F7F7F); // a darker second layer behind
        int shine = 0xCC000000 | lighten(rgb, 0.5f);
        double t = (System.currentTimeMillis() % 100000L) / 1000.0;
        int bottom = y + h;
        for (int px = 0; px < w; px += 2) {
            int cw = Math.min(2, w - px);
            // back wave
            int backTop = y + 3 + (int) Math.round(Math.sin(px * 0.09 - t * 1.1) * 2.5);
            g.fill(x + px, backTop, x + px + cw, bottom, deep);
            // front wave
            int frontTop = y + 5 + (int) Math.round(Math.sin(px * 0.14 + t * 1.8) * 2.0 + Math.sin(px * 0.31 + t * 0.9));
            g.fill(x + px, frontTop, x + px + cw, bottom, body);
            g.fill(x + px, frontTop, x + px + cw, frontTop + 1, shine);
        }
        // pixel bubbles rising
        for (int i = 0; i < 8; i++) {
            double cycle = (t * 0.25 + i * 0.37) % 1.0;
            int bx = x + 6 + (int) ((i * 53 + 17) % Math.max(1, w - 12));
            int by = bottom - 3 - (int) (cycle * (h - 12));
            g.fill(bx, by, bx + 2, by + 2, 0x66000000 | lighten(rgb, 0.7f));
        }
    }

    private static void drawPattern(GuiGraphics g, int x, int y, int w, int h, Look l) {
        Pattern pattern = l.pattern;
        if (pattern == Pattern.NONE || w <= 0 || h <= 0) return;
        int ink = 0x30000000 | lighten(wallBase(l), 0.55f); // faint, so rows and buttons stay readable on top
        int x2 = x + w, y2 = y + h;
        switch (pattern) {
            case STARS -> {
                int bright = 0x70000000 | lighten(wallBase(l), 0.8f);
                for (int i = 0; i < STAR_COUNT; i++) {
                    int sx = x + 3 + STAR_X[i] * (w - 6) / 1000;
                    int sy = y + 3 + STAR_Y[i] * (h - 6) / 1000;
                    g.fill(sx, sy, sx + 1, sy + 1, bright);
                    if (i % 3 == 0) { // every third one is a little plus shape
                        g.fill(sx - 1, sy, sx + 2, sy + 1, ink);
                        g.fill(sx, sy - 1, sx + 1, sy + 2, ink);
                    }
                }
            }
            case WAVES -> {
                double phase = (System.currentTimeMillis() % 6000L) / 6000.0 * Math.PI * 2; // drifts slowly
                for (int row = 0, ry = y + 4; ry < y2 - 2; row++, ry += 8) {
                    for (int px = x; px < x2; px += 2) {
                        int wy = ry + (int) Math.round(Math.sin((px - x) * 0.18 + row * 0.9 + phase) * 2.0);
                        if (wy >= y && wy < y2) g.fill(px, wy, Math.min(x2, px + 2), wy + 1, ink);
                    }
                }
            }
            case STRIPES -> {
                for (int px = x + 2; px < x2; px += 10) g.fill(px, y, Math.min(x2, px + 3), y2, ink);
            }
            case DOTS -> {
                for (int py = y + 4; py < y2 - 1; py += 8) {
                    for (int px = x + 4; px < x2 - 1; px += 8) g.fill(px, py, px + 2, py + 2, ink);
                }
            }
            case CHECKER -> {
                for (int py = y, r = 0; py < y2; py += 10, r++) {
                    for (int px = x + ((r & 1) == 0 ? 0 : 10); px < x2; px += 20) {
                        g.fill(px, py, Math.min(x2, px + 10), Math.min(y2, py + 10), ink);
                    }
                }
            }
            default -> { }
        }
    }

    /** Mixes a colour toward white by `amount` (0 = same colour, 1 = white). Returns RGB with no alpha. */
    public static int lighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (int) ((255 - r) * amount);
        gr += (int) ((255 - gr) * amount);
        b += (int) ((255 - b) * amount);
        return (r << 16) | (gr << 8) | b;
    }
}
