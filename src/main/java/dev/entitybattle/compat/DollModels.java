package dev.entitybattle.compat;

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.entitybattle.client.EntityBattleClientProfiles;
import dev.entitybattle.client.EntityBattleModelRepository;
import dev.entitybattle.client.EntityBattleNativeModels;
import dev.entitybattle.client.EntityPokemonNativeVisuals;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/** Optional CobblemonDoll registry adapter; geometry stays in the shared model repository. */
public final class DollModels {
    private static final int MAX_ENTRIES = 256;
    private record Entry(DollModelIds.Identity identity, FloatingState state, RuntimeModel model) {}
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>(16, .75F, true);
    private static Map<String, Object> keys;
    private static Constructor<?> keyConstructor;
    private static Method scale, giantScale;
    private static boolean failed;
    private DollModels() {}

    private static DollModelIds.Identity decode(String modelId) {
        if (modelId == null || modelId.length() > DollModelIds.MAX_ID_LENGTH) return null;
        try {
            DollModelIds.Identity identity;
            if (modelId.startsWith(DollModelIds.PREFIX)) {
                identity = DollModelIds.decode(modelId);
            } else if (modelId.startsWith("geometry.")) {
                // Existing dolls can retain their species/fusion seed. Older lowercased
                // Base64 appearance is irrecoverable; recreate those dolls for exact variants.
                var parts = modelId.substring(9).split("\\.");
                String name = parts[0]; boolean giant = name.endsWith("_giant");
                if (giant) name = name.substring(0, name.length() - 6);
                final String speciesName = name;
                Set<String> aspects = new LinkedHashSet<>(Arrays.asList(parts).subList(1, parts.length));
                var candidates = EntityBattleClientProfiles.visualSpecies().keySet().stream()
                        .filter(id -> id.toString().equals(speciesName) || id.getPath().equals(speciesName)).toList();
                if (candidates.size() != 1) return null;
                identity = new DollModelIds.Identity(candidates.getFirst().toString(), giant, aspects);
            } else return null;
            if (identity == null || identity.aspects() == null || identity.aspects().contains(null)) return null;
            var species = ResourceLocation.tryParse(identity.species());
            if (species == null) return null;
            if (EntityBattleClientProfiles.sourceForSpecies(species) == null
                    && !DollModelIds.containsNativeData(identity.aspects())) return null;
            return identity;
        } catch (IllegalArgumentException exception) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static boolean api() {
        if (failed) return false;
        if (keys != null) return true;
        try {
            keys = (Map<String, Object>) Class.forName("com.cobblemondoll.examplemod.client.DollModelInjector")
                    .getField("POKEMON_SPECIES").get(null);
            keyConstructor = Class.forName("com.cobblemondoll.examplemod.client.DollModelInjector$PokemonModelKey")
                    .getConstructor(ResourceLocation.class, Set.class, boolean.class, float.class);
            var config = Class.forName("com.cobblemondoll.examplemod.config.SizeConfig");
            scale = config.getMethod("scaleFor", String.class, double.class);
            giantScale = config.getMethod("giantScaleFor", String.class, double.class);
            return true;
        } catch (ReflectiveOperationException exception) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Optional doll registry API unavailable", exception);
            return false;
        }
    }

    public static Model model(String modelId) {
        var entry = entry(modelId);
        return entry == null ? null : entry.model();
    }

    public static ResourceLocation texture(String modelId) {
        var entry = entry(modelId);
        return entry == null ? null : VaryingModelRepository.INSTANCE.getTexture(
                ResourceLocation.parse(entry.identity().species()), entry.state());
    }

    public static String name(String modelId) {
        var identity = decode(modelId);
        if (identity == null) return null;
        var species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByIdentifier(ResourceLocation.parse(identity.species()));
        return species == null ? null : species.getTranslatedName().getString();
    }

    public static boolean ensure(String modelId) { return entry(modelId) != null; }

