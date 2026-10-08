package dev.entitybattle.client;

import com.cobblemon.mod.common.client.render.ModelAssetVariation;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.pokemon.PokemonPosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Pose;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.ModelPartTransformation;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PoseAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.quirk.ModelQuirk;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.mojang.logging.LogUtils;
import java.util.*;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import dev.entitybattle.EntityBattleMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Native geometry registered in Cobblemon's public model/texture/poser repository. */
@EventBusSubscriber(modid = EntityBattleMod.ID, value = Dist.CLIENT)
public final class EntityBattleModelRepository {
    private static final Logger LOG = LogUtils.getLogger();
    private record Entry(ResourceLocation source, ResourceLocation model, ResourceLocation poser, NativePokemonBone bone,
                         Function1<Bone, PosableModel> factory, VaryingRenderableResolver resolver) {}
    private static final Map<ResourceLocation, Entry> ENTRIES = new HashMap<>();
    private static final Set<ResourceLocation> FAILED = new HashSet<>();
    private static boolean registering;
    private static boolean refresh;

    private EntityBattleModelRepository() {}

    public static void beginRender(com.cobblemon.mod.common.client.render.models.blockbench.PosableModel model) {
        if (model.getRootPart() instanceof NativePokemonBone bone) bone.beginRender();
    }

    public static boolean isNative(PosableModel model) { return model.getRootPart() instanceof NativePokemonBone; }

    /** Native multipart geometry needs uniform GUI scaling to stay inside the UI depth range. */
    public static void guiScale(net.minecraft.resources.ResourceLocation species, PosableState state,
            com.mojang.blaze3d.vertex.PoseStack stack, float x, float y, float z,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (x == y && x > 0 && z > 0 && Math.abs(x * z - 1) < .0001F
                && VaryingModelRepository.INSTANCE.getPoser(species, state).getRootPart() instanceof NativePokemonBone)
            z = x;
        original.call(stack, x, y, z);
    }

    /** Called after the server profile packet, including profiles not yet encountered in the world. */
    public static void registerProfiles(Map<ResourceLocation, ResourceLocation> profiles) {
        if (Minecraft.getInstance().level == null) return;
        boolean changed = false;
        for (var species : List.copyOf(ENTRIES.keySet())) {
            if (!ENTRIES.get(species).source().equals(profiles.get(species))) {
                unregister(species); changed = true;
            }
        }
        int previous = ENTRIES.size();
        profiles.forEach((species, source) -> ensure(species));
        if (changed || ENTRIES.size() != previous) resetTextureExtensions();
        refresh = false;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (refresh && Minecraft.getInstance().level != null)
            registerProfiles(EntityBattleClientProfiles.visualSpecies());
    }

    private static void resetTextureExtensions() {
        // The optional mod caches resolver identities; use its public reload hook after replacing ours.
        if (!net.neoforged.fml.ModList.get().isLoaded("teamrocket")) return;
        try { Class.forName("com.jhnwudi666.teamrocket.client.fusion.FusionVariationInjector")
                .getMethod("onResourceReload").invoke(null); }
        catch (ReflectiveOperationException exception) { LOG.warn("Fusion renderer reload hook unavailable", exception); }
    }

