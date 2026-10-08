package dev.entitybattle.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonOrigin;
import net.minecraft.resources.ResourceLocation;

/** Cobblemon owns evolution, item use, individual stats and animation. This layer
 * only checks optional targets and updates the API's source presentation. */
public final class EntityPokemonEvolutions {
    private EntityPokemonEvolutions() {}

    public static void register() {
        CobblemonEvents.EVOLUTION_DISPLAY.subscribe(event -> {
            var pokemon = event.getPokemon();
            var source = EntityBattleProfiles.visualSpeciesMap().get(pokemon.getSpecies().getResourceIdentifier());
            if (source != null || EntityPokemonOrigin.entityId(pokemon).isPresent()) {
                EntityPokemonOrigin.evolvePresentation(pokemon, source);
                // 1.8.1 captures the original aspect set before posting this event.
                event.setDisplay(new com.cobblemon.mod.common.pokemon.evolution.CobblemonEvolutionDisplay(
                        event.getDisplay().getId(), pokemon));
            }
        });
        CobblemonEvents.EVOLUTION_TESTED.subscribe(event -> {
            if (!available(event.getEvolution())) event.setResult(false);
        });
        CobblemonEvents.EVOLUTION_ACCEPTED.subscribe(event -> {
            if (!available(event.getEvolution())) event.cancel();
        });
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(event -> {
            var pokemon = event.getPokemon();
            var source = EntityBattleProfiles.visualSpeciesMap().get(pokemon.getSpecies().getResourceIdentifier());
            if (source == null && EntityPokemonOrigin.entityId(pokemon).isEmpty()) return;
            if (!EntityPokemonOrigin.entityId(pokemon).filter(id -> id.equals(source)).isPresent()) {
                EntityBossSources.clearOwnedEvolution(pokemon);
                EntityBossEncounters.clear(pokemon);
                EntityNativePokemonConversion.clearRecovery(pokemon.getEntity());
                EntityPokemonOrigin.evolvePresentation(pokemon, source);
                if (pokemon.getEntity() != null) EntityBattleNetwork.syncNativeVisual(pokemon.getEntity());
            }
        });
    }

    /** Missing optional species must not consume an item or produce an invalid individual. */
    private static boolean available(Evolution evolution) {
        String value = evolution.getResult().getSpecies();
        if (value == null || !value.startsWith("entitybattle:")) return true;
        var id = ResourceLocation.tryParse(value);
        var target = id == null ? null : PokemonSpecies.getByIdentifier(id);
        return target != null && target.getImplemented();
    }
}
