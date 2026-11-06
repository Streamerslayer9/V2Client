package com.example.gulp;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The inside of the stomach, for a swallowed player who pressed the view key (H). Drawn over the screen:
 *  - the wall in the SWALLOWER's colours and pattern (they send their look to the server, the server passes it on, see LookPacket and
 *    PreyViewPacket), with slow folds moving along it and a heartbeat that makes the walls pulse;
 *  - the acid pool at the bottom, with waves that speed up and get taller when the swallower runs, jumps or falls, and bubbles that
 *    rise through it and pop at the surface;
 *  - the other prey in the same organ, floating around as their spawn eggs, sinking and shrinking as they are digested;
 *  - acid droplets that come off prey while they digest, and a burst of particles when one is gone.
 * Cosmetic only, client side. Reads nothing but the packets and the swallower's movement.
 */
public final class InsideView {
    private InsideView() { }

    private static final Random RNG = new Random();

    // ------------------------------------------------------------------ what the server told us

    private static Look look = new Look(); // the swallower's look (the default one until it arrives, or for a villager)
    private static int organ;

    private static final class Fellow {
        int uid;
        float digest;
        boolean player;
        ItemStack icon = ItemStack.EMPTY;
        float sa = RNG.nextFloat() * 6.2832f, sb = RNG.nextFloat() * 6.2832f, sc = RNG.nextFloat();
        float lastX, lastY;
        float scale = 1f;
    }

    private static final Map<Integer, Fellow> FELLOWS = new HashMap<>();

    private static final class Bubble { float x, y, vy, r, ph; }
    private static final class Spark { float x, y, vx, vy, life, max, gravity; int rgb, size; }

    private static final List<Bubble> BUBBLES = new ArrayList<>();
    private static final List<Spark> SPARKS = new ArrayList<>();

    private static long lastNanos;
    private static double clock;
    private static double wavePhase;
    private static float energy;
    private static float bubbleBank;
    private static float screenW = 320, screenH = 180;

    /** Forget everything (no longer swallowed, or left the world). */
    public static void clear() {
        look = new Look();
        organ = 0;
        FELLOWS.clear();
        BUBBLES.clear();
        SPARKS.clear();
        lastNanos = 0;
        energy = 0f;
        bubbleBank = 0f;
    }

    /** Sends this client's looks to the server (when the world is joined and when the Stomach look screen closes). */
    public static void sendMyLooks() {
        if (Minecraft.getInstance().getConnection() == null) return;
        Net.CHANNEL.sendToServer(LookPacket.fromConfig());
    }

    /** The server's twice-a-second update for the swallowed player. */
    public static void receive(PreyViewPacket m) {
        organ = m.organ;
        if (m.hasLook) {
            Look l = new Look();
            l.wallHue = m.look[0];
            l.wallShade = m.look[1];
            l.acidHue = m.look[2];
            l.acidShade = m.look[3];
            l.acidLevel = m.look[4];
            l.wallCustom = (m.flags & 1) != 0;
            l.acidCustom = (m.flags & 2) != 0;
            StomachStyle.Pattern[] patterns = StomachStyle.Pattern.values();
            l.pattern = patterns[Math.min(patterns.length - 1, (m.flags >> 2) & 7)];
            look = l;
        } else {
            look = new Look();
        }
        Set<Integer> present = new HashSet<>();
        for (PreyViewPacket.Fellow f : m.fellows) {
            present.add(f.uid());
            Fellow fl = FELLOWS.get(f.uid());
            if (fl == null) {
                fl = new Fellow();
                fl.uid = f.uid();
                fl.player = f.player();
                fl.icon = iconFor(f.type(), f.player());
                FELLOWS.put(f.uid(), fl);
            }
            fl.digest = f.digest();
        }
        // Someone who was nearly digested and is now gone: a burst of particles where they were.
        for (Fellow gone : new ArrayList<>(FELLOWS.values())) {
            if (present.contains(gone.uid)) continue;
            burst(gone.lastX, gone.lastY, gone.digest > 0.5f ? 26 : 8);
            FELLOWS.remove(gone.uid);
        }
    }

