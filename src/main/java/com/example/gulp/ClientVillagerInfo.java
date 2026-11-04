package com.example.gulp;

import java.util.HashMap;
import java.util.Map;

/** What the server last told this client about villagers (by entity id). Plain Java, no client-only classes. */
public final class ClientVillagerInfo {
    private ClientVillagerInfo() { }

    private static final Map<Integer, VillagerInfoPacket> INFO = new HashMap<>();

    public static void put(VillagerInfoPacket m) { INFO.put(m.entityId, m); }

    /** @return what we know about this villager, or null if the server hasn't said (yet) */
    public static VillagerInfoPacket get(int entityId) { return INFO.get(entityId); }

    public static void clear() { INFO.clear(); }
}
