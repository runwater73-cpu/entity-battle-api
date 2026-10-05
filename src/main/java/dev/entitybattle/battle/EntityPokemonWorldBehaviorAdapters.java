package dev.entitybattle.battle;

import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityPokemonOrigin;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;

/** Registry for reusable source-entity behavior adapters. */
public final class EntityPokemonWorldBehaviorAdapters {
    private static final Map<ResourceLocation, EntityPokemonWorldBehaviorAdapter> ADAPTERS = new HashMap<>();
    private static final Map<PokemonEntity, Boolean> INSTALLED = new WeakHashMap<>();

    private EntityPokemonWorldBehaviorAdapters() {}

    public static synchronized void register(ResourceLocation sourceEntity,
                                              EntityPokemonWorldBehaviorAdapter adapter) {
        ADAPTERS.put(sourceEntity, adapter);
    }

    static void install(PokemonEntity pokemon, EntityBattleProfile profile) {
        if (INSTALLED.containsKey(pokemon)) return;
        adapter(pokemon, profile).ifPresent(adapter -> {
            adapter.install(pokemon, profile);
            INSTALLED.put(pokemon, true);
        });
    }

    private static java.util.Optional<EntityPokemonWorldBehaviorAdapter> adapter(
            PokemonEntity pokemon, EntityBattleProfile profile) {
        return EntityPokemonOrigin.entityId(pokemon.getPokemon())
                .filter(profile.entity()::equals)
                .map(ADAPTERS::get)
                .filter(java.util.Objects::nonNull);
    }
}
