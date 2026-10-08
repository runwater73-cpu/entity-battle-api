package dev.entitybattle.battle;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.entitybattle.api.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Persistent source context; one original is restored for death or rollback, never a second combat AI. */
public final class EntityBossSources {
    private static final String SNAPSHOT = "entitybattle:boss_source";
    private static final String HELPERS = "entitybattle:source_helpers";
    public static final String OWNER = "entitybattle:source_boss_owner";
    private static final String RESTORING = "entitybattle:source_restoring";
    private static final String COMPLETED = "entitybattle:source_completed";
    private static final String CANCELLED = "entitybattle:source_death_cancelled";
    private static final String WINNER = "entitybattle:source_winner";
    private EntityBossSources() {}

    public static void register() {
        EntityBattleSources.register(ResourceLocation.parse("twilightforest:quest_ram"), new dev.entitybattle.compat.QuestRamSource());
        EntityBattleSources.register(ResourceLocation.parse("kaleidoscope_twilight:umbral_sunflower"), new EntityBattleSourceAdapter() {});
        var aether = new dev.entitybattle.compat.AetherBossSource();
        for (String name : List.of("slider", "valkyrie_queen", "sun_spirit"))
            EntityBattleSources.register(ResourceLocation.fromNamespaceAndPath("aether", name), aether);
        dev.entitybattle.compat.DeepAetherBossSource.register();
        EntityBattleSources.register(ResourceLocation.fromNamespaceAndPath("minecraft", "wither"), new EntityBattleSourceAdapter() {
            @Override public Component denial(Mob source) {
                return source instanceof WitherBoss wither && wither.getInvulnerableTicks() == 0 ? null
                        : Component.translatable("entitybattle.source.wither_charge");
            }
        });
        EntityBattleSources.register(ResourceLocation.fromNamespaceAndPath("minecraft", "warden"), new EntityBattleSourceAdapter() {
            @Override public Component denial(Mob source) {
                return source.hasPose(Pose.EMERGING) || source.hasPose(Pose.DIGGING)
                        ? Component.translatable("entitybattle.source.warden_transition") : null;
            }
        });
    }

    public static boolean isRestoring(Mob source) { return source.getPersistentData().getBoolean(RESTORING); }
    public static boolean managed(Pokemon pokemon) { return pokemon.isWild() && pokemon.getPersistentData().contains(SNAPSHOT, Tag.TAG_COMPOUND); }
    public static boolean canReward(Pokemon pokemon) {
        return !managed(pokemon) || pokemon.getPersistentData().getBoolean(COMPLETED);
    }
    public static boolean cancelled(Pokemon pokemon) { return pokemon.getPersistentData().getBoolean(CANCELLED); }
    public static boolean victoryPending(Pokemon pokemon) { return pokemon.getPersistentData().hasUUID(WINNER); }
    public static boolean returnsToSource(Pokemon pokemon) {
        var adapter = EntityPokemonOrigin.entityId(pokemon).map(EntityBattleSources::get).orElse(null);
        return managed(pokemon) && adapter != null && (adapter.manualWorld() || !EntityNativePokemonConversion.isPermanent(pokemon));
    }

    static void remember(Mob source, Pokemon pokemon) {
        var profile = EntityBattleProfiles.get(source.getType());
        if (EntityBattleSources.get(source) == null || profile == null || !profile.boss()) return;
        CompoundTag snapshot = snapshot(source);
        pokemon.getPersistentData().put(SNAPSHOT, snapshot);
        pokemon.onChange(null);
    }

