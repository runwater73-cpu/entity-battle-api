package dev.entitybattle.api;

import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** Carries the native entity type with the Pokemon through Cobblemon storage. */
public final class EntityPokemonOrigin {
    private static final String DATA_KEY = "entitybattle:origin";
    private static final String ENTITY_KEY = "entity";
    private static final String SOURCE_UUID_KEY = "source_uuid";
    private static final String APPEARANCE_KEY = "appearance";
    private static final Map<EntityType<?>, AppearanceAdapter> APPEARANCES = new IdentityHashMap<>();

    private EntityPokemonOrigin() {}

    public static Optional<ResourceLocation> entityId(Pokemon pokemon) {
        if (pokemon == null) return Optional.empty();
        CompoundTag data = pokemon.getPersistentData();
        if (!data.contains(DATA_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) return Optional.empty();
        return Optional.ofNullable(ResourceLocation.tryParse(data.getCompound(DATA_KEY).getString(ENTITY_KEY)));
    }

    public static Optional<EntityType<?>> entityType(Pokemon pokemon) {
        return entityId(pokemon).filter(BuiltInRegistries.ENTITY_TYPE::containsKey)
                .map(BuiltInRegistries.ENTITY_TYPE::get);
    }

    /** The UUID of the native mob that originally supplied this individual. */
    public static Optional<UUID> sourceUuid(Pokemon pokemon) {
        if (pokemon == null || !pokemon.getPersistentData().contains(DATA_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag origin = pokemon.getPersistentData().getCompound(DATA_KEY);
        if (!origin.contains(SOURCE_UUID_KEY, net.minecraft.nbt.Tag.TAG_INT_ARRAY)) return Optional.empty();
        try {
            return Optional.of(origin.getUUID(SOURCE_UUID_KEY));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static boolean belongsTo(Pokemon pokemon, Mob mob) {
        if (pokemon == null || mob == null || !entityId(pokemon).filter(id ->
                id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()))).isPresent()) return false;
        return sourceUuid(pokemon).filter(mob.getUUID()::equals).isPresent();
    }

    /** Opt-in serialization of an entity's visual variant, equipment, or other presentation state. */
    public static synchronized void registerAppearance(EntityType<?> type, AppearanceAdapter adapter) {
        APPEARANCES.put(Objects.requireNonNull(type), Objects.requireNonNull(adapter));
    }

    public static Optional<CompoundTag> appearance(Pokemon pokemon) {
        if (pokemon == null || !pokemon.getPersistentData().contains(DATA_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag origin = pokemon.getPersistentData().getCompound(DATA_KEY);
        if (!origin.contains(APPEARANCE_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) return Optional.empty();
        return Optional.of(origin.getCompound(APPEARANCE_KEY).copy());
    }

    /** Refreshes saved presentation state without changing the original entity. */
    public static boolean refreshAppearance(Pokemon pokemon, Mob mob) {
        if (!belongsTo(pokemon, mob)) return false;
        AppearanceAdapter adapter = adapter(mob.getType());
        if (adapter == null) return false;
        CompoundTag snapshot = Objects.requireNonNull(adapter.save(mob), "Appearance snapshot cannot be null").copy();
        CompoundTag origin = pokemon.getPersistentData().getCompound(DATA_KEY);
        if (snapshot.equals(origin.getCompound(APPEARANCE_KEY))) return false;
        origin.put(APPEARANCE_KEY, snapshot);
        pokemon.onChange(null);
        return true;
    }

    /** Applies a previously saved variant to a new entity of the same type. */
    public static boolean restoreAppearance(Pokemon pokemon, Mob mob) {
        if (pokemon == null || mob == null || !entityId(pokemon).filter(id ->
                id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()))).isPresent()) return false;
        AppearanceAdapter adapter = adapter(mob.getType());
        Optional<CompoundTag> snapshot = appearance(pokemon);
        if (adapter == null || snapshot.isEmpty()) return false;
        adapter.load(mob, snapshot.get());
        return true;
    }

    /** Applies a snapshot received from the server to a render-only mob. */
    public static boolean restoreAppearance(EntityType<?> type, Mob mob, CompoundTag snapshot) {
        if (mob == null || snapshot == null || mob.getType() != type) return false;
        AppearanceAdapter adapter = adapter(type);
        if (adapter == null) return false;
        adapter.load(mob, snapshot.copy());
        return true;
    }

    private static synchronized AppearanceAdapter adapter(EntityType<?> type) {
        return APPEARANCES.get(type);
    }

    /** Returns false if this individual is already bound to another native mob. */
    public static boolean bind(Pokemon pokemon, Mob mob) {
        if (pokemon == null || mob == null) return false;
        ResourceLocation entity = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        Optional<ResourceLocation> existing = entityId(pokemon);
        if (existing.isPresent()) return belongsTo(pokemon, mob);
        if (pokemon.getPersistentData().contains(DATA_KEY)) return false;

        CompoundTag origin = new CompoundTag();
        origin.putString(ENTITY_KEY, entity.toString());
        origin.putUUID(SOURCE_UUID_KEY, mob.getUUID());
        pokemon.getPersistentData().put(DATA_KEY, origin);
        pokemon.onChange(null);
        return true;
    }

    public interface AppearanceAdapter {
        CompoundTag save(Mob mob);
        void load(Mob mob, CompoundTag snapshot);
    }
}
