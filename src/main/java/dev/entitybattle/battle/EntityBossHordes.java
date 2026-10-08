package dev.entitybattle.battle;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.mojang.logging.LogUtils;
import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonOrigin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/** Prepares configured bosses for Horde Encounters' Cobblemon pve hook. */
public final class EntityBossHordes {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String SUMMONED_MINION = "entitybattle:boss_horde_minion";
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private EntityBossHordes() {}

    public static boolean prepare(ServerPlayer player, PokemonEntity boss) {
        if (boss == null || !(boss.level() instanceof ServerLevel level)
                || boss.getBattleId() != null || !boss.getPokemon().isWild()) return true;

        EntityBattleProfile profile = EntityPokemonOrigin.entityId(boss.getPokemon())
                .map(EntityBattleProfiles::get).orElse(null);
        if (profile == null
                || !profile.species().equals(boss.getPokemon().getSpecies().getResourceIdentifier())) return true;
        if (!profile.boss()) { applyHeldItem(boss, profile.heldItem()); return true; }
        EntityBattleProfile.BossBattle settings = profile.bossBattle();
        if (settings == null) { applyHeldItem(boss, profile.heldItem()); return true; }
        if (settings.mode() == EntityBattleProfile.RosterMode.EXISTING_SQUAD) {
            return EntityKnightSquads.prepare(player, boss, profile);
        }
        applyHeldItem(boss, profile.heldItem());
        if (!ModList.get().isLoaded("hordeencounters")
                || !ModList.get().isLoaded("asymmetricbattles")
                || PENDING.containsKey(boss.getUUID())
                || BattleRegistry.getBattleByParticipatingPlayer(player) != null) return true;
        EntityBattleProfile minionProfile = EntityBattleProfiles.get(settings.minionEntity());
        if (minionProfile == null || minionProfile.boss()) {
            LOGGER.warn("Boss {} has no usable minion profile {}", profile.entity(), settings.minionEntity());
            return true;
        }

        List<PokemonEntity> minions = new ArrayList<>();
        HerdMemory previous = new HerdMemory(boss.getBrain().getMemory(CobblemonMemories.HERD_SIZE),
                boss.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES));
        try {
            for (int i = 0; i < settings.minionCount(); i++) {
                Mob source = (Mob) BuiltInRegistries.ENTITY_TYPE.get(settings.minionEntity()).create(level);
                if (source == null) throw new IllegalStateException("Could not create " + settings.minionEntity());
                source.setPos(boss.getX() + (i % 2 == 0 ? 1.2 : -1.2),
                        boss.getY(), boss.getZ() + (i / 2) * 1.2);
                var converted = EntityNativePokemonConversion.sendOut(source, minionProfile, false);
                if (converted == null) throw new IllegalStateException("Could not send out " + settings.minionEntity());
                minions.add(converted.entity());
                Pokemon minion = converted.pokemon();
                minion.setLevel(Math.clamp(boss.getPokemon().getLevel() + settings.levelOffset(), 1, 100));
                dev.entitybattle.api.EntityPokemonData.initializeMoves(minion,
                        settings.minionMoves().isEmpty() ? minionProfile.moves() : settings.minionMoves());
                minion.setCurrentHealth(minion.getMaxHealth());
                applyHeldItem(converted.entity(), settings.minionHeldItem() != null
                        ? settings.minionHeldItem() : minionProfile.heldItem());
                UncatchableProperty.INSTANCE.uncatchable().apply(minion);
                minion.getPersistentData().putBoolean(SUMMONED_MINION, true);
                minion.onChange(null);
                converted.entity().getBrain().setMemory(CobblemonMemories.HERD_LEADER, boss.getStringUUID());
            }

            List<LivingEntity> visibleMinions = new ArrayList<>(minions);
            boss.getBrain().setMemory(CobblemonMemories.HERD_SIZE, minions.size());
            boss.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                    new EncounterMembers(boss, visibleMinions));
            if (!boss.getBrain().hasMemoryValue(CobblemonMemories.HERD_SIZE)
                    || !boss.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                    || minions.stream().anyMatch(minion ->
                    !minion.getBrain().hasMemoryValue(CobblemonMemories.HERD_LEADER))) {
                throw new IllegalStateException("Horde memories were not retained");
            }
            PENDING.put(boss.getUUID(), new Pending(boss, List.copyOf(minions), previous));
            LOGGER.info("Prepared boss horde {} with {} minions for Horde Encounters", profile.entity(), minions.size());
        } catch (RuntimeException exception) {
            previous.restore(boss);
            recall(minions);
            LOGGER.warn("Could not prepare boss horde {}; using standard battle", profile.entity(), exception);
        }
        return true;
    }

    static void applyHeldItem(PokemonEntity boss, net.minecraft.resources.ResourceLocation itemId) {
        if (itemId == null) return;
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            LOGGER.warn("Boss {} requested unknown held item {}", boss.getPokemon().getSpecies().getResourceIdentifier(), itemId);
            return;
        }
        var pokemon = boss.getPokemon();
        if (!pokemon.heldItem().isEmpty()) return;
        pokemon.swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(itemId)), false, false);
        pokemon.setHeldItemVisible(true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Pending pending : List.copyOf(PENDING.values())) {
            if (!(pending.boss.level() instanceof ServerLevel level)
                    || level.getServer() != event.getServer()) continue;
            PENDING.remove(pending.boss.getUUID());
            pending.previous.restore(pending.boss);
            UUID battleId = pending.boss.getBattleId();
            PokemonBattle battle = battleId == null ? null : BattleRegistry.getBattle(battleId);
            if (battle == null || pending.minions.stream().anyMatch(minion ->
                    !battleId.equals(minion.getBattleId()))) {
                recall(pending.minions);
                LOGGER.warn("Horde Encounters did not start the full boss horde; temporary minions were recalled");
                continue;
            }
            LOGGER.info("Horde Encounters started boss battle {} with {} minions", battleId, pending.minions.size());
            battle.getOnEndHandlers().add(ended -> {
                recall(pending.minions);
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    @SubscribeEvent
    public static void onOrphanedMinion(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity minion)
                || !(minion.level() instanceof ServerLevel level)
                || minion.tickCount % 20 != 0
                || !minion.getPokemon().getPersistentData().getBoolean(SUMMONED_MINION)) return;
        UUID battleId = minion.getBattleId();
        if (battleId == null || BattleRegistry.getBattle(battleId) == null) {
            minion.getPokemon().recall();
            LOGGER.debug("Recalled orphaned boss horde minion in {}", level.dimension().location());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Pending pending : List.copyOf(PENDING.values())) {
            pending.previous.restore(pending.boss);
            recall(pending.minions);
        }
        PENDING.clear();
    }

    static record HerdMemory(Optional<Integer> size, Optional<NearestVisibleLivingEntities> visible) {
        void restore(PokemonEntity boss) {
            boss.getBrain().setMemory(CobblemonMemories.HERD_SIZE, size);
            boss.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, visible);
        }
    }

    /** This encounter's explicit roster, rather than a natural herd's line-of-sight scan.
     * Restored on the next server tick; only our temporary allies are exposed. */
    static final class EncounterMembers extends NearestVisibleLivingEntities {
        private final List<LivingEntity> members;

        EncounterMembers(PokemonEntity boss, List<LivingEntity> members) {
            super(boss, members);
            this.members = List.copyOf(members);
        }

        @Override public Optional<LivingEntity> findClosest(Predicate<LivingEntity> predicate) {
            return find(predicate).findFirst();
        }
        @Override public Iterable<LivingEntity> findAll(Predicate<LivingEntity> predicate) {
            return find(predicate).toList();
        }
        @Override public Stream<LivingEntity> find(Predicate<LivingEntity> predicate) {
            return members.stream().filter(predicate);
        }
        @Override public boolean contains(LivingEntity entity) {
            return members.contains(entity);
        }
        @Override public boolean contains(Predicate<LivingEntity> predicate) {
            return members.stream().anyMatch(predicate);
        }
    }

    private static void recall(List<PokemonEntity> minions) {
        for (PokemonEntity minion : minions) {
            if (minion.getPokemon().isWild()) minion.getPokemon().recall();
        }
    }

    private record Pending(PokemonEntity boss, List<PokemonEntity> minions, HerdMemory previous) {}
}
