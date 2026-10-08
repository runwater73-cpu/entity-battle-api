package dev.entitybattle.client;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
        return VISUALS.get().get(species);
    }

    public static Map<ResourceLocation, ResourceLocation> visualSpecies() { return VISUALS.get(); }
}
