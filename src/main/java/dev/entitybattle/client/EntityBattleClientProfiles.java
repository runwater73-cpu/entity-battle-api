package dev.entitybattle.client;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;

/** Client-side mirror of the server's effective battle profile IDs. */
public final class EntityBattleClientProfiles {
    private static final AtomicReference<Set<ResourceLocation>> IDS =
            new AtomicReference<>(Set.of());
    private static volatile boolean rChallengeEnabled;

    private EntityBattleClientProfiles() {}

    public static void replace(Set<ResourceLocation> ids, boolean enabled) {
        IDS.set(Set.copyOf(ids));
        rChallengeEnabled = enabled;
    }

    public static void clear() {
        IDS.set(Set.of());
        rChallengeEnabled = false;
    }

    public static boolean contains(ResourceLocation entityId) {
        return IDS.get().contains(entityId);
    }

    public static boolean rChallengeEnabled() {
        return rChallengeEnabled;
    }
}
