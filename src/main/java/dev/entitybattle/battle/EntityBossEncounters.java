package dev.entitybattle.battle;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonOrigin;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** A configured wild Boss is settled only after its entire battle has been won. */
public final class EntityBossEncounters {
    private static final String RECOVERY_HP = "entitybattle:encounter_recovery_health";
    private static final Map<UUID, Encounter> ACTIVE = new HashMap<>();
    private EntityBossEncounters() {}

    public static void register() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {
            var battle = event.getBattle();
            for (var actor : battle.getActors()) {
                if (actor.getType() != ActorType.WILD) continue;
                for (var member : actor.getPokemonList()) {
                    Pokemon pokemon = member.getOriginalPokemon();
                    PokemonEntity entity = member.getEntity();
                    var profile = EntityPokemonOrigin.entityId(pokemon).map(EntityBattleProfiles::get).orElse(null);
                    if (entity == null || !pokemon.isWild() || EntityKnightSquads.isMember(pokemon)
                            || profile == null || !profile.boss() || !profile.species().equals(
                                    pokemon.getSpecies().getResourceIdentifier())) continue;
                    if (ACTIVE.containsKey(pokemon.getUuid())) continue;
                    pokemon.getPersistentData().putInt(RECOVERY_HP, Math.max(1, pokemon.getCurrentHealth()));
                    pokemon.onChange(null);
                    ACTIVE.put(pokemon.getUuid(), new Encounter(battle, entity, pokemon));
                }
            }
        });
        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {
            if (event.getWasWildCapture()) return;
            var player = event.getWinners().stream().filter(PlayerBattleActor.class::isInstance)
                    .map(PlayerBattleActor.class::cast).map(PlayerBattleActor::getEntity)
                    .filter(Objects::nonNull).findFirst().orElse(null);
            if (player == null) return;
            for (var actor : event.getLosers()) for (var member : actor.getPokemonList()) {
                Encounter encounter = ACTIVE.get(member.getOriginalPokemon().getUuid());
                if (encounter == null || encounter.battle != event.getBattle() || encounter.won
                        || !encounter.knockedOut) continue;
                encounter.won = true;
                encounter.winner = player;
                encounter.pokemon.setCurrentHealth(0);
                EntityPokemonNativeRewards.settleNativeRewards(encounter.entity, player);
            }
        });
    }

    /** Runs even when the fainted entity has already ceased to be the Pokemon's active state. */
    public static boolean deferFaint(Pokemon pokemon) {
        Encounter encounter = ACTIVE.get(pokemon.getUuid());
        if (encounter == null) return false;
        encounter.knockedOut = true;
        return true;
    }

    public static boolean awaitingSettlement(Pokemon pokemon) {
        return pokemon.getPersistentData().getInt(RECOVERY_HP) > 0;
    }

    /** Logout aborts restore the source immediately, before the next server tick. */
    static void beforeNativeAbort(Pokemon pokemon) {
        Encounter encounter = ACTIVE.get(pokemon.getUuid());
        if (encounter != null && encounter.battle.getEnded() && !encounter.won) finish(encounter);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTick(ServerTickEvent.Post event) {
        for (Encounter encounter : List.copyOf(ACTIVE.values())) {
            if (encounter.entity.level().getServer() == event.getServer() && encounter.battle.getEnded()) {
                finish(encounter);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onStopping(ServerStoppingEvent event) {
        for (Encounter encounter : List.copyOf(ACTIVE.values())) {
            if (encounter.entity.level().getServer() != event.getServer()) continue;
            if (!encounter.battle.getEnded()) encounter.battle.stop();
            finish(encounter);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        for (Encounter encounter : List.copyOf(ACTIVE.values())) {
            if (!EntityBossSources.managed(encounter.pokemon)) continue;
            boolean present = false;
            for (var actor : encounter.battle.getActors())
                if (actor instanceof PlayerBattleActor player && player.getEntity() == event.getEntity()) present = true;
            if (!present) continue;
            if (!encounter.battle.getEnded()) encounter.battle.stop();
            finish(encounter);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOrphan(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity entity)
                || !(entity.level() instanceof ServerLevel)
                || !awaitingSettlement(entity.getPokemon()) || ACTIVE.containsKey(entity.getPokemon().getUuid())
                || entity.getBattleId() != null && BattleRegistry.getBattle(entity.getBattleId()) != null) return;
        // A saved, unfinished encounter can be loaded without a corresponding battle registry entry.
        Pokemon pokemon = entity.getPokemon();
        if (EntityBossSources.cancelled(pokemon)) {
            restore(entity, pokemon);
        } else if (EntityBossSources.victoryPending(pokemon)) {
            var player = EntityBossSources.winner(pokemon, (ServerLevel) entity.level());
            if (player != null && EntityBossSources.complete(entity, player)
                    && EntityPokemonNativeRewards.awardBoss(pokemon, player)) {
                clear(pokemon);
                pokemon.recall();
                entity.discard();
            }
        } else restore(entity, pokemon);
    }

    private static void finish(Encounter encounter) {
        if (encounter.won && EntityBossSources.managed(encounter.pokemon)) {
            if (EntityBossSources.cancelled(encounter.pokemon)) {
                if (!restore(encounter.entity, encounter.pokemon)) return;
            } else {
                if (encounter.winner == null || !EntityBossSources.complete(encounter.entity, encounter.winner)
                        || !EntityPokemonNativeRewards.awardBoss(encounter.pokemon, encounter.winner)) return;
                clear(encounter.pokemon);
                encounter.pokemon.recall();
                encounter.entity.discard();
            }
            ACTIVE.remove(encounter.pokemon.getUuid());
            return;
        }
        if (encounter.won || !encounter.pokemon.isWild()) {
            clear(encounter.pokemon);
        } else if (encounter.knockedOut || encounter.pokemon.isFainted() || EntityBossSources.returnsToSource(encounter.pokemon)) {
            if (!restore(encounter.entity, encounter.pokemon)) return;
        } else {
            clear(encounter.pokemon);
        }
        ACTIVE.remove(encounter.pokemon.getUuid());
    }

    private static boolean restore(PokemonEntity previous, Pokemon pokemon) {
        int health = pokemon.getPersistentData().getInt(RECOVERY_HP);
        if (pokemon.isFainted()) pokemon.setCurrentHealth(Math.clamp(health, 1, pokemon.getMaxHealth()));
        if (EntityBossSources.returnsToSource(pokemon) || EntityBossSources.cancelled(pokemon)) {
            if (!EntityBossSources.rollback(previous)) return false;
            clear(pokemon);
            return true;
        }
        clear(pokemon);
        // Temporary R conversions already have a source snapshot owned by EntityBattleSessions.
        if (EntityNativePokemonConversion.hasRecovery(previous)) return true;
        if (!pokemon.isWild()) return true;
        pokemon.recall();
        var replacement = pokemon.sendOut((ServerLevel) previous.level(), previous.position(), null, entity -> {
            entity.setYRot(previous.getYRot());
            entity.setPersistenceRequired();
            return kotlin.Unit.INSTANCE;
        });
        if (replacement == null) {
            pokemon.getPersistentData().putInt(RECOVERY_HP, Math.max(1, health));
            pokemon.onChange(null);
            com.mojang.logging.LogUtils.getLogger().error("Could not restore unfinished Boss encounter {}", pokemon.getUuid());
            return false;
        }
        return true;
    }

    static void clear(Pokemon pokemon) {
        pokemon.getPersistentData().remove(RECOVERY_HP);
        pokemon.onChange(null);
    }

    private static final class Encounter {
        final PokemonBattle battle;
        final PokemonEntity entity;
        final Pokemon pokemon;
        boolean knockedOut, won;
        net.minecraft.server.level.ServerPlayer winner;
        Encounter(PokemonBattle battle, PokemonEntity entity, Pokemon pokemon) {
            this.battle = battle; this.entity = entity; this.pokemon = pokemon;
        }
    }
}
