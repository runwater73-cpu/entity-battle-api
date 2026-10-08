package dev.entitybattle.battle;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.logging.LogUtils;
import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonOrigin;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Converts an opted-in native spawn into a single Cobblemon world entity. */
public final class EntityPokemonWorldSpawns {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Mob> PENDING_BOSSES = new HashSet<>();

    private EntityPokemonWorldSpawns() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (event.getEntity() instanceof PokemonEntity pokemon) {
            if (EntityNativePokemonConversion.isPermanent(pokemon.getPokemon())) {
                var source = EntityPokemonOrigin.entityId(pokemon.getPokemon()).map(EntityBattleProfiles::get).orElse(null);
                if (source != null && source.boss()) pokemon.setPersistenceRequired();
            }
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (EntityKnightSquads.isRestoringSource(mob)) return;
        if (EntityBossSources.isRestoring(mob)) return;
        EntityBattleProfile profile = EntityBattleProfiles.get(mob.getType());
        if (profile == null || EntityBattleProfiles.worldMode(profile)
                != EntityBattleProfile.WorldMode.POKEMON_ENTITY) return;

        if (profile.boss()) {
            PENDING_BOSSES.add(mob);
            return;
        }

        EntityNativePokemonConversion.Converted converted = EntityNativePokemonConversion.sendOut(mob, profile, false);
        if (converted == null) {
            LOGGER.error("Could not replace native entity {} with PokemonEntity", mob.getUUID());
            return;
        }
        event.setCanceled(true);
        LOGGER.debug("Converted {} ({}) into PokemonEntity {}", mob.getUUID(), profile.entity(),
                converted.entity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Mob mob : Set.copyOf(PENDING_BOSSES)) {
            if (!(mob.level() instanceof ServerLevel level) || level.getServer() != event.getServer()) continue;
            PENDING_BOSSES.remove(mob);
            if (!mob.isAlive() || mob.isRemoved()) continue;
            EntityBattleProfile profile = EntityBattleProfiles.get(mob.getType());
            // Chunk load can join an entity before its section becomes accessible. Cobblemon's
            // sendOut must see an active section, otherwise its successful spawn is not queryable.
            if (level.getEntity(mob.getUUID()) != mob) {
                if (profile != null && EntityBattleProfiles.worldMode(profile) == EntityBattleProfile.WorldMode.POKEMON_ENTITY)
                    PENDING_BOSSES.add(mob);
                continue;
            }
            if (dev.entitybattle.api.EntityBattleSources.denial(mob) != null) {
                if (profile != null && EntityBattleProfiles.worldMode(profile) == EntityBattleProfile.WorldMode.POKEMON_ENTITY)
                    PENDING_BOSSES.add(mob);
                continue;
            }
            if (profile != null && profile.boss() && EntityBattleProfiles.worldMode(profile)
                    == EntityBattleProfile.WorldMode.POKEMON_ENTITY
                    && !EntityNativePokemonConversion.convertPermanently(mob, profile)) {
                LOGGER.warn("Could not convert boss {} ({}) into PokemonEntity", mob.getUUID(), profile.entity());
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof PokemonEntity entity)
                || !entity.getPokemon().isWild() || entity.getPokemon().getCurrentHealth() <= 0
                || !EntityNativePokemonConversion.isPermanent(entity.getPokemon())) return;
        var profile = EntityPokemonOrigin.entityId(entity.getPokemon()).map(EntityBattleProfiles::get).orElse(null);
        if (profile != null && profile.boss()) LOGGER.info("World boss {} ({}) left: reason={}, pos={}, battle={}, age={}",
                entity.getUUID(), profile.entity(), entity.getRemovalReason(), entity.blockPosition(), entity.getBattleId(), entity.tickCount);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING_BOSSES.clear();
    }
}
