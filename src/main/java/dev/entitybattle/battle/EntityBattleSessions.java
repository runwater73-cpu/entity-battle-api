package dev.entitybattle.battle;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** A native mob exists outside battle; a real PokemonEntity exists during battle. */
public final class EntityBattleSessions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    private static final java.util.Set<UUID> FINISHING = new java.util.HashSet<>();

    private EntityBattleSessions() {}

    public static boolean start(ServerPlayer player, PokemonEntity sent, Mob mob) {
        if (sent == null || !player.getUUID().equals(sent.getOwnerUUID()) || sent.level() != mob.level()) return false;
        return start(player, sent.getPokemon().getUuid(), mob);
    }

    public static boolean start(ServerPlayer player, UUID pokemonId, Mob mob) {
        EntityBattleProfile profile = EntityBattleProfiles.get(mob.getType());
        if (profile == null || profile.worldMode() != EntityBattleProfile.WorldMode.NATIVE_MOB
                || ACTIVE.containsKey(mob.getUUID()) || !mob.isAlive() || pokemonId == null
                || mob.level() != player.level()
                || BattleRegistry.getBattleByParticipatingPlayer(player) != null) return false;
        if (COOLDOWN.getOrDefault(mob.getUUID(), 0) > player.getServer().getTickCount()) return false;
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party.toBattleTeam(false, false, pokemonId).isEmpty()) return false;

        EntityNativePokemonConversion.Converted converted = EntityNativePokemonConversion.sendOut(mob, profile);
        if (converted == null) return false;
        try {
            var result = BattleBuilder.INSTANCE.pve(player, converted.entity(), pokemonId);
            if (!(result instanceof SuccessfulBattleStart success)) {
                converted.pokemon().recall();
                return false;
            }
            // The battle is now owned by Cobblemon. Only after it accepts the encounter
            // can the source be removed and its recovery snapshot attached to the Pokemon.
            EntityNativePokemonConversion.attachRecovery(converted);
            Session session = new Session(mob.getUUID(), player, converted.entity(), converted.pokemon(),
                    converted.snapshot(), profile, success.getBattle());
            ACTIVE.put(mob.getUUID(), session);
            mob.discard();
            success.getBattle().getOnEndHandlers().add(battle -> {
                FINISHING.add(session.sourceId);
                return kotlin.Unit.INSTANCE;
            });
            return true;
        } catch (RuntimeException exception) {
            converted.pokemon().recall();
            LOGGER.error("Could not start battle for native entity {}", mob.getUUID(), exception);
            return false;
        }
    }

    public static void onCaptured(Pokemon pokemon) {
        for (Session session : List.copyOf(ACTIVE.values())) {
            if (session.pokemon.getUuid().equals(pokemon.getUuid())) {
                session.captured = true;
                EntityNativePokemonConversion.clearRecovery(session.pokemonEntity);
                ACTIVE.remove(session.sourceId);
                FINISHING.remove(session.sourceId);
                LOGGER.info("Captured converted native entity {}", session.sourceId);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        for (Session session : List.copyOf(ACTIVE.values())) {
            if (session.player == event.getEntity()) abort(session);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Session session : List.copyOf(ACTIVE.values())) abort(session);
        ACTIVE.clear();
        COOLDOWN.clear();
        FINISHING.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (UUID sourceId : List.copyOf(FINISHING)) {
            FINISHING.remove(sourceId);
            Session session = ACTIVE.get(sourceId);
            if (session != null) finish(session);
        }
        if (event.getServer().getTickCount() % 200 == 0) {
            COOLDOWN.values().removeIf(until -> until <= event.getServer().getTickCount());
        }
    }

    @SubscribeEvent
    public static void onOrphanedPokemon(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity entity)
                || !(entity.level() instanceof ServerLevel level)
                || entity.tickCount % 20 != 0 || !EntityNativePokemonConversion.hasRecovery(entity)) return;
        UUID sourceId = dev.entitybattle.api.EntityPokemonOrigin.sourceUuid(entity.getPokemon()).orElse(null);
        if (sourceId == null || ACTIVE.containsKey(sourceId)
                || entity.getBattleId() != null && BattleRegistry.getBattle(entity.getBattleId()) != null) return;
        if (level.getEntity(sourceId) instanceof Mob) {
            EntityNativePokemonConversion.clearRecovery(entity);
            entity.getPokemon().recall();
            return;
        }
        if (EntityNativePokemonConversion.recover(entity) != null) {
            EntityNativePokemonConversion.clearRecovery(entity);
            entity.getPokemon().recall();
            LOGGER.warn("Recovered native entity {} from an interrupted battle", sourceId);
        }
    }

    private static void finish(Session session) {
        if (!ACTIVE.remove(session.sourceId, session) || session.captured
                || !session.pokemon.isWild()) return;
        COOLDOWN.put(session.sourceId, session.player.getServer().getTickCount() + 200);
        ServerLevel level = (ServerLevel) session.pokemonEntity.level();
        session.pokemon.recall();
        Mob restored = EntityNativePokemonConversion.restore(level, session.snapshot, session.pokemon);
        if (restored == null) {
            LOGGER.error("Could not restore native entity {} after battle", session.sourceId);
            return;
        }
        EntityNativePokemonConversion.clearRecovery(session.pokemonEntity);
        if (session.pokemon.isFainted() && session.profile.defeat() == EntityBattleProfile.DefeatMode.VANILLA_DEATH) {
            restored.hurt(level.damageSources().playerAttack(session.player), Float.MAX_VALUE);
        }
        if (restored.isAlive()) {
            EntityBattleWorldBridge.copyMobHealthToPokemon(restored, session.pokemon);
            EntityPokemonData.save(restored, session.pokemon);
        }
    }

    private static void abort(Session session) {
        if (!ACTIVE.remove(session.sourceId, session)) return;
        FINISHING.remove(session.sourceId);
        if (!session.battle.getEnded()) session.battle.stop();
        session.pokemon.recall();
        ServerLevel level = (ServerLevel) session.pokemonEntity.level();
        if (EntityNativePokemonConversion.restore(level, session.snapshot, session.pokemon) != null) {
            EntityNativePokemonConversion.clearRecovery(session.pokemonEntity);
        }
    }

    private static final class Session {
        final UUID sourceId;
        final ServerPlayer player;
        final PokemonEntity pokemonEntity;
        final Pokemon pokemon;
        final CompoundTag snapshot;
        final EntityBattleProfile profile;
        final PokemonBattle battle;
        boolean captured;

        Session(UUID sourceId, ServerPlayer player, PokemonEntity pokemonEntity, Pokemon pokemon,
                CompoundTag snapshot, EntityBattleProfile profile, PokemonBattle battle) {
            this.sourceId = sourceId;
            this.player = player;
            this.pokemonEntity = pokemonEntity;
            this.pokemon = pokemon;
            this.snapshot = snapshot;
            this.profile = profile;
            this.battle = battle;
        }
    }
}
