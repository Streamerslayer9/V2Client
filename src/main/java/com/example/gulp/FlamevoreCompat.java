package com.example.gulp;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Flamevore compatibility. Flamevore is a separate mod we never touch: we only ask whether it is installed and,
 * later, read what it does and react to it. Nothing here needs Flamevore at compile time.
 */
public final class FlamevoreCompat {
    private FlamevoreCompat() { }

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Flamevore is found by this word in a mod's id or display name (its jar is flamevoremod-x.y.z.jar), so the exact id doesn't matter. */
    private static final String MATCH = "flamevore";

    private static Boolean loaded;
    private static String modId = "";

    /** @return true if Flamevore is installed (the answer is the same for the whole session, so it is remembered) */
    public static boolean isLoaded() {
        if (loaded == null) {
            ModList list = ModList.get();
            if (list == null) return false; // too early to know; ask again next time
            detect(list);
        }
        return loaded;
    }

    /** @return Flamevore's real mod id once it has been found, or "" */
    public static String modId() {
        isLoaded();
        return modId;
    }

    private static void detect(ModList list) {
        List<String> all = new ArrayList<>();
        String found = null;
        for (IModInfo info : list.getMods()) {
            String id = info.getModId();
            all.add(id);
            if (found == null && !Gulp.ID.equals(id)
                    && (id.toLowerCase(Locale.ROOT).contains(MATCH)
                    || String.valueOf(info.getDisplayName()).toLowerCase(Locale.ROOT).contains(MATCH))) {
                found = id;
            }
        }
        loaded = found != null;
        modId = found != null ? found : "";
        if (found != null) {
            LOGGER.info("[Gulp] Flamevore found (mod id '{}'): the swallowing system choice is on.", found);
        } else {
            LOGGER.info("[Gulp] Flamevore not found among the installed mods: {}", String.join(", ", all));
        }
    }
}
