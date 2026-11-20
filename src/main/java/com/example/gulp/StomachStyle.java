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
        CHECKER("Checkerboard"),
        HEARTS("Hearts"),
        DIAMONDS("Diamonds"),
        SCALES("Scales"),
        VEINS("Veins"),
        BUBBLES("Bubbles"),
        ZIGZAG("Zigzag"),
        PLAID("Plaid");

        public final String label;
        Pattern(String label) { this.label = label; }

        public Pattern next() {
            Pattern[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        public Pattern previous() {
            Pattern[] v = values();
            return v[(ordinal() + v.length - 1) % v.length];
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

    /** Fills a box, cut to the area the pattern may draw in. */
    private static void clipFill(GuiGraphics g, int ax, int ay, int bx, int by, int c, int x1, int y1, int x2, int y2) {
        int l = Math.max(ax, x1), tp = Math.max(ay, y1), r = Math.min(bx, x2), bt = Math.min(by, y2);
        if (r > l && bt > tp) g.fill(l, tp, r, bt, c);
    }

    /** Draws a little picture made of 'X' squares (each u pixels big) with its top left corner at (ox, oy). */
    private static void sprite(GuiGraphics g, int ox, int oy, int u, String[] rows, int c, int x1, int y1, int x2, int y2) {
        for (int r = 0; r < rows.length; r++) {
            String s = rows[r];
            int start = -1;
            for (int i = 0; i <= s.length(); i++) {
                boolean on = i < s.length() && s.charAt(i) == 'X';
                if (on && start < 0) start = i;
                if (!on && start >= 0) {
                    clipFill(g, ox + start * u, oy + r * u, ox + i * u, oy + (r + 1) * u, c, x1, y1, x2, y2);
                    start = -1;
                }
            }
        }
    }

    private static final String[] HEART = {".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."};
    private static final String[] DIAMOND = {"..X..", ".X.X.", "X...X", ".X.X.", "..X.."};

    private static void drawPattern(GuiGraphics g, int x, int y, int w, int h, Look l) {
        Pattern pattern = l.pattern;
        if (pattern == Pattern.NONE || w <= 0 || h <= 0) return;
        // Size: 0.5 = normal, 0 = half as big, 1 = twice as big. Strength: 0.5 = the original faint look. Colour: a lighter wall colour, or the picked hue.
        float k = (float) Math.pow(2.0, (l.patternSize - 0.5) * 2.0);
        int alpha = Math.max(6, Math.round(96f * l.patternStrength));
        int rgb = l.patternCustom ? Mth.hsvToRgb(l.patternHue, 0.6f, 0.95f) : lighten(wallBase(l), 0.55f);
        int ink = alpha << 24 | rgb; // faint, so rows and buttons stay readable on top
        int bright = Math.min(255, alpha * 7 / 3) << 24 | (l.patternCustom ? lighten(rgb, 0.3f) : lighten(wallBase(l), 0.8f));
        int x2 = x + w, y2 = y + h;
        int u = Math.max(1, Math.round(k));
        double sec = (System.currentTimeMillis() % 1000000L) / 1000.0;
        switch (pattern) {
            case STARS -> {
                for (int i = 0; i < STAR_COUNT; i++) {
                    int sx = x + 3 + STAR_X[i] * (w - 6) / 1000;
                    int sy = y + 3 + STAR_Y[i] * (h - 6) / 1000;
                    g.fill(sx, sy, sx + u, sy + u, bright);
                    if (i % 3 == 0) { // every third one is a little plus shape
                        g.fill(sx - u, sy, sx + 2 * u, sy + u, ink);
                        g.fill(sx, sy - u, sx + u, sy + 2 * u, ink);
                    }
                }
            }
            case WAVES -> {
                double phase = (System.currentTimeMillis() % 6000L) / 6000.0 * Math.PI * 2; // drifts slowly
                int rowGap = Math.max(4, Math.round(8 * k));
                for (int row = 0, ry = y + 4; ry < y2 - 2; row++, ry += rowGap) {
                    for (int px = x; px < x2; px += 2) {
                        int wy = ry + (int) Math.round(Math.sin((px - x) * 0.18 / k + row * 0.9 + phase) * 2.0 * k);
                        if (wy >= y && wy < y2) g.fill(px, wy, Math.min(x2, px + 2), wy + u, ink);
                    }
                }
            }
            case STRIPES -> {
                int gap = Math.max(5, Math.round(10 * k));
                for (int px = x + 2; px < x2; px += gap) g.fill(px, y, Math.min(x2, px + Math.max(2, Math.round(3 * k))), y2, ink);
            }
            case DOTS -> {
                int gap = Math.max(5, Math.round(8 * k));
                for (int py = y + 4; py < y2 - 1; py += gap) {
                    for (int px = x + 4; px < x2 - 1; px += gap) g.fill(px, py, px + 2 * u, py + 2 * u, ink);
                }
            }
            case CHECKER -> {
                int cell = Math.max(5, Math.round(10 * k));
                for (int py = y, r = 0; py < y2; py += cell, r++) {
                    for (int px = x + ((r & 1) == 0 ? 0 : cell); px < x2; px += cell * 2) {
                        g.fill(px, py, Math.min(x2, px + cell), Math.min(y2, py + cell), ink);
                    }
                }
            }
            case HEARTS -> {
                int stepX = 18 * u + (int) (4 * k), stepY = 14 * u + (int) (3 * k);
                for (int row = 0, py = y + 3; py < y2; py += stepY, row++) {
                    for (int px = x + 3 + ((row & 1) == 0 ? 0 : stepX / 2); px < x2; px += stepX) sprite(g, px, py, u, HEART, ink, x, y, x2, y2);
                }
            }
            case DIAMONDS -> {
                int stepX = 14 * u + (int) (3 * k), stepY = 12 * u + (int) (2 * k);
                for (int row = 0, py = y + 3; py < y2; py += stepY, row++) {
                    for (int px = x + 3 + ((row & 1) == 0 ? 0 : stepX / 2); px < x2; px += stepX) sprite(g, px, py, u, DIAMOND, ink, x, y, x2, y2);
                }
            }
            case SCALES -> {
                int r = Math.max(6, Math.round(7 * k));
                for (int row = 0, py = y; py < y2 + r; py += r, row++) {
                    for (int px = x + ((row & 1) == 0 ? 0 : r); px < x2 + r; px += 2 * r) {
                        clipFill(g, px - r, py, px - r + 1, py + r / 2 + 1, ink, x, y, x2, y2); // a "U": the two sides...
                        clipFill(g, px + r - 1, py, px + r, py + r / 2 + 1, ink, x, y, x2, y2);
                        clipFill(g, px - r + 1, py + r / 2, px + r - 1, py + r / 2 + 1, ink, x, y, x2, y2); // ...and the bottom
                    }
                }
            }
            case VEINS -> {
                int spacing = Math.max(14, Math.round(34 * k));
                int pulse = (int) (alpha * (0.75 + 0.25 * Math.sin(sec * 2.2)));
                int vein = pulse << 24 | rgb;
                int wiggle = Math.max(2, Math.round(5 * k));
                int branchGap = Math.max(12, Math.round(26 * k));
                int n = 0;
                for (int bx = x + spacing / 2; bx < x2; bx += spacing, n++) {
                    for (int py = y; py < y2; py += 2) {
                        int vx = bx + (int) Math.round(Math.sin(py * 0.045 / k + n * 1.7) * wiggle);
                        clipFill(g, vx, py, vx + 2, py + 2, vein, x, y, x2, y2);
                        if ((py - y) % branchGap < 2) { // a small branch off to the side
                            int dir = ((n + (py - y) / branchGap) & 1) == 0 ? 1 : -1;
                            for (int s = 1; s <= 5; s++) clipFill(g, vx + dir * s * 2, py + s, vx + dir * s * 2 + 2, py + s + 1, vein, x, y, x2, y2);
                        }
                    }
                }
            }
            case BUBBLES -> {
                for (int i = 0; i < 18; i++) {
                    int r = Math.max(2, Math.round((3 + (i % 4) * 1.5f) * k));
                    int cx = x + 4 + STAR_X[i] * (w - 8) / 1000;
                    int rise = (int) (sec * (5 + (i % 5) * 2) * k);
                    int cy = y2 - Math.floorMod(STAR_Y[i] * h / 1000 + rise, h + 2 * r) + r;
                    for (int a = 0; a < 12; a++) {
                        double ang = a * Math.PI / 6;
                        int bx = cx + (int) Math.round(Math.cos(ang) * r), by = cy + (int) Math.round(Math.sin(ang) * r);
                        clipFill(g, bx, by, bx + 1, by + 1, ink, x, y, x2, y2);
                    }
                    clipFill(g, cx - r / 2, cy - r / 2, cx - r / 2 + 1, cy - r / 2 + 1, bright, x, y, x2, y2);
                }
            }
            case ZIGZAG -> {
                int period = Math.max(5, Math.round(12 * k));
                int amp = Math.max(2, Math.round(4 * k));
                for (int ry = y + 3; ry < y2; ry += period + amp + 2) {
                    for (int px = x; px < x2; px += 2) {
                        int m = (px - x) % (2 * period);
                        int tri = m < period ? m : 2 * period - m;
                        clipFill(g, px, ry + tri * amp / period, px + 2, ry + tri * amp / period + 1, ink, x, y, x2, y2);
                    }
                }
            }
            case PLAID -> {
                int band = Math.max(2, Math.round(5 * k)), gap = Math.max(10, Math.round(22 * k));
                int soft = Math.max(4, alpha * 2 / 3) << 24 | rgb;
                for (int px = x + 3; px < x2; px += gap) clipFill(g, px, y, px + band, y2, soft, x, y, x2, y2);
                for (int py = y + 3; py < y2; py += gap) clipFill(g, x, py, x2, py + band, soft, x, y, x2, y2);
                for (int px = x + 3 + band / 2; px < x2; px += gap) clipFill(g, px, y, px + 1, y2, ink, x, y, x2, y2);
                for (int py = y + 3 + band / 2; py < y2; py += gap) clipFill(g, x, py, x2, py + 1, ink, x, y, x2, y2);
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
