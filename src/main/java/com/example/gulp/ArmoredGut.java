package com.example.gulp;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Armored Gut perk: the more prey you carry, the tougher you are. Each rank gives 0.5 armor per prey inside, counting at most
 * MAX_PREY of them (rank 5 with 8 or more prey = 20 armor, a full armor bar). A transient attribute modifier, refreshed every second,
 * so it never gets saved on the player and is gone the moment the prey leave (or the perk is off).
 */
public final class ArmoredGut {
    public static final int MAX_PREY = 8;
    private static final UUID MODIFIER_ID = UUID.fromString("5b7d1c3e-6a42-4f0e-9d11-6c2a8e0f4a91");
    private static final String NAME = "gulp.armored_gut";

    private ArmoredGut() { }

    public static double bonus(int rank, int prey) {
        return rank <= 0 ? 0 : 0.5 * rank * Math.min(MAX_PREY, Math.max(0, prey));
    }

    /** Call every tick; it only does work once a second. */
    public static void tick(ServerPlayer p, Stomach s) {
        if (p.tickCount % 20 != 0) return;
        AttributeInstance armor = p.getAttribute(Attributes.ARMOR);
        if (armor == null) return;
        int rank = GulpServerConfig.ARMORED_GUT_ENABLED.get() ? s.rank(Perk.ARMORED) : 0;
        int prey = rank > 0 ? s.contents.size() : 0; // every entry is one prey (mob, player or villager), in any organ
        double want = bonus(rank, prey);
        AttributeModifier current = armor.getModifier(MODIFIER_ID);
        if (current != null && current.getAmount() == want) return;
        if (current != null) armor.removeModifier(MODIFIER_ID);
        if (want > 0) armor.addTransientModifier(new AttributeModifier(MODIFIER_ID, NAME, want, AttributeModifier.Operation.ADDITION));
    }
}
