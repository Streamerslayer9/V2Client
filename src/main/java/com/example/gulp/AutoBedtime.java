package com.example.gulp;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Auto bedtime: when night falls, a villager who is holding a player (a comfort or a cuddle, not an angry grab) walks to a free bed nearby
 * and lies down in it, and the hold carries on through the night. After about five seconds asleep the player counts as sleeping too, so when
 * everyone in the world is sleeping or held by a sleeping villager the night is skipped, exactly like normal sleeping: morning comes, the
 * weather clears, and the held player is rested (the phantom timer resets, a short regeneration, and any villagers inside them count as
 * having slept too). Turn it off with autoBedtimeEnabled. A held player is a spectator, so vanilla does not count them by itself.
 */
final class AutoBedtime {
    private AutoBedtime() { }

    private static final int BED_RADIUS = 28;
    private static final int SLEEP_TICKS = 100; // asleep this long before it counts, like a player in a bed

    private static final class Plan {
        BlockPos bed;
        int sleepTicks;
        long nextSearch;
        boolean told;
        boolean toldAsleep;
    }

    private static final Map<UUID, Plan> PLANS = new HashMap<>();
    private static MinecraftServer forServer;

    /** True while this villager is heading to bed or asleep with someone in their stomach: the hold must not end until morning. */
    static boolean keeping(Entity villager) {
        Plan plan = PLANS.get(villager.getUUID());
        return plan != null && plan.bed != null && isNight(villager.level().getDayTime());
    }

    private static boolean isNight(long dayTime) {
        long t = dayTime % 24000L;
        return t >= 12542L && t < 23460L; // the same window beds work in
    }

    /** Called every 10 ticks. */
    static void tick(MinecraftServer server) {
        if (!GulpServerConfig.MCA_ENABLED.get() || !GulpServerConfig.AUTO_BEDTIME_ENABLED.get()) return;
        if (forServer != server) {
            forServer = server;
            PLANS.clear();
        }
        // who is held by which resting villager
        Map<UUID, List<ServerPlayer>> held = new LinkedHashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Entity v = VillagerHolding.restfulHolder(p);
            if (v != null) held.computeIfAbsent(v.getUUID(), k -> new ArrayList<>()).add(p);
        }
        // forget plans of villagers who no longer hold anyone
        for (UUID id : new ArrayList<>(PLANS.keySet())) {
            if (!held.containsKey(id)) endPlan(server, id);
        }
        if (held.isEmpty()) return;

