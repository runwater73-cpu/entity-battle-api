package dev.entitybattle.compat;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Shared client/server doll identity. Hex remains intact through legacy lowercase/split steps. */
public final class DollModelIds {
    public static final String PREFIX = "geometry.entitybattle_v1.";
    public static final int MAX_ID_LENGTH = 32768;
    private static final Gson JSON = new Gson();
    public record Identity(String species, boolean giant, Set<String> aspects) {}
    private DollModelIds() {}

    public static boolean containsNativeData(Collection<String> aspects) {
        return aspects.stream().anyMatch(value -> value.startsWith("entitybattle-source=")
                || value.startsWith("tr-fuse|") && value.contains("|entitybattle:"));
    }

    public static String encode(String species, boolean giant, Set<String> aspects) {
        var id = ResourceLocation.tryParse(species);
        if (id == null || !id.getNamespace().equals("entitybattle") && !containsNativeData(aspects)) return null;
        String result = PREFIX + HexFormat.of().formatHex(JSON.toJson(new Identity(species, giant,
                new TreeSet<>(aspects))).getBytes(StandardCharsets.UTF_8));
        return result.length() <= MAX_ID_LENGTH ? result : null;
    }

    public static Identity decode(String modelId) {
        if (modelId == null || !modelId.startsWith(PREFIX) || modelId.length() > MAX_ID_LENGTH) return null;
        try {
            var identity = JSON.fromJson(new String(HexFormat.of().parseHex(modelId.substring(PREFIX.length())),
                    StandardCharsets.UTF_8), Identity.class);
            return identity == null || identity.aspects() == null || identity.aspects().contains(null)
                    || identity.species() == null || ResourceLocation.tryParse(identity.species()) == null ? null : identity;
        } catch (RuntimeException invalid) { return null; }
    }
}
