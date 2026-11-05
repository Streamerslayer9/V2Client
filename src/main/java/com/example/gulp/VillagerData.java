package com.example.gulp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the mod saves about a villager (level, XP, perks, item storage, what they remember about players), stored in the
 * world under the villager's own UUID. It used to live in the entity's persistent data, but MCA villagers can end up sharing
 * that data, so leveling one villager levelled all of them. Here every villager has a compound of their own, and each part of
 * it (the "keys": gulp_comfort, gulp_storage, gulp_memory) is a tag inside it. Server side only.
 */
public class VillagerData extends SavedData {
    private final Map<UUID, CompoundTag> map = new HashMap<>();

    public static VillagerData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(VillagerData::load, VillagerData::new, "gulp_villagers");
    }

    private static VillagerData of(Entity villager) {
        MinecraftServer server = villager.level().getServer();
        return server == null ? null : get(server);
    }

    /** A copy of one part of this villager's data; an empty compound if there is none (or on a client). */
    static CompoundTag getCompound(Entity villager, String key) {
        VillagerData d = of(villager);
        if (d == null) return new CompoundTag();
        CompoundTag mine = d.map.get(villager.getUUID());
        return mine == null ? new CompoundTag() : mine.getCompound(key).copy();
    }

    /** True when this villager's compound under `key` has the boolean `name` set (no copying: used on hot paths). */
    static boolean getFlag(Entity villager, String key, String name) {
        VillagerData d = of(villager);
        if (d == null) return false;
        CompoundTag mine = d.map.get(villager.getUUID());
        return mine != null && mine.contains(key, Tag.TAG_COMPOUND) && mine.getCompound(key).getBoolean(name);
    }

    /** Any kind of tag (the storage is a list), or null if there is none. A copy. */
    static Tag getTag(Entity villager, String key) {
        VillagerData d = of(villager);
        if (d == null) return null;
        CompoundTag mine = d.map.get(villager.getUUID());
        Tag t = mine == null ? null : mine.get(key);
        return t == null ? null : t.copy();
    }

    static void put(Entity villager, String key, Tag tag) {
        VillagerData d = of(villager);
        if (d == null) return;
        d.map.computeIfAbsent(villager.getUUID(), k -> new CompoundTag()).put(key, tag.copy());
        d.setDirty();
    }

    static void remove(Entity villager, String key) {
        VillagerData d = of(villager);
        if (d == null) return;
        CompoundTag mine = d.map.get(villager.getUUID());
        if (mine != null && mine.contains(key)) {
            mine.remove(key);
            d.setDirty();
        }
    }

    /** Throw away everything about this villager (they died). */
    static void forget(Entity villager) {
        VillagerData d = of(villager);
        if (d != null && d.map.remove(villager.getUUID()) != null) d.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag all = new CompoundTag();
        map.forEach((id, tag) -> all.put(id.toString(), tag));
        nbt.put("Villagers", all);
        return nbt;
    }

    public static VillagerData load(CompoundTag nbt) {
        VillagerData d = new VillagerData();
        CompoundTag all = nbt.getCompound("Villagers");
        for (String key : all.getAllKeys()) {
            try {
                d.map.put(UUID.fromString(key), all.getCompound(key));
            } catch (IllegalArgumentException ignored) {
                // not a UUID: skip it
            }
        }
        return d;
    }
}