    /** A spawn egg for the mob (a head for a player, rotten flesh for anything without an egg). */
    private static ItemStack iconFor(String type, boolean player) {
        if (player) return new ItemStack(Items.PLAYER_HEAD);
        try {
            ResourceLocation id = new ResourceLocation(type);
            if (ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(id);
                SpawnEggItem egg = SpawnEggItem.byId(et);
                if (egg != null) return new ItemStack(egg);
            }
        } catch (Exception ignored) {
            // a mob id we cannot read: the fallback below
        }
        return new ItemStack(Items.ROTTEN_FLESH);
    }

    // ------------------------------------------------------------------ drawing

    public static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        clock += dt;
        screenW = w;
        screenH = h;
        updateEnergy(mc, dt);
        wavePhase += dt * (1.2 + 5.5 * energy);

        // the heartbeat: a "lub-dub" that comes a little faster when the swallower is moving
        double period = 1.25 - 0.3 * Math.min(1f, energy);
        double p = (clock / period) % 1.0;
        float beat = (float) Math.min(1.0, Math.exp(-sq((p - 0.08) * 12)) + 0.6 * Math.exp(-sq((p - 0.30) * 12)));

        StomachStyle.paintWall(g, 0, 0, w, h, look);
        int wallRgb = StomachStyle.wallArgb(look) & 0xFFFFFF;
        drawFolds(g, w, h, beat, wallRgb);

        int acidH = Math.round((h - 2) * look.acidLevel);
        int surface = h - acidH;
        int acidRgb = StomachStyle.acidArgb(look) & 0xFFFFFF;

        if (acidH > 4) drawAcid(g, w, h, surface, acidRgb);
        drawFellows(g, w, h, dt, acidRgb);
        if (acidH > 4) drawBubbles(g, w, h, surface, dt, acidRgb);
        drawSparks(g, dt);
        drawVignette(g, w, h, beat, wallRgb);

