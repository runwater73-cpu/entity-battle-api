package dev.entitybattle.api;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;

/** Persists one Cobblemon individual on its original world entity. */
public final class EntityPokemonData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String POKEMON_KEY = "entitybattle:pokemon";
    private static final String WORLD_HEALTH_KEY = "entitybattle:world_health";
    private static final String WORLD_MAX_HEALTH_KEY = "entitybattle:world_max_health";
    private static final String FORCED_UNCATCHABLE_KEY = "entitybattle:forced_uncatchable";

    private EntityPokemonData() {}

    /** Reads an already bound individual without creating one or changing the mob. */
    public static Optional<Pokemon> getExisting(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)) return Optional.empty();
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(POKEMON_KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        try {
            Pokemon pokemon = Pokemon.Companion.loadFromNBT(level.registryAccess(), data.getCompound(POKEMON_KEY));
            return EntityPokemonOrigin.belongsTo(pokemon, mob) ? Optional.of(pokemon) : Optional.empty();
        } catch (RuntimeException exception) {
            LOGGER.error("Could not read Pokemon data for entity {}", mob.getUUID(), exception);
            return Optional.empty();
        }
    }

    /** Returns the existing individual, or creates it once for an eligible mob. */
    public static Pokemon getOrCreate(Mob mob, EntityBattleProfile profile) {
        if (mob == null || profile == null || !mob.isAlive()
                || !(mob.level() instanceof ServerLevel level)
                || !BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).equals(profile.entity())) return null;
        CompoundTag data = mob.getPersistentData();
        if (data.contains(POKEMON_KEY)) {
            if (!data.contains(POKEMON_KEY, Tag.TAG_COMPOUND)) {
                LOGGER.error("Invalid Pokemon data type on entity {}", mob.getUUID());
                return null;
            }
            try {
                Pokemon pokemon = Pokemon.Companion.loadFromNBT(level.registryAccess(), data.getCompound(POKEMON_KEY));
                if (!EntityPokemonOrigin.belongsTo(pokemon, mob)) {
                    LOGGER.error("Pokemon origin does not match entity {}", mob.getUUID());
                    return null;
                }
                boolean appearanceChanged = EntityPokemonOrigin.refreshAppearance(pokemon, mob);
                boolean capturePolicyChanged = applyCapturePolicy(pokemon, profile);
                boolean worldHealthChanged = data.contains(WORLD_HEALTH_KEY, Tag.TAG_FLOAT)
                        && data.contains(WORLD_MAX_HEALTH_KEY, Tag.TAG_FLOAT)
                        && (Float.compare(data.getFloat(WORLD_HEALTH_KEY), mob.getHealth()) != 0
                        || Float.compare(data.getFloat(WORLD_MAX_HEALTH_KEY), mob.getMaxHealth()) != 0);
                if (worldHealthChanged) {
                    pokemon.setCurrentHealth(healthFromWorld(mob, pokemon));
                }
                if (appearanceChanged || capturePolicyChanged || worldHealthChanged) {
                    if (!save(mob, pokemon)) return null;
                }
                return pokemon;
            } catch (RuntimeException exception) {
                LOGGER.error("Could not load Pokemon data for entity {}", mob.getUUID(), exception);
                return null;
            }
        }

        try {
            var species = PokemonSpecies.getByIdentifier(profile.species());
            if (species == null) return null;
            int levelNumber = profile.minLevel()
                    + mob.getRandom().nextInt(profile.maxLevel() - profile.minLevel() + 1);
            Pokemon pokemon = species.create(levelNumber);
            if (!EntityPokemonOrigin.bind(pokemon, mob)) return null;
            EntityPokemonOrigin.refreshAppearance(pokemon, mob);
            applyCapturePolicy(pokemon, profile);
            pokemon.setCurrentHealth(healthFromWorld(mob, pokemon));
            return save(mob, pokemon) ? pokemon : null;
        } catch (RuntimeException exception) {
            LOGGER.error("Could not create Pokemon data for entity {}", mob.getUUID(), exception);
            return null;
        }
    }

    /** Writes the complete Cobblemon state after a battle or an integration change. */
    public static boolean save(Mob mob, Pokemon pokemon) {
        if (mob == null || !EntityPokemonOrigin.belongsTo(pokemon, mob)
                || !(mob.level() instanceof ServerLevel level)) return false;
        try {
            CompoundTag encoded = pokemon.saveToNBT(level.registryAccess(), new CompoundTag());
            CompoundTag data = mob.getPersistentData();
            data.put(POKEMON_KEY, encoded);
            data.putFloat(WORLD_HEALTH_KEY, mob.getHealth());
            data.putFloat(WORLD_MAX_HEALTH_KEY, mob.getMaxHealth());
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Could not save Pokemon data for entity {}", mob.getUUID(), exception);
            return false;
        }
    }

    private static int healthFromWorld(Mob mob, Pokemon pokemon) {
        return Math.max(1, Math.round(pokemon.getMaxHealth() * mob.getHealth()
                / Math.max(1F, mob.getMaxHealth())));
    }

    private static boolean applyCapturePolicy(Pokemon pokemon, EntityBattleProfile profile) {
        CompoundTag data = pokemon.getPersistentData();
        if (!profile.catchable()) {
            if (pokemon.isUncatchable()) return false;
            UncatchableProperty.INSTANCE.uncatchable().apply(pokemon);
            data.putBoolean(FORCED_UNCATCHABLE_KEY, true);
            pokemon.onChange(null);
            return true;
        }
        if (!data.getBoolean(FORCED_UNCATCHABLE_KEY)) return false;
        UncatchableProperty.INSTANCE.catchable().apply(pokemon);
        data.remove(FORCED_UNCATCHABLE_KEY);
        pokemon.onChange(null);
        return true;
    }
}