        for (Map.Entry<UUID, List<ServerPlayer>> en : held.entrySet()) {
            List<ServerPlayer> players = en.getValue();
            ServerLevel level = players.get(0).serverLevel();
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof LivingEntity v) || !level.dimensionType().bedWorks()) continue;
            Plan plan = PLANS.get(v.getUUID());
            if (!isNight(level.getDayTime())) { // morning: wake up
                if (plan != null) endPlan(server, v.getUUID());
                continue;
            }
            if (plan == null) {
                plan = new Plan();
                PLANS.put(v.getUUID(), plan);
            }
            if (plan.bed == null) {
                if (level.getGameTime() < plan.nextSearch) continue;
                plan.bed = findBed(level, v.blockPosition());
                plan.nextSearch = level.getGameTime() + 600L; // none free: look again in 30 seconds
                if (plan.bed == null) continue;
            }
            if (!plan.told) {
                plan.told = true;
                String bedtime = McaIntegration.flavorOf(v).say(Say.BEDTIME);
                for (ServerPlayer p : players) p.sendSystemMessage(Component.literal("<").append(v.getName()).append("> " + (bedtime != null ? bedtime : "It's late. Let's get some sleep, shall we?")));
            }
            BlockState state = level.getBlockState(plan.bed);
            if (!state.is(BlockTags.BEDS)) { // the bed is gone: find another
                plan.bed = null;
                continue;
            }
            if (v.isSleeping()) {
                plan.sleepTicks += 10;
                if (!plan.toldAsleep && plan.sleepTicks >= SLEEP_TICKS) {
                    plan.toldAsleep = true;
                    for (ServerPlayer p : players) p.sendSystemMessage(Component.literal("You drift off to the sound of ").append(v.getName()).append("'s heartbeat. Zzz..."));
                }
            } else if (v.distanceToSqr(plan.bed.getX() + 0.5, plan.bed.getY(), plan.bed.getZ() + 0.5) <= 3.0 * 3.0) {
                if (v instanceof Mob arriving) arriving.getNavigation().stop();
                v.startSleeping(plan.bed);
                if (state.hasProperty(BedBlock.OCCUPIED)) level.setBlock(plan.bed, state.setValue(BedBlock.OCCUPIED, true), 3);
            } else if (v instanceof Mob mob) {
                mob.getNavigation().moveTo(plan.bed.getX() + 0.5, plan.bed.getY(), plan.bed.getZ() + 0.5, 0.65);
            }
        }
        skipNightIfEveryoneSleeps(server, held);
    }

    /** Vanilla's rule, with held players counted by their villager: enough are asleep, so the night is over. */
    private static void skipNightIfEveryoneSleeps(MinecraftServer server, Map<UUID, List<ServerPlayer>> held) {
        ServerLevel level = server.overworld();
        if (!isNight(level.getDayTime()) || !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return;
        int sleepingHeld = 0, heldTotal = 0;
        for (Map.Entry<UUID, List<ServerPlayer>> en : held.entrySet()) {
            Plan plan = PLANS.get(en.getKey());
            heldTotal += en.getValue().size();
            if (plan != null && plan.sleepTicks >= SLEEP_TICKS) sleepingHeld += en.getValue().size();
        }
        if (sleepingHeld == 0) return;
        int active = 0, sleeping = 0;
        for (ServerPlayer p : level.players()) {
            if (VillagerHolding.isHeld(p)) continue; // counted above
            if (p.isSpectator()) continue;
            active++;
            if (p.isSleepingLongEnough()) sleeping++;
        }
        int total = active + heldTotal;
        int percent = level.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        int required = Math.max(1, (int) Math.ceil(total * percent / 100.0));
        if (sleeping + sleepingHeld < required) return;

        long now = level.getDayTime();
        level.setDayTime(now + 24000L - now % 24000L); // morning
        for (ServerPlayer p : level.players()) {
            if (p.isSleeping()) p.stopSleepInBed(false, false);
        }
        if (level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && (level.isRaining() || level.isThundering())) {
            level.setWeatherParameters(6000, 0, false, false);
        }
        for (Map.Entry<UUID, List<ServerPlayer>> en : held.entrySet()) {
            Plan plan = PLANS.get(en.getKey());
            if (plan == null || plan.sleepTicks < SLEEP_TICKS) continue;
            for (ServerPlayer p : en.getValue()) {
                p.getStats().setValue(p, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 0); // no phantoms: you slept
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0, true, false));
                McaIntegration.onPlayerSlept(p); // the villagers inside you slept too
                Entity v = level.getEntity(en.getKey());
                String morning = v != null ? McaIntegration.flavorOf(v).say(Say.GOOD_MORNING) : null;
                p.sendSystemMessage(Component.literal("<").append(v != null ? v.getName() : Component.literal("Someone"))
                        .append("> " + (morning != null ? morning : "Good morning! That was a lovely sleep.")));
            }
        }
        for (UUID id : new ArrayList<>(PLANS.keySet())) endPlan(server, id);
    }

    /** A villager is up: out of bed, the bed free again. */
    private static void endPlan(MinecraftServer server, UUID villager) {
        Plan plan = PLANS.remove(villager);
        if (plan == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(villager);
            if (e instanceof LivingEntity v) {
                if (v.isSleeping()) v.stopSleeping();
                if (plan.bed != null) {
                    BlockState state = level.getBlockState(plan.bed);
                    if (state.is(BlockTags.BEDS) && state.hasProperty(BedBlock.OCCUPIED)) level.setBlock(plan.bed, state.setValue(BedBlock.OCCUPIED, false), 3);
                }
                break;
            }
        }
    }

    /** The nearest free bed (the head end of one nobody is in) within reach, or null. */
    private static BlockPos findBed(ServerLevel level, BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-BED_RADIUS, -8, -BED_RADIUS), from.offset(BED_RADIUS, 8, BED_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.BEDS) || !state.hasProperty(BedBlock.PART) || state.getValue(BedBlock.PART) != BedPart.HEAD) continue;
            if (state.hasProperty(BedBlock.OCCUPIED) && state.getValue(BedBlock.OCCUPIED)) continue;
            double d = pos.distSqr(from);
            if (d < bestDist) {
                best = pos.immutable();
                bestDist = d;
            }
        }
        return best;
    }
}
