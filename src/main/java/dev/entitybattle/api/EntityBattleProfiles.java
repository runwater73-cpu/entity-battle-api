package dev.entitybattle.api;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/** Datapack profiles take precedence over profiles registered by another mod. */
public final class EntityBattleProfiles {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, EntityBattleProfile> CODE = new HashMap<>();
    private static volatile Map<ResourceLocation, EntityBattleProfile> data = Map.of();

    private EntityBattleProfiles() {}

    public static synchronized void register(EntityBattleProfile profile) {
        CODE.put(profile.entity(), profile);
    }

    public static EntityBattleProfile get(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        EntityBattleProfile profile = data.get(id);
        if (profile != null) return profile;
        synchronized (EntityBattleProfiles.class) {
            return CODE.get(id);
        }
    }

    public static EntityBattleProfile get(ResourceLocation entityId) {
        EntityBattleProfile profile = data.get(entityId);
        if (profile != null) return profile;
        synchronized (EntityBattleProfiles.class) {
            return CODE.get(entityId);
        }
    }

    /** Effective entity IDs after datapack profiles override code registrations. */
    public static synchronized java.util.Set<ResourceLocation> entityIds() {
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>(CODE.keySet());
        ids.addAll(data.keySet());
        ids.removeIf(id -> {
            EntityBattleProfile profile = data.getOrDefault(id, CODE.get(id));
            if (PokemonSpecies.getByIdentifier(profile.species()) != null) return false;
            LOGGER.warn("Skipping entity battle profile for {}: unknown species {}", id, profile.species());
            return true;
        });
        return java.util.Set.copyOf(ids);
    }

    /** Only native world mobs need the client R-key interception. */
    public static synchronized java.util.Set<ResourceLocation> nativeBattleEntityIds() {
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>(entityIds());
        ids.removeIf(id -> data.getOrDefault(id, CODE.get(id)).worldMode()
                != EntityBattleProfile.WorldMode.NATIVE_MOB);
        return java.util.Set.copyOf(ids);
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() { super(new Gson(), "battle_profiles"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> resources,
                             ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, EntityBattleProfile> loaded = new HashMap<>();
            resources.forEach((path, element) -> {
                try {
                    if (element.isJsonArray()) {
                        for (JsonElement entry : element.getAsJsonArray()) {
                            try {
                                loadProfile(path, entry.getAsJsonObject(), loaded);
                            } catch (RuntimeException exception) {
                                LOGGER.error("Invalid entity battle profile entry {}", path, exception);
                            }
                        }
                    } else {
                        loadProfile(path, element.getAsJsonObject(), loaded);
                    }
                } catch (RuntimeException exception) {
                    LOGGER.error("Invalid entity battle profile {}", path, exception);
                }
            });
            data = Map.copyOf(loaded);
            LOGGER.info("Loaded {} entity battle profiles", loaded.size());
        }

        private static void loadProfile(ResourceLocation path, JsonObject json,
                                        Map<ResourceLocation, EntityBattleProfile> loaded) {
            ResourceLocation entity = ResourceLocation.parse(json.get("entity").getAsString());
            ResourceLocation species = ResourceLocation.parse(json.get("species").getAsString());
            JsonObject level = json.getAsJsonObject("level");
            EntityBattleProfile.DefeatMode defeat = EntityBattleProfile.DefeatMode.valueOf(
                    json.get("defeat").getAsString().toUpperCase(java.util.Locale.ROOT));
            boolean catchable = json.has("catchable") && json.get("catchable").getAsBoolean();
            EntityBattleProfile.WorldMode worldMode = json.has("worldMode")
                    ? EntityBattleProfile.WorldMode.valueOf(json.get("worldMode").getAsString()
                            .toUpperCase(java.util.Locale.ROOT))
                    : EntityBattleProfile.WorldMode.NATIVE_MOB;
            EntityBattleProfile.WorldBehavior worldBehavior = json.has("worldBehavior")
                    ? EntityBattleProfile.WorldBehavior.valueOf(json.get("worldBehavior").getAsString()
                            .toUpperCase(java.util.Locale.ROOT))
                    : EntityBattleProfile.WorldBehavior.PASSIVE;
            EntityBattleProfile profile = new EntityBattleProfile(entity, species,
                    level.get("min").getAsInt(), level.get("max").getAsInt(), defeat,
                    catchable, worldMode,
                    json.has("nativeDrops") && json.get("nativeDrops").getAsBoolean(),
                    json.has("nativeExperience") && json.get("nativeExperience").getAsBoolean(),
                    worldBehavior);
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)) {
                throw new JsonParseException("Unknown entity type " + entity);
            }
            if (loaded.putIfAbsent(entity, profile) != null) {
                throw new JsonParseException("Duplicate profile for " + entity + " in " + path);
            }
        }
    }
}