        String holder = ClientState.heldHolder();
        if (holder != null && !holder.isEmpty()) {
            g.drawCenteredString(mc.font, "Inside " + holder + "'s " + Organ.byId(organ).lower(), w / 2, 8, 0xFFDDDDDD);
        }
    }

    private static double sq(double v) { return v * v; }

    /** How hard the swallower is moving (running, jumping, falling), eased: 0 = standing still, 1 = sprinting. */
    private static void updateEnergy(Minecraft mc, float dt) {
        Entity cam = mc.getCameraEntity();
        float target = 0f;
        if (cam != null && cam != mc.player) {
            double dx = cam.getX() - cam.xo, dz = cam.getZ() - cam.zo, dy = cam.getY() - cam.yo;
            double speed = Math.sqrt(dx * dx + dz * dz) + 0.5 * Math.abs(dy); // blocks per tick; sprinting is about 0.28
            target = (float) Math.min(1.6, speed / 0.25);
        }
        energy += (target - energy) * Math.min(1f, dt * 4f);
    }

    /** Slow folds moving along the wall, a little lighter at their crests, stronger on each heartbeat. */
    private static void drawFolds(GuiGraphics g, int w, int h, float beat, int wallRgb) {
        int crest = StomachStyle.lighten(wallRgb, 0.4f);
        for (int x = 0; x < w; x += 3) {
            double s = Math.sin(x * 0.035 + clock * 0.5) * Math.sin(x * 0.011 - clock * 0.23 + 1.3);
            if (s > 0.05) {
                int a = (int) (s * (26 + 34 * beat));
                if (a > 2) g.fill(x, 0, x + 3, h, a << 24);
            } else if (s < -0.45) {
                int a = (int) ((-s - 0.45) * (30 + 30 * beat));
                if (a > 2) g.fill(x, 0, x + 3, h, a << 24 | crest);
            }
        }
        int breath = (int) (22 * beat);
        if (breath > 1) g.fill(0, 0, w, h, breath << 24 | StomachStyle.lighten(wallRgb, 0.5f));
    }

    /** The edges of the screen darken in and out with the heartbeat, so the walls seem to squeeze and relax. */
    private static void drawVignette(GuiGraphics g, int w, int h, float beat, int wallRgb) {
        int depth = (int) (34 + 20 * beat);
        int dark = (wallRgb >> 1) & 0x7F7F7F;
        for (int i = 0; i < depth; i += 2) {
            float f = 1f - i / (float) depth;
            int a = (int) (150 * f * f * (0.8f + 0.4f * beat));
            if (a < 2) continue;
            int c = a << 24 | dark;
            g.fill(0, i, w, i + 2, c);
            g.fill(0, h - i - 2, w, h - i, c);
            g.fill(i, 0, i + 2, h, c);
            g.fill(w - i - 2, 0, w - i, h, c);
        }
    }

    /** The acid pool. The waves move with wavePhase and get taller with the swallower's energy. */
    private static void drawAcid(GuiGraphics g, int w, int h, int surface, int rgb) {
        int body = 0x88000000 | rgb;
        int deep = 0x55000000 | (rgb >> 1 & 0x7F7F7F);
        int shine = 0xCC000000 | StomachStyle.lighten(rgb, 0.5f);
        double amp = 1.0 + 1.8 * energy;
        for (int px = 0; px < w; px += 2) {
            int cw = Math.min(2, w - px);
            int backTop = surface + 3 + (int) Math.round(Math.sin(px * 0.09 - wavePhase * 1.1) * 2.5 * amp + Math.sin(px * 0.02 + wavePhase * 0.7) * 3 * energy);
            backTop = Math.max(surface - 14, Math.min(h - 1, backTop));
            g.fill(px, backTop, px + cw, h, deep);
            int frontTop = surface + 5 + (int) Math.round(Math.sin(px * 0.14 + wavePhase * 1.8) * 2.0 * amp + Math.sin(px * 0.31 + wavePhase * 0.9) * amp);
            frontTop = Math.max(surface - 14, Math.min(h - 1, frontTop));
            g.fill(px, frontTop, px + cw, h, body);
            g.fill(px, frontTop, px + cw, frontTop + 1, shine);
        }
        g.fillGradient(0, surface + (h - surface) / 2, w, h, 0x00000000, 0x66000000); // darker deep down
    }

    /** Bubbles rise from the bottom of the pool and pop at the surface; more of them, and faster, when the swallower moves. */
    private static void drawBubbles(GuiGraphics g, int w, int h, int surface, float dt, int rgb) {
        float rate = (8f + 28f * energy) * Math.max(0.3f, (h - surface) / (float) h * 2f);
        bubbleBank += rate * dt;
        while (bubbleBank >= 1f && BUBBLES.size() < 140) {
            bubbleBank -= 1f;
            Bubble b = new Bubble();
            b.x = RNG.nextFloat() * w;
            b.y = h - 2;
            b.r = 1 + RNG.nextInt(3);
            b.vy = 16f + RNG.nextFloat() * 26f;
            b.ph = RNG.nextFloat() * 6.28f;
            BUBBLES.add(b);
        }
        if (bubbleBank > 4f) bubbleBank = 0f;
        int ring = 0xAA000000 | StomachStyle.lighten(rgb, 0.8f);
        int fillc = 0x26000000 | StomachStyle.lighten(rgb, 0.6f);
        for (int i = BUBBLES.size() - 1; i >= 0; i--) {
            Bubble b = BUBBLES.get(i);
            b.y -= b.vy * (1f + 0.8f * energy) * dt;
            b.x += (float) Math.sin(clock * 3 + b.ph) * 10f * dt;
            if (b.y <= surface + 4) { // pop
                for (int k = 0; k < 3; k++) spark(b.x, surface + 2, (RNG.nextFloat() - 0.5f) * 30f, -10f - RNG.nextFloat() * 25f, 0.3f + RNG.nextFloat() * 0.2f, StomachStyle.lighten(rgb, 0.7f), 1, 70f);
                BUBBLES.remove(i);
                continue;
            }
            int d = (int) (b.r * 2 + 2);
            int x = (int) b.x, y = (int) b.y;
            g.fill(x, y, x + d, y + d, fillc);
            g.fill(x, y, x + d, y + 1, ring);
            g.fill(x, y + d - 1, x + d, y + d, ring);
            g.fill(x, y, x + 1, y + d, ring);
            g.fill(x + d - 1, y, x + d, y + d, ring);
            g.fill(x + 1, y + 1, x + 2, y + 2, 0xCCFFFFFF);
        }
    }

    /** The other prey in this organ, floating around as spawn eggs. They sink and shrink as they are digested and drip while they dissolve. */
    private static void drawFellows(GuiGraphics g, int w, int h, float dt, int acidRgb) {
        for (Fellow f : FELLOWS.values()) {
            double ax = 0.5 + 0.36 * Math.sin(clock * (0.17 + 0.05 * f.sc) + f.sa);
            double ay = 0.5 + 0.5 * Math.sin(clock * (0.13 + 0.04 * f.sc) + f.sb * 3);
            float x = (float) (w * ax);
            float y = (float) (h * (0.22 + 0.56 * ay + 0.12 * f.digest) + 4 * Math.sin(clock * 1.7 + f.sa));
            float s = Math.max(0.8f, (2.0f + 0.5f * energy) * (1f - 0.55f * f.digest));
            f.lastX = x;
            f.lastY = y;
            f.scale = s;
            g.pose().pushPose();
            g.pose().translate(x, y, 0f);
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) (12 * Math.sin(clock * 0.8 + f.sa))));
            g.pose().scale(s, s, 1f);
            g.renderItem(f.icon, -8, -8);
            g.pose().popPose();
            if (f.digest > 0.01f) { // a small bar under them: how digested they are
                int bw = Math.max(10, (int) (16 * s));
                int bx = (int) (x - bw / 2f), by = (int) (y + 8 * s + 2);
                g.fill(bx - 1, by - 1, bx + bw + 1, by + 3, 0xAA000000);
                g.fill(bx, by, bx + (int) (bw * f.digest), by + 2, 0xFFC0502E);
                // droplets coming off them while they dissolve
                if (RNG.nextFloat() < dt * (4f + 14f * f.digest)) {
                    spark(x + (RNG.nextFloat() - 0.5f) * 12f * s / 2f, y + (RNG.nextFloat() - 0.5f) * 8f * s / 2f,
                            (RNG.nextFloat() - 0.5f) * 14f, -18f - RNG.nextFloat() * 28f, 0.6f + RNG.nextFloat() * 0.5f,
                            RNG.nextBoolean() ? StomachStyle.lighten(acidRgb, 0.5f) : 0xC0502E, 2, 20f);
                }
            }
        }
    }

    private static void spark(float x, float y, float vx, float vy, float life, int rgb, int size, float gravity) {
        if (SPARKS.size() > 400) return;
        Spark s = new Spark();
        s.x = x; s.y = y; s.vx = vx; s.vy = vy; s.life = life; s.max = life; s.rgb = rgb & 0xFFFFFF; s.size = size; s.gravity = gravity;
        SPARKS.add(s);
    }

    /** A burst of particles: someone was just digested (or let out). */
    private static void burst(float x, float y, int n) {
        int[] colours = {0xE09030, 0xC0502E, 0xFFE9A0, 0xFFFFFF};
        for (int i = 0; i < n; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            float sp = 20f + RNG.nextFloat() * 70f;
            spark(x, y, (float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp - 20f, 0.6f + RNG.nextFloat() * 0.7f,
                    colours[RNG.nextInt(colours.length)], 2 + RNG.nextInt(2), 80f);
        }
    }

    private static void drawSparks(GuiGraphics g, float dt) {
        for (int i = SPARKS.size() - 1; i >= 0; i--) {
            Spark s = SPARKS.get(i);
            s.life -= dt;
            if (s.life <= 0f || s.y > screenH + 8) { SPARKS.remove(i); continue; }
            s.vy += s.gravity * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            int a = (int) (255 * Math.min(1f, s.life / s.max * 1.5f));
            g.fill((int) s.x, (int) s.y, (int) s.x + s.size, (int) s.y + s.size, a << 24 | s.rgb);
        }
    }
}
