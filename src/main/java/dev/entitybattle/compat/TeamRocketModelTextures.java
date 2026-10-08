package dev.entitybattle.compat;

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import dev.entitybattle.client.EntityBattleModelTextures;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** Calls the optional mod's public palette/cache API. Contains no recolouring implementation. */
public final class TeamRocketModelTextures {
    private static Method payload, subSpecies, seed, keep, generate;
    private static boolean failed;
    private record Palette(Set<String> aspects, ResourceLocation donor, long seed, float keep, FloatingState clean) {}
    private static final Map<PosableState, Palette> PALETTES = new WeakHashMap<>();
    private TeamRocketModelTextures() {}
    public static void clear() { PALETTES.clear(); }
    public static void register() {
        if (!ModList.get().isLoaded("teamrocket")) return;
        try {
            var factory = Class.forName("com.jhnwudi666.teamrocket.gene.FusionFormFactory");
            var data = Class.forName("com.jhnwudi666.teamrocket.gene.FusionFormFactory$Payload");
            payload = factory.getMethod("findPayload", Collection.class);
            subSpecies = data.getMethod("subSpecies"); seed = data.getMethod("seed"); keep = data.getMethod("keepProb");
            generate = Class.forName("com.jhnwudi666.teamrocket.client.fusion.FusionTextureCache")
                    .getMethod("getOrCreate", ResourceLocation.class, ResourceLocation.class, long.class, float.class);
            EntityBattleModelTextures.register(ResourceLocation.fromNamespaceAndPath("entitybattle", "teamrocket"), TeamRocketModelTextures::resolve);
        } catch (ReflectiveOperationException exception) { fail(exception); }
    }
    private static ResourceLocation resolve(EntityBattleModelTextures.Context context) {
        String path = context.texture().getPath();
        if (failed || !context.state().getCurrentAspects().contains("tr-fused")
                || !(path.startsWith("textures/entity/") || path.startsWith("textures/model/"))) return context.texture();
        try {
            var palette = PALETTES.get(context.state());
            if (palette == null || !palette.aspects().equals(context.state().getCurrentAspects())) {
                Object fusion = payload.invoke(null, context.state().getCurrentAspects());
                if (fusion == null) return context.texture();
                var donor = ResourceLocation.tryParse((String) subSpecies.invoke(fusion));
                if (donor == null) return context.texture();
                var clean = new FloatingState();var aspects = new HashSet<>(context.state().getCurrentAspects());
                aspects.remove("tr-fused");clean.setCurrentAspects(aspects);
                palette = new Palette(Set.copyOf(context.state().getCurrentAspects()), donor,
                        (Long) seed.invoke(fusion), (Float) keep.invoke(fusion), clean);
                PALETTES.put(context.state(), palette);
            }
            var donorTexture = VaryingModelRepository.INSTANCE.getTexture(palette.donor(), palette.clean());
            var result = (ResourceLocation) generate.invoke(null, context.texture(), donorTexture, palette.seed(), palette.keep());
            return result == null ? context.texture() : result;
        } catch (ReflectiveOperationException | RuntimeException exception) { fail(exception); return context.texture(); }
    }
    private static void fail(Exception exception) {
        if (!failed) com.mojang.logging.LogUtils.getLogger().warn("Optional fusion texture API unavailable; retaining native extra textures", exception);
        failed = true;
    }
}
