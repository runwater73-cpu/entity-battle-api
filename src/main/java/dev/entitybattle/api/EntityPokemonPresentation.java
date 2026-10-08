package dev.entitybattle.api;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/** Carries visual identity through Cobblemon's existing lightweight GUI packets. */
public final class EntityPokemonPresentation implements AspectProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SOURCE = "entitybattle-source=";
    private static final String APPEARANCE = "entitybattle-appearance=";
    private static final int MAX_APPEARANCE_BYTES = 4096;
    private final Map<Pokemon, Cached> cache = new WeakHashMap<>();
    private final Set<ResourceLocation> oversizedSources = new HashSet<>();
    private record Cached(ResourceLocation source, CompoundTag appearance, Set<String> aspects) {}
    public record Visual(ResourceLocation source, CompoundTag appearance) {}

    @Override
    public synchronized Set<String> provide(Pokemon pokemon) {
        ResourceLocation source = EntityPokemonOrigin.entityId(pokemon).orElse(null);
        if (source == null) return Set.of();
        CompoundTag appearance = EntityPokemonOrigin.appearance(pokemon).orElseGet(CompoundTag::new);
        Cached previous = cache.get(pokemon);
        if (previous != null && source.equals(previous.source()) && appearance.equals(previous.appearance())) {
            return previous.aspects();
        }
        Set<String> aspects = new HashSet<>();
        aspects.add(SOURCE + source);
        if (!appearance.isEmpty()) {
            byte[] data = appearance.toString().getBytes(StandardCharsets.UTF_8);
            if (data.length > MAX_APPEARANCE_BYTES) {
                if (oversizedSources.add(source)) {
                    LOGGER.warn("Appearance snapshot for {} is {} bytes; GUI packets retain the entity type but omit the variant (limit {})",
                            source, data.length, MAX_APPEARANCE_BYTES);
                }
            } else {
                aspects.add(APPEARANCE + Base64.getUrlEncoder().withoutPadding().encodeToString(data));
            }
        }
        Set<String> result = Set.copyOf(aspects);
        cache.put(pokemon, new Cached(source, appearance, result));
        return result;
    }

    @Override
    public Set<String> provide(PokemonProperties properties) { return Set.of(); }

    /** Decode only presentation data, never the original mob's complete save or AI. */
    public static Visual decode(Set<String> aspects) {
        ResourceLocation source = null;
        String encoded = null;
        for (String aspect : aspects) {
            if (aspect.startsWith(SOURCE)) source = ResourceLocation.tryParse(aspect.substring(SOURCE.length()));
            else if (aspect.startsWith(APPEARANCE)) encoded = aspect.substring(APPEARANCE.length());
        }
        if (source == null) return null;
        CompoundTag appearance = new CompoundTag();
        if (encoded != null && encoded.length() <= (MAX_APPEARANCE_BYTES + 2) / 3 * 4) {
            try {
                byte[] data = Base64.getUrlDecoder().decode(encoded);
                if (data.length <= MAX_APPEARANCE_BYTES) {
                    appearance = TagParser.parseTag(new String(data, StandardCharsets.UTF_8));
                }
            } catch (IllegalArgumentException | com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
                // A malformed appearance must not break a menu; keep the correct entity type.
            }
        }
        return new Visual(source, appearance);
    }
}