    private static Entry entry(String modelId) {
        if (net.minecraft.client.Minecraft.getInstance().level == null) return null;
        var entry = ENTRIES.get(modelId);
        if (entry != null && keys != null && keys.containsKey(modelId)) return entry;
        var identity = entry == null ? decode(modelId) : entry.identity();
        if (identity == null || !api()) return null;
        var species = ResourceLocation.parse(identity.species());
        EntityBattleModelRepository.ensure(species);
        if (!VaryingModelRepository.INSTANCE.getVariations().containsKey(species)) return null;
        try {
            var state = new FloatingState(); state.setCurrentAspects(Set.copyOf(identity.aspects()));
            var visual = EntityPokemonNativeVisuals.modelFor(species, state);
            double extent = 1;
            if (visual != null) {
                EntityBattleNativeModels.prepare(visual, null);
                var bounds = EntityBattleNativeModels.guiBounds(visual);
                extent = Math.max(bounds.getYsize(), Math.max(bounds.getXsize(), bounds.getZsize()))
                        / EntityBattleNativeModels.guiExtent(visual);
            } else {
                var pokemonSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByIdentifier(species);
                if (pokemonSpecies != null) extent = pokemonSpecies.getStandardForm().getHeight() / 10D;
            }
            float factor = ((Double) (identity.giant() ? giantScale : scale)
                    .invoke(null, species.getPath(), extent)).floatValue();
            keys.put(modelId, keyConstructor.newInstance(species, state.getCurrentAspects(), identity.giant(), factor));
            entry = new Entry(identity, state, new RuntimeModel(species, state, factor));
            ENTRIES.put(modelId, entry);
            if (ENTRIES.size() > MAX_ENTRIES) {
                var oldest = ENTRIES.keySet().iterator(); String removed = oldest.next(); oldest.remove(); keys.remove(removed);
            }
            return entry;
        } catch (ReflectiveOperationException exception) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Cannot register native doll model", exception);
            return null;
        }
    }

    /** CobblemonDoll anchors standard 24-unit roots. Our normalized geometry needs a foot anchor. */
    public static void anchor(com.cobblemon.mod.common.client.render.models.blockbench.PosableModel model,
            com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext context, PoseStack stack) {
        if (!EntityBattleModelRepository.isNative(model)) return;
        var state = context.request(com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext.Companion.getPOSABLE_STATE());
        var species = context.request(com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext.Companion.getSPECIES());
        if (state == null || species == null) return;
        var visual = EntityPokemonNativeVisuals.modelFor(species, state);
        if (visual != null) stack.translate(0, 1.5 - EntityBattleNativeModels.guiBounds(visual).getYsize()
                / (2 * EntityBattleNativeModels.guiExtent(visual)), 0);
    }

    public static void clear() {
        if (keys != null) ENTRIES.keySet().forEach(keys::remove);
        ENTRIES.clear();
    }

    /** Called by the optional renderer while its complete material buffer is available. */
    public static boolean render(Model model, PoseStack stack, MultiBufferSource buffers,
                                 int light, int overlay, int color) {
        if (!(model instanceof RuntimeModel runtime)) return false;
        runtime.render(stack, buffers, light, overlay, color);
        return true;
    }

    /** A real model, including a consumer-only fallback for callers outside the optional renderer. */
    private static final class RuntimeModel extends Model {
        private final ResourceLocation species;
        private final FloatingState state;
        private final float scale;

        private RuntimeModel(ResourceLocation species, FloatingState state, float scale) {
            super(RenderType::entityCutoutNoCull);
            this.species = species; this.state = state; this.scale = scale;
        }

        @Override public void renderToBuffer(PoseStack stack, VertexConsumer consumer,
                                             int light, int overlay, int color) {
            render(stack, type -> consumer, light, overlay, color);
        }

        private void render(PoseStack stack, MultiBufferSource buffers, int light, int overlay, int color) {
            var repository = VaryingModelRepository.INSTANCE;
            var poser = repository.getPoser(species, state);
            if (poser == null) return;
            var context = new RenderContext();
            context.put(RenderContext.Companion.getSPECIES(), species);
            context.put(RenderContext.Companion.getASPECTS(), state.getCurrentAspects());
            context.put(RenderContext.Companion.getPOSABLE_STATE(), state);
            RenderContext previousContext;
            try { previousContext = poser.getContext(); }
            catch (kotlin.UninitializedPropertyAccessException uninitialized) { previousContext = new RenderContext(); }
            poser.setContext(context);
            state.setCurrentModel(poser);
            state.setPoseToFirstSuitable(com.cobblemon.mod.common.entity.PoseType.STAND);
            stack.pushPose();
            try {
                poser.applyAnimations(null, state, 0, 0, 0, 0, 0);
                stack.scale(scale, scale, scale);
                stack.translate(0, 1.5 / scale, 0);
                anchor(poser, context, stack);
                var texture = repository.getTexture(species, state);
                poser.setLayerContext(buffers, state, repository.getLayers(species, state));
                poser.render(context, stack, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay, color);
            } finally {
                poser.resetLayerContext();
                poser.setDefault();
                poser.setContext(previousContext);
                stack.popPose();
            }
        }
    }
}
