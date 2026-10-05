package dev.entitybattle.battle;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonOrigin;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Adds configurable aggression to converted wild Pokemon using Cobblemon's own fight brain. */
public final class EntityPokemonWorldBehaviors {
    private static final double AGGRO_RANGE = 16D;
    private static final double DISENGAGE_RANGE = 24D;
    private static final long REENGAGE_DELAY = 200L;
    private static final String WAS_BATTLING = "entitybattle:was_battling";
    private static final String COOLDOWN_UNTIL = "entitybattle:aggression_cooldown_until";

    private EntityPokemonWorldBehaviors() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity pokemon)
                || !(pokemon.level() instanceof ServerLevel level)
                || pokemon.tickCount % 10 != 0) return;
        EntityBattleProfile profile = profileFor(pokemon);
        if (profile == null) return;

        if (profile.worldBehavior() != EntityBattleProfile.WorldBehavior.HOSTILE) {
            EntityPokemonWorldBehaviorAdapters.install(pokemon, profile);
            return;
        }

        var data = pokemon.getPersistentData();
        if (pokemon.getBattleId() != null) {
            data.putBoolean(WAS_BATTLING, true);
            pokemon.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }
        if (data.getBoolean(WAS_BATTLING)) {
            data.remove(WAS_BATTLING);
            data.putLong(COOLDOWN_UNTIL, level.getGameTime() + REENGAGE_DELAY);
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL || !pokemon.isAlive()
                || pokemon.isBusy() || level.getGameTime() < data.getLong(COOLDOWN_UNTIL)) {
            pokemon.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }

        LivingEntity current = pokemon.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (current instanceof ServerPlayer player) {
            if (canTarget(pokemon, player, DISENGAGE_RANGE)) return;
            pokemon.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        } else if (current != null && current.isAlive()) {
            return;
        }

        level.getEntitiesOfClass(ServerPlayer.class, pokemon.getBoundingBox().inflate(AGGRO_RANGE),
                        player -> canTarget(pokemon, player, AGGRO_RANGE)
                                && pokemon.getSensing().hasLineOfSight(player))
                .stream().min(Comparator.comparingDouble(pokemon::distanceToSqr))
                .ifPresent(player -> pokemon.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player));
    }

    private static boolean canTarget(PokemonEntity pokemon, ServerPlayer player, double range) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.level() == pokemon.level()
                && pokemon.distanceToSqr(player) <= range * range
                && BattleRegistry.getBattleByParticipatingPlayer(player) == null;
    }

    private static EntityBattleProfile profileFor(PokemonEntity pokemon) {
        if (!pokemon.getPokemon().isWild() || pokemon.getOwnerUUID() != null) return null;
        return EntityPokemonOrigin.entityId(pokemon.getPokemon())
                .map(EntityBattleProfiles::get)
                .filter(profile -> (profile.worldMode() == EntityBattleProfile.WorldMode.POKEMON_ENTITY
                        || EntityNativePokemonConversion.isPermanent(pokemon.getPokemon()))
                        && profile.species().equals(pokemon.getPokemon().getSpecies().getResourceIdentifier()))
                .orElse(null);
    }
}
