package com.example.gulp;

import java.util.Properties;

/**
 * One saved "look" for a stomach screen: the colour of the wall and of the acid (hue and shade, 0 to 1), how full of acid the box is,
 * and the wall pattern. The stomach, breast, cock and unbirth each have their own (see GulpConfig.look); the tail leads to the stomach,
 * so it uses the stomach's. Client-side only.
 */
public final class Look {
    /** false = the original look (neutral dark wall, orange acid) until the player moves a slider or picks a swatch. */
    public boolean wallCustom = false;
    public boolean acidCustom = false;
    public float wallHue = 0.0f;
    public float wallShade = 0.15f;
    public float acidHue = 0.09f;
    public float acidShade = 0.8f;
    /** How full of acid the box looks, 0 (empty) to 1 (full). 0.5 = the bottom half. */
    public float acidLevel = 0.5f;
    public StomachStyle.Pattern pattern = StomachStyle.Pattern.NONE;
    /** How strong the pattern is (0.5 = the original faint look), how big (0.5 = normal size) and its colour (auto = a lighter wall colour). */
    public float patternStrength = 0.5f;
    public float patternSize = 0.5f;
    public boolean patternCustom = false;
    public float patternHue = 0.0f;
    /** How hard the walls pulse in the inside view when someone is inside you (0.5 = the normal heartbeat, 0 = none). */
    public float wallPulse = 0.5f;

    public void reset() {
        wallCustom = false;
        acidCustom = false;
        wallHue = 0.0f;
        wallShade = 0.15f;
        acidHue = 0.09f;
        acidShade = 0.8f;
        acidLevel = 0.5f;
        pattern = StomachStyle.Pattern.NONE;
        patternStrength = 0.5f;
        patternSize = 0.5f;
        patternCustom = false;
        patternHue = 0.0f;
        wallPulse = 0.5f;
    }

    private static float unit(Properties props, String key, float fallback) {
        try {
            return Math.max(0f, Math.min(1f, Float.parseFloat(props.getProperty(key, Float.toString(fallback)))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The stomach uses an empty prefix (the keys the first version saved), the other organs use "breast." and so on. */
    private static String patternKey(String prefix) {
        return prefix.isEmpty() ? "stomachPattern" : prefix + "pattern";
    }

    public void load(Properties props, String prefix) {
        wallCustom = Boolean.parseBoolean(props.getProperty(prefix + "wallCustom", "false"));
        acidCustom = Boolean.parseBoolean(props.getProperty(prefix + "acidCustom", "false"));
        wallHue = unit(props, prefix + "wallHue", wallHue);
        wallShade = unit(props, prefix + "wallShade", wallShade);
        acidHue = unit(props, prefix + "acidHue", acidHue);
        acidShade = unit(props, prefix + "acidShade", acidShade);
        acidLevel = unit(props, prefix + "acidLevel", acidLevel);
        patternStrength = unit(props, prefix + "patternStrength", 0.5f);
        patternSize = unit(props, prefix + "patternSize", 0.5f);
        patternHue = unit(props, prefix + "patternHue", 0.0f);
        patternCustom = Boolean.parseBoolean(props.getProperty(prefix + "patternCustom", "false"));
        wallPulse = unit(props, prefix + "wallPulse", 0.5f);
        try {
            pattern = StomachStyle.Pattern.valueOf(props.getProperty(patternKey(prefix), "NONE"));
        } catch (IllegalArgumentException e) {
            pattern = StomachStyle.Pattern.NONE;
        }
    }

    public void save(Properties props, String prefix) {
        props.setProperty(prefix + "wallCustom", Boolean.toString(wallCustom));
        props.setProperty(prefix + "acidCustom", Boolean.toString(acidCustom));
        props.setProperty(prefix + "wallHue", Float.toString(wallHue));
        props.setProperty(prefix + "wallShade", Float.toString(wallShade));
        props.setProperty(prefix + "acidHue", Float.toString(acidHue));
        props.setProperty(prefix + "acidShade", Float.toString(acidShade));
        props.setProperty(prefix + "acidLevel", Float.toString(acidLevel));
        props.setProperty(prefix + "patternStrength", Float.toString(patternStrength));
        props.setProperty(prefix + "patternSize", Float.toString(patternSize));
        props.setProperty(prefix + "patternHue", Float.toString(patternHue));
        props.setProperty(prefix + "patternCustom", Boolean.toString(patternCustom));
        props.setProperty(prefix + "wallPulse", Float.toString(wallPulse));
        props.setProperty(patternKey(prefix), pattern.name());
    }
}