    private static CompoundTag snapshot(Entity source) {
        CompoundTag tag = source.saveWithoutId(new CompoundTag());
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString());
        // The Pokemon is saved separately: repeated conversions must not nest old snapshots.
        tag.getCompound("NeoForgeData").remove("entitybattle:pokemon");
        return tag;
    }

    /** Called only once the source is actually replaced. Persist and remove its own helpers. */
    public static void suspend(Mob source, Pokemon pokemon) {
        if (!managed(pokemon) || !(source.level() instanceof ServerLevel level)) return;
        var helpers = new ListTag();
        for (Entity helper : List.copyOf(toList(level.getAllEntities()))) {
            boolean owned = helper.getPersistentData().hasUUID(OWNER)
                    && helper.getPersistentData().getUUID(OWNER).equals(source.getUUID());
            if (!owned && helper instanceof Projectile projectile && projectile.getOwner() != null)
                owned = projectile.getOwner().getUUID().equals(source.getUUID());
            if (!owned && helper instanceof Mob mob && helper != source) {
                Mob controller = EntityBattleSources.resolve(mob);
                owned = controller != null && controller != mob && controller.getUUID().equals(source.getUUID());
            }
            if (!owned || helper == source || helper instanceof PokemonEntity) continue;
            if (helper instanceof Mob mob && mob.isAlive()
                    && !(helper instanceof dev.entitybattle.compat.SourceOwnedAttack)
                    && EntityBattleSources.resolve(mob) == mob) helpers.add(snapshot(mob));
            helper.discard();
        }
        pokemon.getPersistentData().put(HELPERS, helpers);
        pokemon.onChange(null);
    }

    public static boolean complete(PokemonEntity entity, ServerPlayer winner) {
        entity.getPokemon().getPersistentData().putUUID(WINNER, winner.getUUID());
        return complete(entity, winner.serverLevel().damageSources().playerAttack(winner));
    }

    public static boolean complete(PokemonEntity entity, net.minecraft.world.damagesource.DamageSource damage) {
        Pokemon pokemon = entity.getPokemon();
        if (!managed(pokemon)) return false;
        var data = pokemon.getPersistentData();
        if (data.getBoolean(COMPLETED)) return true;
        if (data.getBoolean(CANCELLED)) return false;
        pokemon.onChange(null);
        try {
            Mob source = restore(entity, false);
            if (source == null) return false;
            var adapter = EntityBattleSources.get(source);
            if (adapter == null) return false;
            var profile = EntityBattleProfiles.get(source.getType());
            if (profile != null && !profile.nativeExperience())
                ((dev.entitybattle.client.mixin.MobExperienceAccessor) source).entitybattle$setXpReward(0);
            if (!((dev.entitybattle.client.mixin.LivingDeathStateAccess) source).entitybattle$isDead() && !adapter.complete(source, damage)) {
                // A cancelled death must not repeatedly invoke its source-specific side effects.
                data.putBoolean(CANCELLED, true);
                source.setHealth(Math.max(1, source.getMaxHealth()));
                adapter.reset(source);
                pokemon.onChange(null);
                com.mojang.logging.LogUtils.getLogger().error("Source death was cancelled for {}; Boss reward withheld", source.getUUID());
                return false;
            }
            data.putBoolean(COMPLETED, true);
            data.remove(HELPERS);
            pokemon.onChange(null);
            return true;
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Could not settle source for {}", pokemon.getUuid(), failure);
            return false;
        }
    }

    public static ServerPlayer winner(Pokemon pokemon, ServerLevel level) {
        var data = pokemon.getPersistentData();
        return data.hasUUID(WINNER) ? level.getServer().getPlayerList().getPlayer(data.getUUID(WINNER)) : null;
    }

    public static boolean rollback(PokemonEntity entity) {
        var pokemon = entity.getPokemon();
        if (!managed(pokemon)) return false;
        try {
            Mob source = restore(entity, true);
            if (source == null) return false;
            var adapter = EntityBattleSources.get(source);
            if (adapter != null) adapter.reset(source);
            var helpers = pokemon.getPersistentData().getList(HELPERS, Tag.TAG_COMPOUND);
            ServerLevel level = (ServerLevel) entity.level();
            for (Tag helper : helpers) {
                var tag = ((CompoundTag) helper).copy();
                if (tag.hasUUID("UUID") && level.getEntity(tag.getUUID("UUID")) != null) continue;
                Entity restored = net.minecraft.world.entity.EntityType.loadEntityRecursive(tag, level, e -> e);
                if (restored != null) level.addFreshEntity(restored);
            }
            clear(pokemon);
            EntityBossEncounters.clear(pokemon);
            EntityPokemonData.save(source, pokemon);
            pokemon.recall();
            entity.discard();
            return true;
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Could not roll back source for {}", pokemon.getUuid(), failure);
            return false;
        }
    }

    private static Mob restore(PokemonEntity entity, boolean applyHealth) {
        var pokemon = entity.getPokemon();
        var level = (ServerLevel) entity.level();
        var snapshot = pokemon.getPersistentData().getCompound(SNAPSHOT);
        if (snapshot.hasUUID("UUID") && level.getEntity(snapshot.getUUID("UUID")) instanceof Mob existing) return existing;
        Entity created = net.minecraft.world.entity.EntityType.loadEntityRecursive(snapshot.copy(), level, e -> e);
        if (!(created instanceof Mob source)) return null;
        source.getPersistentData().putBoolean(RESTORING, true);
        if (applyHealth) EntityBattleWorldBridge.applyPokemonHealthToMob(source, pokemon);
        if (!level.addFreshEntity(source)) return null;
        return source;
    }

    private static void clear(Pokemon pokemon) {
        for (String key : List.of(SNAPSHOT, HELPERS, WINNER, CANCELLED, COMPLETED)) pokemon.getPersistentData().remove(key);
        pokemon.onChange(null);
    }
    private static List<Entity> toList(Iterable<Entity> entities) { var list = new ArrayList<Entity>(); entities.forEach(list::add); return list; }

    @SubscribeEvent public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        Entity entity = event.getEntity();
        Entity attackOwner = entity instanceof Projectile projectile ? projectile.getOwner()
                : entity instanceof dev.entitybattle.compat.SourceOwnedAttack attack ? attack.entitybattle$owner() : null;
        if (!(attackOwner instanceof Mob owner) || EntityBattleSources.get(owner) == null) return;
        entity.getPersistentData().putUUID(OWNER, owner.getUUID());
    }
    @SubscribeEvent public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob source) || !isRestoring(source)) return;
        var profile = EntityBattleProfiles.get(source.getType());
        if (profile != null && !profile.nativeDrops()) event.getDrops().clear();
    }
}
