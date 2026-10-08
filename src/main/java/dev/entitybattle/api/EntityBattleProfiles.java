package dev.entitybattle.api;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import dev.entitybattle.EntityBattleConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/** Datapack profiles take precedence over profiles registered by another mod. */
public final class EntityBattleProfiles {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, EntityBattleProfile> CODE = new HashMap<>();
    private static volatile Map<ResourceLocation, EntityBattleProfile> data = Map.of();
    private static volatile boolean serverProfilesLoaded;

    private EntityBattleProfiles() {}

    public static synchronized void register(EntityBattleProfile profile) {
        CODE.put(profile.entity(), profile);
        refreshSpeciesAvailability();
    }

    /** Built-in optional sources stay hidden until their entity and effective profile exist.
     * Use Cobblemon's own flag/list so every registry consumer sees the same availability. */
    public static synchronized void refreshSpeciesAvailability() {
        // Remote clients must keep the flags carried by Cobblemon's species sync.
        if (!serverProfilesLoaded) return;
        var profiles = new HashMap<>(CODE);
        profiles.putAll(data);
        var available = new java.util.HashSet<ResourceLocation>();
        profiles.forEach((source, profile) -> {
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(source)) available.add(profile.species());
        });
        PokemonSpecies.getSpecies().forEach(species -> {
            if (species.getResourceIdentifier().getNamespace().equals("entitybattle"))
                species.setImplemented(available.contains(species.getResourceIdentifier()));
        });
    }

    public static void registerSpeciesAvailability() {
        PokemonSpecies.INSTANCE.getObservable().subscribe(registry -> refreshSpeciesAvailability());
    }

    @SubscribeEvent
    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        serverProfilesLoaded = false;
        data = Map.of();
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

    public static EntityBattleProfile.WorldMode worldMode(EntityBattleProfile profile) {
        var source = EntityBattleSources.get(profile.entity());
        if (profile.boss() && source != null && source.manualWorld()) return EntityBattleProfile.WorldMode.NATIVE_MOB;
        return profile.boss() ? EntityBattleConfig.BOSS_WORLD_MODE.get() : profile.worldMode();
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
        ids.removeIf(id -> worldMode(data.getOrDefault(id, CODE.get(id)))
                != EntityBattleProfile.WorldMode.NATIVE_MOB);
        for (ResourceLocation part : EntityBattleSources.partIds())
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(part)) ids.add(part);
        return java.util.Set.copyOf(ids);
    }

    /** Species-to-source map used by Cobblemon's lightweight GUI packets. */
    public static synchronized java.util.Map<ResourceLocation, ResourceLocation> visualSpeciesMap() {
        java.util.Map<ResourceLocation, ResourceLocation> result = new java.util.HashMap<>();
        java.util.Set<ResourceLocation> ambiguous = new java.util.HashSet<>();
        for (ResourceLocation entity : entityIds()) {
            EntityBattleProfile profile = data.getOrDefault(entity, CODE.get(entity));
            if ("cobblemon".equals(profile.species().getNamespace())) continue;
            if (result.putIfAbsent(profile.species(), entity) != null) ambiguous.add(profile.species());
        }
        ambiguous.forEach(result::remove);
        return java.util.Map.copyOf(result);
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
            serverProfilesLoaded = true;
            refreshSpeciesAvailability();
            LOGGER.info("Loaded {} entity battle profiles", loaded.size());
        }

        private static void loadProfile(ResourceLocation path, JsonObject json,
                                        Map<ResourceLocation, EntityBattleProfile> loaded) {
            ResourceLocation entity = ResourceLocation.parse(json.get("entity").getAsString());
            // Built-in integrations are portable across packs: absent source mods
            // should be skipped, while bad IDs in installed mods remain errors.
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)
                    && !"minecraft".equals(entity.getNamespace())
                    && !ModList.get().isLoaded(entity.getNamespace())) return;
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
            EntityBattleProfile.BossBattle bossBattle = null;
            if (json.has("bossBattle")) {
                JsonObject battle = json.getAsJsonObject("bossBattle");
                String mode = battle.get("mode").getAsString();
                if (!"horde".equals(mode) && !"existing_squad".equals(mode)) {
                    throw new JsonParseException("Unknown boss battle mode in " + path);
                }
                bossBattle = new EntityBattleProfile.BossBattle(
                        ResourceLocation.parse(battle.get("minionEntity").getAsString()),
                        battle.get("minionCount").getAsInt(),
                        battle.get("levelOffset").getAsInt(),
                        battle.has("minionHeldItem") ? ResourceLocation.parse(battle.get("minionHeldItem").getAsString()) : null,
                        EntityBattleProfile.RosterMode.valueOf(mode.toUpperCase(java.util.Locale.ROOT)),
                        moves(battle, "minionMoves"));
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(bossBattle.minionEntity())) {
                    throw new JsonParseException("Unknown boss minion type " + bossBattle.minionEntity());
                }
            }
            EntityBattleProfile profile = new EntityBattleProfile(entity, species,
                    level.get("min").getAsInt(), level.get("max").getAsInt(), defeat,
                    catchable, worldMode,
                    json.has("nativeDrops") && json.get("nativeDrops").getAsBoolean(),
                    json.has("nativeExperience") && json.get("nativeExperience").getAsBoolean(),
                    worldBehavior,
                    json.has("boss") && json.get("boss").getAsBoolean(),
                    json.has("heldItem") ? ResourceLocation.parse(json.get("heldItem").getAsString()) : null,
                    bossBattle,
                    moves(json, "moves"));
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)) {
                throw new JsonParseException("Unknown entity type " + entity);
            }
            if (loaded.putIfAbsent(entity, profile) != null) {
                throw new JsonParseException("Duplicate profile for " + entity + " in " + path);
            }
        }

        private static java.util.List<String> moves(JsonObject json, String key) {
            return json.has(key) ? java.util.stream.StreamSupport.stream(json.getAsJsonArray(key).spliterator(), false)
                    .map(JsonElement::getAsString).toList() : java.util.List.of();
        }
    }
}
