package dev.entitybattle.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/** Resolves this API's active species from an optional viewer's names or registry IDs. */
public final class CobbledexSpecies {
    private static final Pattern SEPARATORS = Pattern.compile("[\\s_.:'-]");
    private static volatile Map<String, ResourceLocation> aliases;
    static {
        PokemonSpecies.INSTANCE.getObservable().subscribe(registry -> invalidate());
    }
    private CobbledexSpecies() {}
    public static Species resolve(String name) {
        var id = ResourceLocation.tryParse(name);
        if (id == null || !id.getNamespace().equals("entitybattle")) id = aliases().get(normalize(name));
        var species = id == null ? null : PokemonSpecies.getByIdentifier(id);
        return species != null && species.getImplemented() ? species : null;
    }
    public static synchronized void invalidate() { aliases = null; }
    private static synchronized Map<String, ResourceLocation> aliases() {
        if (aliases == null) aliases = buildAliases();
        return aliases;
    }
    private static Map<String, ResourceLocation> buildAliases() {
        var result = new HashMap<String, ResourceLocation>();
        var ambiguous = new HashSet<String>();
        for (var species : PokemonSpecies.getSpecies()) {
            var id = species.getResourceIdentifier();
            if (!id.getNamespace().equals("entitybattle")) continue;
            for (String alias : new String[]{species.getName(), id.getPath(), id.toString()}) {
                String key = normalize(alias);
                var previous = result.putIfAbsent(key, id);
                if (previous != null && !previous.equals(id)) ambiguous.add(key);
            }
        }
        ambiguous.forEach(result::remove);
        return Map.copyOf(result);
    }
    private static String normalize(String name) {
        return SEPARATORS.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
