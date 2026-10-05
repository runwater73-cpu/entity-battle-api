package dev.entitybattle.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.logging.LogUtils;
import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonOrigin;
import dev.entitybattle.client.mixin.MobExperienceAccessor;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

/** Optional native loot-table and Minecraft XP settlement for converted wild Pokemon. */
public final class EntityPokemonNativeRewards {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String REWARDED_KEY = "entitybattle:native_rewards_awarded";

    private EntityPokemonNativeRewards() {}

    public static void register() {
        CobblemonEvents.BATTLE_FAINTED.subscribe(event -> {
            PokemonEntity entity = event.getKilled().getEntity();
            if (entity == null || !(entity.level() instanceof ServerLevel level)) return;
            EntityBattleProfile profile = profile(entity);
            if (profile == null || (!profile.nativeDrops() && !profile.nativeExperience())
                    || entity.getPersistentData().getBoolean(REWARDED_KEY)) return;
            ServerPlayer player = null;
            for (var actor : event.getBattle().getActors()) {
                if (actor != event.getKilled().getActor() && actor instanceof PlayerBattleActor playerActor) {
                    player = playerActor.getEntity();
                    break;
                }
            }
            if (player == null) return;
            Mob nativeMob = createSource(entity, level);
            if (nativeMob == null) return;
            entity.getPersistentData().putBoolean(REWARDED_KEY, true);
            DamageSource source = level.damageSources().playerAttack(player);
            if (profile.nativeDrops()) {
                for (ItemStack stack : nativeLoot(nativeMob, entity, source, player)) {
                    entity.spawnAtLocation(stack);
                }
            }
            if (profile.nativeExperience()) {
                ExperienceOrb.award(level, entity.position(), nativeMob.getExperienceReward(level, player));
            }
        });
    }

    @SubscribeEvent
    public static void onWorldDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof PokemonEntity entity)
                || !(entity.level() instanceof ServerLevel level)) return;
        EntityBattleProfile profile = profile(entity);
        if (profile == null || !profile.nativeDrops()
                || entity.getPersistentData().getBoolean(REWARDED_KEY)) return;
        Mob nativeMob = createSource(entity, level);
        if (nativeMob == null) return;
        entity.getPersistentData().putBoolean(REWARDED_KEY, true);
        ServerPlayer player = event.getSource().getEntity() instanceof ServerPlayer serverPlayer
                ? serverPlayer : null;
        for (ItemStack stack : nativeLoot(nativeMob, entity, event.getSource(), player)) {
            event.getDrops().add(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), stack));
        }
    }

    @SubscribeEvent
    public static void onWorldDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof PokemonEntity entity)
                || !(entity.level() instanceof ServerLevel level)) return;
        EntityBattleProfile profile = profile(entity);
        if (profile == null || !profile.nativeExperience()) return;
        if (entity.getPersistentData().getBoolean(REWARDED_KEY)) {
            ((MobExperienceAccessor) entity).entitybattle$setXpReward(0);
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        Mob nativeMob = createSource(entity, level);
        if (nativeMob != null) {
            ((MobExperienceAccessor) entity).entitybattle$setXpReward(
                    nativeMob.getExperienceReward(level, player));
        }
    }

    private static EntityBattleProfile profile(PokemonEntity entity) {
        if (!entity.getPokemon().isWild()) return null;
        return EntityPokemonOrigin.entityId(entity.getPokemon())
                .map(EntityBattleProfiles::get)
                .filter(profile -> (profile.worldMode() == EntityBattleProfile.WorldMode.POKEMON_ENTITY
                        || EntityNativePokemonConversion.isPermanent(entity.getPokemon()))
                        && profile.species().equals(entity.getPokemon().getSpecies().getResourceIdentifier()))
                .orElse(null);
    }

    private static Mob createSource(PokemonEntity pokemon, ServerLevel level) {
        return EntityPokemonOrigin.entityType(pokemon.getPokemon()).map(type -> {
            Entity created = type.create(level);
            if (!(created instanceof Mob mob)) return null;
            mob.setPos(pokemon.getX(), pokemon.getY(), pokemon.getZ());
            EntityPokemonOrigin.restoreAppearance(pokemon.getPokemon(), mob);
            return mob;
        }).orElse(null);
    }

    private static List<ItemStack> nativeLoot(Mob nativeMob, PokemonEntity pokemon,
                                               DamageSource source, ServerPlayer player) {
        ServerLevel level = (ServerLevel) pokemon.level();
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) return List.of();
        try {
            LootParams.Builder params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, nativeMob)
                    .withParameter(LootContextParams.ORIGIN, pokemon.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
            if (player != null) {
                params.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                        .withLuck(player.getLuck());
            }
            return level.getServer().reloadableRegistries().getLootTable(nativeMob.getLootTable())
                    .getRandomItems(params.create(LootContextParamSets.ENTITY));
        } catch (RuntimeException exception) {
            LOGGER.error("Could not generate native loot for {}", BuiltInRegistries.ENTITY_TYPE.getKey(nativeMob.getType()),
                    exception);
            return List.of();
        }
    }
}
