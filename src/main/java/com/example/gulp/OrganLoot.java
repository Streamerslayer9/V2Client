package com.example.gulp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What hard mode produces in the extra places (Stomach keeps its normal loot):
 * <ul>
 * <li>Breast: digested mobs and players give Milk (a bucket of milk). Digestion has three levels (thirds of the digestion bar): milk at the
 *     end of level 1 and level 2, and when level 3 is reached, the end of the digestion, the contents are churned into Butter.</li>
 * <li>Cock: a digested player is turned into Cum (a bucket of cum, drunk like milk). Mobs give their normal loot.</li>
 * <li>Unbirth: a digested mob drops its own spawn egg (instead of its loot). If it has no spawn egg, it gives its normal loot.</li>
 * </ul>
 * All of it goes straight into the swallower's inventory, like normal digestion loot. Setting: organLootEnabled.
 */
final class OrganLoot {
    private OrganLoot() { }

    static boolean enabled() { return GulpServerConfig.ORGAN_LOOT_ENABLED.get(); }

    /** How many items a victim of this size is worth: size x per, between 1 and 4. */
    private static int amount(CompoundTag entry, float per) {
        return Math.max(1, Math.min(4, Math.round(entry.getFloat("Size") * per)));
    }

    private static void give(ServerPlayer p, Item item, int count) {
        for (int i = 0; i < count; i++) ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(item)); // buckets stack to 1
    }

    // ------------------------------------------------------------------ breast

    /** Called as a breast entry digests (d of total): milk at the end of digestion level 1 and level 2. */
    static void breastStage(ServerPlayer p, CompoundTag entry, int d, int total) {
        if (!enabled()) return;
        int reached = Math.min(2, (int) (3f * d / Math.max(1, total))); // 0, 1 or 2 levels done (level 3 is the finish)
        int had = entry.getInt("Stage");
        if (reached <= had) return;
        entry.putInt("Stage", reached);
        give(p, Items.MILK_BUCKET, amount(entry, 1.5f));
        StomachLogic.say(p, Component.literal("Digestion level " + reached + ": your breast makes milk from ")
                .append(StomachLogic.entryName(entry)).append("."));
    }

    /** The breast digestion is done (level 3): any milk still owed, and the rest is churned into Butter. */
    static void finishBreast(ServerPlayer p, CompoundTag entry) {
        for (int stage = entry.getInt("Stage") + 1; stage <= 2; stage++) give(p, Items.MILK_BUCKET, amount(entry, 1.5f));
        int butter = amount(entry, 2f);
        give(p, Gulp.BUTTER.get(), butter);
        StomachLogic.say(p, Component.literal("Digestion level 3: ").append(StomachLogic.entryName(entry))
                .append(" was churned into " + butter + " butter."));
    }

    // ------------------------------------------------------------------ cock

    static void finishCock(ServerPlayer p, CompoundTag entry) {
        int n = amount(entry, 1.5f);
        give(p, Gulp.CUM_BUCKET.get(), n);
        StomachLogic.say(p, Component.literal("You turned ").append(StomachLogic.entryName(entry)).append(" into cum."));
    }

    // ------------------------------------------------------------------ unbirth

    /** The spawn egg of the mob in this entry, or null if it has none. */
    static ItemStack spawnEggOf(CompoundTag entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.getCompound("Entity").getString("id"));
        if (id == null) return null;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) return null;
        SpawnEggItem egg = SpawnEggItem.byId(type);
        return egg == null ? null : new ItemStack(egg);
    }

    // ------------------------------------------------------------------ mobs being digested (StomachLogic.digest)

    /**
     * Gives what the organ makes of a digested mob. @return true if that replaces the mob's normal loot.
     */
    static boolean mobDigested(ServerPlayer p, CompoundTag entry, int organ) {
        if (!enabled()) return false;
        if (organ == Organ.BREAST.ordinal()) {
            finishBreast(p, entry);
            return true;
        }
        if (organ == Organ.UNBIRTH_ID) {
            ItemStack egg = spawnEggOf(entry);
            if (egg == null) return false;
            ItemHandlerHelper.giveItemToPlayer(p, egg);
            StomachLogic.say(p, Component.literal("Your womb finished ").append(StomachLogic.entryName(entry)).append(": it left a spawn egg."));
            return true;
        }
        return false;
    }
}
