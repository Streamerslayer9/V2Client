package com.example.gulp;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What hard mode makes in the extra places (the Stomach keeps its normal loot). "Cum" and "Breast Milk" are just normal buckets of milk with
 * another name, so they drink, stack and work exactly like milk.
 * <ul>
 * <li>Breast: a digested mob or player leaves no loot. It is completely digested and replaced, in the breast storage, by Breast Milk. While
 *     the breast is in hard mode that milk digests too, one batch after the other, and is churned into Butter. The Take Milk and Take Butter
 *     buttons on the stomach screen (Breast tab) hand over the milk as it is, or the butter that is ready.</li>
 * <li>Cock: a digested mob or player leaves no loot, it gives Cum (milk bucket) straight into your inventory.</li>
 * <li>Unbirth: a digested mob drops its own spawn egg (instead of its loot). A mob without a spawn egg gives its normal loot.</li>
 * </ul>
 * Setting: organLootEnabled (off = the normal digestion result everywhere), breastChurnSeconds.
 */
final class OrganLoot {
    private OrganLoot() { }

    static boolean enabled() { return GulpServerConfig.ORGAN_LOOT_ENABLED.get(); }

    /** How many items a victim of this size is worth: size x per, between 1 and 4. */
    private static int amount(CompoundTag entry, float per) {
        return Math.max(1, Math.min(4, Math.round(entry.getFloat("Size") * per)));
    }

    /** A bucket of milk with another name. */
    private static ItemStack milk(String name) {
        return new ItemStack(Items.MILK_BUCKET).setHoverName(Component.literal(name).withStyle(st -> st.withItalic(false)));
    }

    private static void giveMilk(ServerPlayer p, String name, int count) {
        for (int i = 0; i < count; i++) ItemHandlerHelper.giveItemToPlayer(p, milk(name)); // buckets stack to 1
    }

    // ------------------------------------------------------------------ digested prey

    /** @return true if the organ replaced the mob's normal loot (so none is given) */
    static boolean mobDigested(ServerPlayer p, Stomach s, CompoundTag entry, int organ) {
        if (!enabled()) return false;
        if (organ == Organ.BREAST.ordinal()) {
            addMilk(p, s, entry);
            return true;
        }
        if (organ == Organ.COCK.ordinal()) {
            cum(p, entry);
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

    /** A swallowed player's hard-mode digestion finished in this organ. */
    static void playerDigested(ServerPlayer p, Stomach s, CompoundTag entry, int organ) {
        if (!enabled()) return;
        if (organ == Organ.BREAST.ordinal()) addMilk(p, s, entry);
        else if (organ == Organ.COCK.ordinal()) cum(p, entry);
    }

    private static void cum(ServerPlayer p, CompoundTag entry) {
        int n = amount(entry, 1.5f);
        giveMilk(p, "Cum", n);
        StomachLogic.say(p, Component.literal("You turned ").append(StomachLogic.entryName(entry)).append(" into " + n + " cum."));
    }

    private static void addMilk(ServerPlayer p, Stomach s, CompoundTag entry) {
        int n = amount(entry, 1.5f);
        s.breastMilk += n;
        StomachLogic.say(p, Component.literal("Your breast completely digested ").append(StomachLogic.entryName(entry))
                .append(" into " + n + " Breast Milk."));
    }

    // ------------------------------------------------------------------ the breast storage: milk churns into butter

    /** Every tick: while the breast is in hard mode, the stored milk digests one batch at a time and is churned into Butter. */
    static void tickBreast(ServerPlayer p, StomachManager mgr, Stomach s, boolean hard) {
        if (s.breastMilk <= 0 || !hard || !enabled()) return;
        int need = GulpServerConfig.BREAST_CHURN_SECONDS.get() * 20;
        if (++s.breastChurn < need) {
            if (s.breastChurn % 20 == 0) StomachLogic.sync(p, s); // the churn bar on the screen
            return;
        }
        s.breastChurn = 0;
        s.breastMilk--;
        s.breastButter++;
        mgr.setDirty();
        StomachLogic.sync(p, s);
        StomachLogic.say(p, Component.literal("A batch of Breast Milk was churned into Butter."));
    }

    /** The Take Milk (butter = false) and Take Butter (true) buttons. */
    static void take(ServerPlayer p, StomachManager mgr, Stomach s, boolean butter) {
        if (!butter) {
            int n = s.breastMilk;
            if (n <= 0) {
                StomachLogic.say(p, Component.literal("There is no Breast Milk to take."));
                return;
            }
            s.breastMilk = 0;
            s.breastChurn = 0;
            giveMilk(p, "Breast Milk", n);
            StomachLogic.say(p, Component.literal("You took " + n + " Breast Milk."));
        } else {
            int n = s.breastButter;
            if (n <= 0) {
                StomachLogic.say(p, Component.literal("There is no Butter ready yet."));
                return;
            }
            s.breastButter = 0;
            for (int left = n; left > 0; left -= 64) ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(Gulp.BUTTER.get(), Math.min(64, left)));
            StomachLogic.say(p, Component.literal("You took " + n + " Butter.").withStyle(ChatFormatting.YELLOW));
        }
        mgr.setDirty();
        StomachLogic.sync(p, s);
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
}
