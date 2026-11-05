package com.example.gulp;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Client-side settings, saved to config/gulp-client.properties. */
public class GulpConfig {
    public enum BellyStyle {
        /** The original look: the whole torso (chest to waist) swells forward. */
        CLASSIC("Classic (whole torso)"),
        /** Only the lower stomach area swells; the chest stays flat. */
        STOMACH("Stomach only"),
        /** No built-in belly at all (for players whose Custom Player Model already has its own belly). */
        OFF("Off (my model has one)");

        public final String label;
        BellyStyle(String label) { this.label = label; }

        public BellyStyle next() {
            BellyStyle[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public static BellyStyle bellyStyle = BellyStyle.STOMACH;

    /** Stomach look (Settings > Stomach look): wall and acid colour as hue / shade (0 to 1) and the wall pattern. */
    /** false = the original look (neutral dark wall, orange acid) until the player moves a slider or picks a swatch. */
    public static boolean wallCustom = false;
    public static boolean acidCustom = false;
    public static float wallHue = 0.0f;
    public static float wallShade = 0.15f;
    public static float acidHue = 0.09f;
    public static float acidShade = 0.8f;
    public static StomachStyle.Pattern stomachPattern = StomachStyle.Pattern.NONE;

    public static void resetStomachLook() {
        wallCustom = false;
        acidCustom = false;
        wallHue = 0.0f;
        wallShade = 0.15f;
        acidHue = 0.09f;
        acidShade = 0.8f;
        stomachPattern = StomachStyle.Pattern.NONE;
    }

    private static float unit(Properties props, String key, float fallback) {
        try {
            return Math.max(0f, Math.min(1f, Float.parseFloat(props.getProperty(key, Float.toString(fallback)))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
    public static boolean levelUpMessages = true;

    /** Show each player's role in their name tag, e.g. "Name [Prey]". */
    public static boolean showRoleTags = true;

    /** Right-click an item in the stomach storage to eat it (hold to keep eating). Off = normal right-click. */
    public static boolean rightClickEat = true;

    /** The short action-bar messages when you do something (swallow, release, "too big", ...). */
    public static boolean actionMessages = true;

    /** The helpful hover tooltips on Gulp's buttons and screens (Eat 1, Strip, payout, settings help...). Off hides them. */
    public static boolean helpTooltips = true;

    /** Custom Player Models: drive the model's belly animations from your stomach (does nothing without CPM). */
    public static boolean cpmSliders = true;
    /** Names of the animations in your CPM model. Change them in config/gulp-client.properties. */
    public static String cpmBellyName = "belly";
    public static String cpmMobsName = "belly_mobs";
    public static String cpmItemsName = "belly_items";

    /** true = the old list-style perk menu, false = the new look (nodes around a hub). */
    public static boolean legacyPerkMenu = false;

    /** true = the mod's own sounds, false = the Minecraft stand-in sounds. */
    public static boolean useCustomSounds = true;
    private static final boolean[] SOUND_ON = new boolean[GulpSound.values().length];
    static { java.util.Arrays.fill(SOUND_ON, true); }

    /** Per-sound volume: 0.0 = silent, 1.0 = normal (100%), 2.0 = double (200%). */
    private static final float[] SOUND_VOLUME = new float[GulpSound.values().length];
    static { java.util.Arrays.fill(SOUND_VOLUME, 1.0f); }

    public static float volume(GulpSound s) { return SOUND_VOLUME[s.ordinal()]; }
    public static void setVolume(GulpSound s, float v) { SOUND_VOLUME[s.ordinal()] = Math.max(0f, Math.min(2f, v)); }

    public static boolean isOn(GulpSound s) { return SOUND_ON[s.ordinal()]; }
    public static void setOn(GulpSound s, boolean on) { SOUND_ON[s.ordinal()] = on; }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("gulp-client.properties");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(f)) {
                props.load(in);
            }
            bellyStyle = BellyStyle.valueOf(props.getProperty("bellyStyle", BellyStyle.STOMACH.name()));
            levelUpMessages = Boolean.parseBoolean(props.getProperty("levelUpMessages", "true"));
            actionMessages = Boolean.parseBoolean(props.getProperty("actionMessages", "true"));
            showRoleTags = Boolean.parseBoolean(props.getProperty("showRoleTags", "true"));
            rightClickEat = Boolean.parseBoolean(props.getProperty("rightClickEat", "true"));
            cpmSliders = Boolean.parseBoolean(props.getProperty("cpmSliders", "true"));
            helpTooltips = Boolean.parseBoolean(props.getProperty("helpTooltips", "true"));
            cpmBellyName = props.getProperty("cpmBellyName", "belly").trim();
            cpmMobsName = props.getProperty("cpmMobsName", "belly_mobs").trim();
            cpmItemsName = props.getProperty("cpmItemsName", "belly_items").trim();
            legacyPerkMenu = Boolean.parseBoolean(props.getProperty("legacyPerkMenu", "false"));
            wallCustom = Boolean.parseBoolean(props.getProperty("wallCustom", "false"));
            acidCustom = Boolean.parseBoolean(props.getProperty("acidCustom", "false"));
            wallHue = unit(props, "wallHue", wallHue);
            wallShade = unit(props, "wallShade", wallShade);
            acidHue = unit(props, "acidHue", acidHue);
            acidShade = unit(props, "acidShade", acidShade);
            try {
                stomachPattern = StomachStyle.Pattern.valueOf(props.getProperty("stomachPattern", "NONE"));
            } catch (IllegalArgumentException e) {
                stomachPattern = StomachStyle.Pattern.NONE;
            }
            useCustomSounds = Boolean.parseBoolean(props.getProperty("customSounds", "true"));
            for (GulpSound s : GulpSound.values()) {
                setOn(s, Boolean.parseBoolean(props.getProperty("sound." + s.name(), "true")));
                setVolume(s, Float.parseFloat(props.getProperty("volume." + s.name(), "1.0")));
            }
        } catch (Exception ignored) {
            // Bad or missing file: keep defaults.
        }
    }

    public static void save() {
        try {
            Properties props = new Properties();
            props.setProperty("bellyStyle", bellyStyle.name());
            props.setProperty("levelUpMessages", Boolean.toString(levelUpMessages));
            props.setProperty("actionMessages", Boolean.toString(actionMessages));
            props.setProperty("showRoleTags", Boolean.toString(showRoleTags));
            props.setProperty("rightClickEat", Boolean.toString(rightClickEat));
            props.setProperty("cpmSliders", Boolean.toString(cpmSliders));
            props.setProperty("helpTooltips", Boolean.toString(helpTooltips));
            props.setProperty("cpmBellyName", cpmBellyName);
            props.setProperty("cpmMobsName", cpmMobsName);
            props.setProperty("cpmItemsName", cpmItemsName);
            props.setProperty("legacyPerkMenu", Boolean.toString(legacyPerkMenu));
            props.setProperty("wallCustom", Boolean.toString(wallCustom));
            props.setProperty("acidCustom", Boolean.toString(acidCustom));
            props.setProperty("wallHue", Float.toString(wallHue));
            props.setProperty("wallShade", Float.toString(wallShade));
            props.setProperty("acidHue", Float.toString(acidHue));
            props.setProperty("acidShade", Float.toString(acidShade));
            props.setProperty("stomachPattern", stomachPattern.name());
            props.setProperty("customSounds", Boolean.toString(useCustomSounds));
            for (GulpSound s : GulpSound.values()) {
                props.setProperty("sound." + s.name(), Boolean.toString(isOn(s)));
                props.setProperty("volume." + s.name(), Float.toString(volume(s)));
            }
            try (OutputStream out = Files.newOutputStream(file())) {
                props.store(out, "Gulp client settings");
            }
        } catch (Exception ignored) {
        }
    }
}
