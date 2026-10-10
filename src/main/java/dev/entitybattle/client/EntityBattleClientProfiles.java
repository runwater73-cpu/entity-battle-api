package dev.entitybattle.client;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Client-side mirror of the server's effective battle profile IDs. */
public final class EntityBattleClientProfiles {
    private static final AtomicReference<Set<ResourceLocation>> IDS =
            new AtomicReference<>(Set.of());
    private static final AtomicReference<Map<ResourceLocation, ResourceLocation>> VISUALS =
            new AtomicReference<>(Map.of());
    private static volatile boolean rChallengeEnabled;

    private EntityBattleClientProfiles() {}

    public static void replace(Set<ResourceLocation> ids, Map<ResourceLocation, ResourceLocation> visuals,
                               boolean enabled) {
        IDS.set(Set.copyOf(ids));
        VISUALS.set(Map.copyOf(visuals));
        dev.entitybattle.compat.CobbledexSpecies.invalidate();
        EntityPokemonNativeVisuals.clearGuiModels();
        EntityBattleModelRepository.registerProfiles(VISUALS.get());
        rChallengeEnabled = enabled;
    }

    public static void clear() {
        dev.entitybattle.compat.CobbledexSpecies.invalidate();
        EntityBattleModelRepository.reloaded();
        IDS.set(Set.of());
        VISUALS.set(Map.of());
        rChallengeEnabled = false;
    }

    public static boolean contains(ResourceLocation entityId) {
        return IDS.get().contains(entityId);
    }

    public static boolean rChallengeEnabled() {
        return rChallengeEnabled;
    }

    public static ResourceLocation sourceForSpecies(ResourceLocation species) {
        ResourceLocation synced = VISUALS.get().get(species);
        if (synced != null) return synced;

        /*
         * Team Rocket and similar client screens can render a newly selected party
         * before a server has sent ProfileSync. Built-in profiles encode their source
         * as entitybattle:<namespace>_<path> (Minecraft sources use entitybattle:<path>).
         * Resolve that convention against the entities actually installed in this
         * client. Optional mods therefore remain optional, while an installed source
         * can render immediately at the title/selection screen.
         */
        if (!"entitybattle".equals(species.getNamespace())) return null;
        String path = species.getPath();
        for (ResourceLocation candidate : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (candidate.getNamespace().equals("minecraft") && candidate.getPath().equals(path)) return candidate;
            String encoded = candidate.getNamespace() + "_" + candidate.getPath();
            if (encoded.equals(path)) return candidate;
        }
        return null;
    }

    public static Map<ResourceLocation, ResourceLocation> visualSpecies() { return VISUALS.get(); }
}
