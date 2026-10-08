package dev.entitybattle.battle;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityPokemonData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import org.slf4j.Logger;

/** Converts one world entity at a time; a battle conversion retains a recoverable mob snapshot. */
public final class EntityNativePokemonConversion {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SNAPSHOT = "entitybattle:native_snapshot";
    private static final String PERMANENT = "entitybattle:permanent_conversion";

    private EntityNativePokemonConversion() {}

    public record Converted(PokemonEntity entity, Pokemon pokemon, CompoundTag snapshot) {}

    public static Converted sendOut(Mob mob, EntityBattleProfile profile) {
        return sendOut(mob, profile, true);
    }

    public static Converted sendOut(Mob mob, EntityBattleProfile profile, boolean snapshotRequired) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isRemoved()
                || mob instanceof TamableAnimal tameable && tameable.isTame()) return null;
        if (dev.entitybattle.api.EntityBattleSources.denial(mob) != null) return null;
        Pokemon pokemon = EntityPokemonData.getOrCreate(mob, profile);
        if (pokemon == null || pokemon.getCurrentHealth() <= 0) return null;
        CompoundTag snapshot = new CompoundTag();
        if (snapshotRequired) {
            snapshot = mob.saveWithoutId(snapshot);
            snapshot.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        }
        try {
            PokemonEntity entity = pokemon.sendOut(level, mob.position(), null, sent -> {
                sent.setYRot(mob.getYRot());
                sent.setXRot(mob.getXRot());
                // Native bosses often override despawning without setting the vanilla flag.
                // Preserve that lifetime when the replacement uses Cobblemon's aging despawner.
                if (profile.boss() || mob.isPersistenceRequired()) sent.setPersistenceRequired();
                return kotlin.Unit.INSTANCE;
            });
            if (entity == null || level.getEntity(entity.getUUID()) != entity) {
                pokemon.recall();
                return null;
            }
            EntityKnightSquads.rememberSource(mob, entity);
            EntityBossSources.remember(mob, pokemon);
            return new Converted(entity, pokemon, snapshot);
        } catch (RuntimeException exception) {
            pokemon.recall();
            LOGGER.error("Could not send out native entity {}", mob.getUUID(), exception);
            return null;
        }
    }

    public static boolean convertPermanently(Mob mob, EntityBattleProfile profile) {
        Converted converted = sendOut(mob, profile, false);
        if (converted == null) return false;
        converted.pokemon().getPersistentData().putBoolean(PERMANENT, true);
        converted.pokemon().onChange(null);
        EntityBossSources.suspend(mob, converted.pokemon());
        mob.discard();
        if (profile.boss()) LOGGER.info("Converted boss {} ({}) to PokemonEntity {} at {} in {}",
                mob.getUUID(), profile.entity(), converted.entity().getUUID(), mob.blockPosition(), mob.level().dimension().location());
        return true;
    }

    public static boolean isPermanent(Pokemon pokemon) {
        return pokemon.getPersistentData().getBoolean(PERMANENT);
    }

    public static void attachRecovery(Converted converted) {
        converted.entity().getPersistentData().put(SNAPSHOT, converted.snapshot().copy());
    }

    public static void clearRecovery(PokemonEntity entity) {
        if (entity != null) entity.getPersistentData().remove(SNAPSHOT);
    }

    public static boolean hasRecovery(PokemonEntity entity) {
        return entity.getPersistentData().contains(SNAPSHOT, Tag.TAG_COMPOUND);
    }

    public static Mob recover(PokemonEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !hasRecovery(entity)) return null;
        return restore(level, entity.getPersistentData().getCompound(SNAPSHOT), entity.getPokemon());
    }

    public static Mob restore(ServerLevel level, CompoundTag snapshot, Pokemon pokemon) {
        try {
            CompoundTag tag = snapshot.copy();
            Entity created = net.minecraft.world.entity.EntityType.loadEntityRecursive(tag, level, entity -> entity);
            if (!(created instanceof Mob mob)) return null;
            EntityBattleWorldBridge.applyPokemonHealthToMob(mob, pokemon);
            if (!EntityPokemonData.save(mob, pokemon)) return null;
            if (!level.addFreshEntity(mob)) return null;
            return mob;
        } catch (RuntimeException exception) {
            LOGGER.error("Could not restore native entity from Pokemon {}", pokemon.getUuid(), exception);
            return null;
        }
    }
}
