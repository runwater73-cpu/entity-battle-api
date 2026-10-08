package dev.entitybattle.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityBattleSources;
import dev.entitybattle.api.EntityPokemonOrigin;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Identity and acquisition policy at TeamRocket's optional recruitment boundary. */
public final class TeamRocketRecruitment {
    private TeamRocketRecruitment() {}

    public static String speciesKey(ResourceLocation id) {
        return id.getNamespace().equals("cobblemon") ? id.getPath() : id.toString();
    }

    /** Recover older path-only balls only when the API mapping is unambiguous.
     * An existing official species always keeps precedence. */
    public static String canonicalSpecies(String value) {
        if (value == null || value.contains(":")) return value;
        var parsed = ResourceLocation.tryParse(value);
        if (parsed == null || PokemonSpecies.getByIdentifier(
                ResourceLocation.fromNamespaceAndPath("cobblemon", parsed.getPath())) != null) return value;
        var matches = EntityBattleProfiles.visualSpeciesMap().keySet().stream()
                .filter(id -> id.getPath().equals(value)).toList();
        return matches.size() == 1 ? matches.getFirst().toString() : value;
    }

    public static boolean managed(Pokemon pokemon) {
        return EntityPokemonOrigin.entityId(pokemon).isPresent()
                || EntityBattleProfiles.visualSpeciesMap().containsKey(pokemon.getSpecies().getResourceIdentifier());
    }

    /** A freshly generated machine result has no source mob, variant or death settlement. */
    public static void presentation(Pokemon pokemon) {
        if (pokemon == null || EntityPokemonOrigin.entityId(pokemon).isPresent()) return;
        var source = EntityBattleProfiles.visualSpeciesMap().get(pokemon.getSpecies().getResourceIdentifier());
        if (source != null) EntityPokemonOrigin.setPresentation(pokemon, source, null);
    }

    /** Preserve owned Boss rewards in balls, but never generate wild-only Bosses or
     * quest sources whose required original interaction cannot run in this machine. */
    public static List<Species> recruitmentPool(List<Species> original) {
        var blocked = new java.util.HashSet<ResourceLocation>();
        for (var id : EntityBattleProfiles.entityIds()) {
            var profile = EntityBattleProfiles.get(id);
            if (!profile.catchable() || EntityBattleSources.get(id) instanceof QuestRamSource)
                blocked.add(profile.species());
        }
        return original.stream().filter(species -> !blocked.contains(species.getResourceIdentifier())).toList();
    }
}
