package dev.entitybattle.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonOrigin;
import dev.entitybattle.client.mixin.MobExperienceAccessor;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
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
    private static final String BOSS_REWARD_KEY = "entitybattle:boss_reward_awarded";

    private EntityPokemonNativeRewards() {}

    public static void register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(EntityPokemonNativeRewards::onBattleVictory);
        CobblemonEvents.BATTLE_FAINTED.subscribe(event -> {
            if (EntityBossEncounters.deferFaint(event.getKilled().getOriginalPokemon())) return;
            PokemonEntity entity = event.getKilled().getEntity();
            if (entity == null || !(entity.level() instanceof ServerLevel level)) return;
            if (isBossHordeMinion(entity.getPokemon()) || EntityKnightSquads.isMember(entity.getPokemon())) return;
            ServerPlayer player = null;
            for (var actor : event.getBattle().getActors()) {
                if (actor != event.getKilled().getActor() && actor instanceof PlayerBattleActor playerActor) {
                    player = playerActor.getEntity();
                    break;
                }
            }
            if (player == null) return;
            settleNativeRewards(entity, player);
        });
    }

    static void settleNativeRewards(PokemonEntity entity, ServerPlayer player) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (EntityBossSources.managed(entity.getPokemon())) {
            EntityBossSources.complete(entity, player);
            return;
        }
        EntityBattleProfile profile = profile(entity);
        if (profile == null || (!profile.nativeDrops() && !profile.nativeExperience())
                || entity.getPersistentData().getBoolean(REWARDED_KEY)) return;
        Mob nativeMob = createSource(entity, level);
        if (nativeMob == null) return;
        entity.getPersistentData().putBoolean(REWARDED_KEY, true);
        DamageSource source = level.damageSources().playerAttack(player);
        if (profile.nativeDrops()) {
            for (ItemStack stack : nativeLoot(nativeMob, entity, source, player)) entity.spawnAtLocation(stack);
        }
        if (profile.nativeExperience()) {
            ExperienceOrb.award(level, entity.position(), nativeMob.getExperienceReward(level, player));
        }
    }

    /** Rewards a defeated configured Boss once, using the optional Team Rocket recruit ball. */
    private static void onBattleVictory(BattleVictoryEvent event) {
        if (event.getWasWildCapture()) return;
        ServerPlayer player = event.getWinners().stream()
                .filter(PlayerBattleActor.class::isInstance)
                .map(PlayerBattleActor.class::cast)
                .map(PlayerBattleActor::getEntity)
                .findFirst()
                .orElse(null);
        if (player == null) return;

        for (var actor : event.getLosers()) {
            for (var battlePokemon : actor.getPokemonList()) {
                Pokemon boss = battlePokemon.getOriginalPokemon();
                awardBoss(boss, player);
            }
        }
    }

    static boolean awardBoss(Pokemon boss, ServerPlayer player) {
        if (boss.getPersistentData().getBoolean(BOSS_REWARD_KEY)) return true;
        EntityBattleProfile profile = bossProfile(boss);
        if (profile == null || !boss.isFainted() || !EntityKnightSquads.canReward(boss) || !EntityBossSources.canReward(boss)) return false;
        Pokemon reward = createBossReward(boss);
        if (reward == null || !giveBossReward(player, reward)) {
            LOGGER.error("Could not deliver level 1 reward for Boss {} to {}", profile.entity(), player.getName().getString());
            return false;
        }
        boss.getPersistentData().putBoolean(BOSS_REWARD_KEY, true);
        boss.onChange(null);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "entitybattle.boss_reward", reward.getSpecies().getTranslatedName()), false);
        return true;
    }

    private static Pokemon createBossReward(Pokemon boss) {
        try {
            Pokemon reward = boss.getSpecies().create(1);
            EntityPokemonOrigin.copyPresentation(boss, reward);
            UncatchableProperty.INSTANCE.catchable().apply(reward);
            reward.setCurrentHealth(reward.getMaxHealth());
            return reward;
        } catch (RuntimeException exception) {
            LOGGER.error("Could not create level 1 Boss reward for {}", boss.getSpecies().getResourceIdentifier(), exception);
            return null;
        }
    }

    private static boolean giveBossReward(ServerPlayer player, Pokemon reward) {
        ItemStack recruitBall;
        try {
            recruitBall = recruitBall(reward, player);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not fill Team Rocket recruit ball; using Cobblemon storage", exception);
            recruitBall = ItemStack.EMPTY;
        }
        if (!recruitBall.isEmpty()) {
            return player.getInventory().add(recruitBall) || player.drop(recruitBall, false) != null;
        }

        // Keep the API usable without Team Rocket: party first, then Cobblemon PC overflow.
        if (Cobblemon.INSTANCE.getStorage().getParty(player).add(reward)) return true;
        return Cobblemon.INSTANCE.getStorage().getPC(player).add(reward);
    }

    private static ItemStack recruitBall(Pokemon reward, ServerPlayer player) {
        var itemId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("teamrocket", "recruit_ball");
        var item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) return ItemStack.EMPTY;

        CompoundTag root = new CompoundTag();
        root.putString("rarity", "legendary");
        root.putString("species", reward.getSpecies().getResourceIdentifier().toString());
        root.putInt("level", 1);
        root.putInt("iv_hp", reward.getIvs().getOrDefault(Stats.HP));
        root.putInt("iv_atk", reward.getIvs().getOrDefault(Stats.ATTACK));
        root.putInt("iv_def", reward.getIvs().getOrDefault(Stats.DEFENCE));
        root.putInt("iv_spa", reward.getIvs().getOrDefault(Stats.SPECIAL_ATTACK));
        root.putInt("iv_spd", reward.getIvs().getOrDefault(Stats.SPECIAL_DEFENCE));
        root.putInt("iv_spe", reward.getIvs().getOrDefault(Stats.SPEED));
        root.put("pokemon_nbt", reward.saveToNBT(player.registryAccess(), new CompoundTag()));
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }

    @SubscribeEvent
    public static void onWorldDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof PokemonEntity entity)
                || !(entity.level() instanceof ServerLevel level)) return;
        if (EntityBossSources.managed(entity.getPokemon())) { event.getDrops().clear(); return; }
        if (EntityBossEncounters.awaitingSettlement(entity.getPokemon())) return;
        if (isBossHordeMinion(entity.getPokemon()) || EntityKnightSquads.isMember(entity.getPokemon())) return;
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
        if (EntityBossEncounters.awaitingSettlement(entity.getPokemon())) {
            ((MobExperienceAccessor) entity).entitybattle$setXpReward(0);
            return;
        }
        if (isBossHordeMinion(entity.getPokemon()) || EntityKnightSquads.isMember(entity.getPokemon())) {
            ((MobExperienceAccessor) entity).entitybattle$setXpReward(0);
            return;
        }
        if (EntityBossSources.managed(entity.getPokemon())) {
            EntityBossSources.complete(entity, event.getSource());
            ((MobExperienceAccessor) entity).entitybattle$setXpReward(0);
            return;
        }
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
        return profile(entity.getPokemon());
    }

    private static EntityBattleProfile profile(Pokemon pokemon) {
        if (!pokemon.isWild()) return null;
        return EntityPokemonOrigin.entityId(pokemon)
                .map(EntityBattleProfiles::get)
                .filter(profile -> (EntityBattleProfiles.worldMode(profile) == EntityBattleProfile.WorldMode.POKEMON_ENTITY
                        || EntityNativePokemonConversion.isPermanent(pokemon))
                        && profile.species().equals(pokemon.getSpecies().getResourceIdentifier()))
                .orElse(null);
    }

    private static EntityBattleProfile bossProfile(Pokemon pokemon) {
        if (!pokemon.isWild() || EntityPokemonOrigin.sourceUuid(pokemon).isEmpty()) return null;
        return EntityPokemonOrigin.entityId(pokemon)
                .map(EntityBattleProfiles::get)
                .filter(profile -> profile.boss() && !profile.catchable()
                        && profile.species().equals(pokemon.getSpecies().getResourceIdentifier()))
                .orElse(null);
    }

    private static boolean isBossHordeMinion(Pokemon pokemon) {
        return pokemon.getPersistentData().getBoolean(EntityBossHordes.SUMMONED_MINION);
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