    /** Does not replace author-supplied Cobblemon resource pack models. Render thread only. */
    public static void ensure(ResourceLocation species) {
        if (registering || FAILED.contains(species) || Minecraft.getInstance().level == null) return;
        var repository = VaryingModelRepository.INSTANCE;
        if (repository.getVariations().containsKey(species)) return;
        ResourceLocation source = EntityBattleClientProfiles.sourceForSpecies(species);
        if (source == null) return;
        registering = true;
        Entry candidate = null;
        try {
            var state = new com.cobblemon.mod.common.client.render.models.blockbench.FloatingState();
            var visual = EntityPokemonNativeVisuals.modelFor(species, state);
            if (visual == null) return;
            NativePokemonBone bone = new NativePokemonBone(species, source, visual);
            ResourceLocation model = ResourceLocation.fromNamespaceAndPath("entitybattle",
                    "native/" + species.getNamespace() + "/" + species.getPath());
            ResourceLocation poser = ResourceLocation.fromNamespaceAndPath("entitybattle", "native_poser/"
                    + species.getNamespace() + "/" + species.getPath());
            Function1<Bone, PosableModel> factory = root -> new NativePoser(root, bone);
            var variations = new ArrayList<ModelAssetVariation>();
            variations.add(new ModelAssetVariation(new HashSet<>(), null, poser, model,
                    bone::texture, List.of(), Map.of()));
            var resolver = new VaryingRenderableResolver(species, variations);
            candidate = new Entry(source, model, poser, bone, factory, resolver);
            repository.getTexturedModels().put(model, bone);
            repository.getPosers().put(poser, factory);
            resolver.initialize(repository);
            repository.getVariations().put(species, resolver);
            ENTRIES.put(species, candidate);
        } catch (RuntimeException exception) {
            if (candidate != null) remove(candidate);
            if (FAILED.add(species)) LOG.error("Cannot register native model for {}; retaining native presentation", species, exception);
        } finally { registering = false; }
    }

    public static boolean usesRepository(ResourceLocation species, PosableState state) {
        if (species.getNamespace().equals("cobblemon")
                && dev.entitybattle.api.EntityPokemonPresentation.decode(state.getCurrentAspects()) != null) return false;
        ensure(species);
        var resolver = VaryingModelRepository.INSTANCE.getVariations().get(species);
        if (resolver == null) return false;
        // Author resources and third-party model replacements also use the normal render path.
        return !FAILED.contains(species);
    }

    /** Resource reload invalidates resolver objects, renderer instances and native model parts. */
    public static void reloaded() {
        dev.entitybattle.compat.DollModels.clear();
        for (var entry : ENTRIES.values()) remove(entry);
        ENTRIES.clear(); FAILED.clear();
        EntityBattleModelTextures.clear();
        refresh = true;
        EntityPokemonNativeVisuals.clearResourceModels();
    }

    private static void unregister(ResourceLocation species) {
        var entry = ENTRIES.remove(species);
        if (entry != null) remove(entry);
    }

    private static void remove(Entry entry) {
        var repository = VaryingModelRepository.INSTANCE;
        repository.getTexturedModels().remove(entry.model(), entry.bone());
        repository.getPosers().remove(entry.poser(), entry.factory());
        repository.getVariations().values().removeIf(value -> value == entry.resolver());
    }

    static void renderFailed(ResourceLocation species, net.minecraft.world.entity.Mob visual, RuntimeException exception) {
        unregister(species);
        FAILED.add(species);
        EntityPokemonNativeVisuals.reportFailure(visual, exception);
    }

    private static final class NativePoser extends PokemonPosableModel {
        private final NativePokemonBone bone;
        NativePoser(Bone root, NativePokemonBone bone) {
            super(root); this.bone = bone;
            bone.getChildren().forEach(this::registerPartAndAllNamedChildren);
            // The Bone normalizes each individual's geometry at its first GUI draw, when
            // the world's render camera is ready. Registration must not render previews.
            setProfileScale(1.8F);
            setProfileTranslation(new Vec3(0, 1, 0));
            setProfileSummaryScale(1.35F);
            setProfileSummaryTranslation(new Vec3(0, 1.05, 0));
            setPortraitScale(1.2F);
            // Portrait callers anchor above the clipped frame. Centered native geometry
            // needs the same positive portrait translation for party and battle portraits.
            setPortraitTranslation(new Vec3(0, .95, 0));
        }
        @Override public void registerPoses() {
            getPoses().put("native", new Pose("native", EnumSet.allOf(PoseType.class), null,
                    state -> Unit.INSTANCE, 0, 0, new HashMap<>(), new PoseAnimation[]{bone.animation()},
                    new ModelPartTransformation[0], new ModelQuirk<?>[0]));
        }
    }
}
