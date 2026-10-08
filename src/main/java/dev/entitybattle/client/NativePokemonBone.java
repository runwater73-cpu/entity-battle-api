package dev.entitybattle.client;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PoseAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Cobblemon Bone extension retaining native geometry, animation and feature rendering. */
final class NativePokemonBone implements Bone {
    private final ResourceLocation species, source;
    private final Map<String, Bone> children;
    private final Mob preview;
    private Mob current;
    private PosableState state;
    private PokemonEntity pokemon;
    private float headYaw, headPitch;
    private int pass;

    NativePokemonBone(ResourceLocation species, ResourceLocation source, Mob preview) {
        this.species = species; this.source = source; this.preview = preview;
        EntityBattleNativeModels.prepare(preview, null);
        TwilightBossPoses.preparePortrait(preview);
        children = parts(preview);
    }
    Mob preview() { return preview; }
    void beginRender() { pass = 0; }
    @Override public Map<String, Bone> getChildren() { return children; }
    @Override public void transform(PoseStack stack) { stack.translate(0, -1.5, 0); }

    ResourceLocation texture(PosableState state) {
        Mob visual = visual(state);
        if (visual == null) visual = preview;
        PokemonEntity entity = state.getEntity() instanceof PokemonEntity candidate
                && species.equals(candidate.getPokemon().getSpecies().getResourceIdentifier()) ? candidate : null;
        // GUI and fusion callers resolve textures before applying the pose animation.
        // Material measurement must see the same initialized geometry as the later draw.
        if (entity == null) TwilightBossPoses.preparePortrait(visual);
        EntityBattleNativeModels.prepare(visual, entity);
        return EntityBattleNativeModels.texture(visual);
    }

    private Mob visual(PosableState state) {
        if (state.getEntity() instanceof PokemonEntity entity
                && species.equals(entity.getPokemon().getSpecies().getResourceIdentifier())) {
            var world = EntityPokemonNativeVisuals.modelFor(entity);
            if (world != null) return world;
        }
        // A fusion donor's texture is queried with the primary's state. Ignore foreign source aspects.
        return EntityPokemonNativeVisuals.modelFor(species, state, source);
    }

    PoseAnimation animation() {
        return new PoseAnimation() {
            @Override protected void setupAnim(RenderContext context, PosableModel model, PosableState pose,
                    float swing, float amount, float age, float yaw, float pitch, float intensity) {
                state = pose; headYaw = yaw; headPitch = pitch;
                pokemon = context.getEntity() instanceof PokemonEntity entity ? entity : null;
                current = pokemon == null ? visual(pose) : EntityPokemonNativeVisuals.modelFor(pokemon);
                if (current == null) current = visual(pose);
                if (current != null) {
                    if (pokemon == null) TwilightBossPoses.preparePortrait(current);
                    EntityBattleNativeModels.prepare(current, pokemon);
                }
            }
        };
    }

    @Override public void render(RenderContext context, PoseStack stack, VertexConsumer consumer,
                                 int light, int overlay, int color) {
        var renderState = context.request(RenderContext.Companion.getPOSABLE_STATE());
        if (renderState != null) {
            state = renderState;
            current = visual(state);
            pokemon = context.getEntity() instanceof PokemonEntity entity ? entity : null;
            if (current != null) {
                if (pokemon == null) TwilightBossPoses.preparePortrait(current);
                EntityBattleNativeModels.prepare(current, pokemon);
            }
        }
        Mob visual = current;
        if (visual == null) return;
        // FloatingState stores accumulated animation ticks, not a 0..1 entity interpolation.
        float partial = state == null ? 0 : net.minecraft.util.Mth.frac(state.getPartialTicks());
        var client = Minecraft.getInstance();
        MultiBufferSource buffers = state != null && state.getCurrentModel() != null
                ? state.getCurrentModel().getBufferProvider() : null;
        if (buffers == null) buffers = client.renderBuffers().bufferSource();
        var nativeTexture = texture(state);
        var provider = buffers;
        boolean extraLayer = pass++ > 0;
        var tinted = tint(consumer, color);
        var selectedTexture = com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository.INSTANCE
                .getTexture(species, state);
        // Base geometry uses the resolver's texture with the source material; explicit Cobblemon
        // layers use the supplied layer consumer and do not draw native armour a second time.
        MultiBufferSource resolved = type -> {
            var original = EntityBattleModelTextures.texture(type);
            if (extraLayer) {
                boolean skin = original.isPresent() && (original.get().equals(nativeTexture)
                        || original.get().getPath().startsWith("textures/entity/")
                        || original.get().getPath().startsWith("textures/model/"));
                return skin ? tinted : DISCARD;
            }
            if (original.isEmpty()) return tint(provider.getBuffer(type), color);
            ResourceLocation replacement = original.get().equals(nativeTexture) ? selectedTexture
                    : EntityBattleModelTextures.resolve(new EntityBattleModelTextures.Context(species, state, visual, original.get()));
            return tint(provider.getBuffer(EntityBattleModelTextures.withTexture(type, replacement)), color);
        };
        float yRot = visual.getYRot(), xRot = visual.getXRot(), oldXRot = visual.xRotO;
        float body = visual.yBodyRot, oldBody = visual.yBodyRotO;
        float head = visual.yHeadRot, oldHead = visual.yHeadRotO;
        stack.pushPose();
        try {
            // Cobblemon has already applied entity orientation/scale. Undo only the model basis,
            // then let the source renderer reproduce its geometry, feature layers and native scale.
            // The world wrapper already cancels the living renderer's 24-unit offset;
            // GUI callers add that offset themselves and need it removed here.
            if (pokemon == null) stack.translate(0, -1.5, 0);
            stack.scale(-1, -1, 1);
            stack.mulPose(Axis.YP.rotationDegrees(180));
            if (pokemon == null) {
                var center = EntityBattleNativeModels.guiBounds(visual).getCenter();
                // A species poser is shared, but baby/large/variant display copies can have
                // different dimensions. Fit that individual's geometry without world scaling.
                float fit = 1F / EntityBattleNativeModels.guiExtent(visual);
                stack.scale(fit, fit, fit);
                stack.translate(-center.x, -center.y, -center.z);
            }
            if (pokemon != null) {
                var offset = TwilightBossPoses.offset(pokemon, partial)
                        .yRot((float) Math.toRadians(body));
                stack.translate(offset.x, offset.y, offset.z);
            }
            // World look goals belong to PokemonEntity, not the unticked display Mob.
            // GUI cursor tracking is already interpolated by Cobblemon: do not interpolate
            // it again from a stale xRotO every frame, which produces continuous nodding.
            visual.setYRot(0);
            visual.setXRot(pokemon == null ? headPitch : 0);
            visual.xRotO = visual.getXRot();
            visual.yBodyRot = visual.yBodyRotO = 0;
            visual.yHeadRot = visual.yHeadRotO = headYaw;
            var renderer = client.getEntityRenderDispatcher().getRenderer(visual);
            if (EntityBattleNativeModels.rendersParent(visual)) renderer.render(visual, 0, partial, stack, resolved, light);
            var parts = EntityBattleNativeModels.parts(visual);
            if (parts != null) {
                double x = net.minecraft.util.Mth.lerp(partial, visual.xo, visual.getX());
                double y = net.minecraft.util.Mth.lerp(partial, visual.yo, visual.getY());
                double z = net.minecraft.util.Mth.lerp(partial, visual.zo, visual.getZ());
                for (var part : parts) if (EntityBattleNativeModels.isPartVisible(visual, part)) {
                    stack.pushPose();
                    try {
                        var position = new net.minecraft.world.phys.Vec3(net.minecraft.util.Mth.lerp(partial, part.xo, part.getX()) - x,
                                net.minecraft.util.Mth.lerp(partial, part.yo, part.getY()) - y,
                                net.minecraft.util.Mth.lerp(partial, part.zo, part.getZ()) - z)
                                .yRot((float) Math.toRadians(body));
                        stack.translate(position.x, position.y, position.z);
                        net.minecraft.world.entity.Entity partEntity = part;
                        EntityBattleNativeModels.renderPart(visual, partEntity,
                                net.minecraft.util.Mth.rotLerp(partial, part.yRotO, part.getYRot()) - body,
                                partial, stack, resolved, light);
                    } finally { stack.popPose(); }
                }
            }
        } catch (RuntimeException exception) {
            EntityBattleModelRepository.renderFailed(species, visual, exception);
        } finally {
            visual.setYRot(yRot); visual.setXRot(xRot); visual.xRotO = oldXRot;
            visual.yBodyRot = body; visual.yBodyRotO = oldBody;
            visual.yHeadRot = head; visual.yHeadRotO = oldHead;
            stack.popPose();
        }
    }

    private static VertexConsumer tint(VertexConsumer delegate, int color) {
        if (color == -1) return delegate;
        return new VertexConsumer() {
            public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x,y,z); return this; }
            public VertexConsumer setColor(int r,int g,int b,int a) {
                delegate.setColor(r * ((color >>> 16) & 255) / 255, g * ((color >>> 8) & 255) / 255,
                        b * (color & 255) / 255, a * ((color >>> 24) & 255) / 255); return this;
            }
            public VertexConsumer setUv(float u,float v) { delegate.setUv(u,v); return this; }
            public VertexConsumer setUv1(int u,int v) { delegate.setUv1(u,v); return this; }
            public VertexConsumer setUv2(int u,int v) { delegate.setUv2(u,v); return this; }
            public VertexConsumer setNormal(float x,float y,float z) { delegate.setNormal(x,y,z); return this; }
        };
    }

    private static final VertexConsumer DISCARD = new VertexConsumer() {
        public VertexConsumer addVertex(float x,float y,float z){return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    };

    private static Map<String, Bone> parts(Mob mob) {
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) return Map.of();
        var model = living.getModel();
        if (model instanceof net.minecraft.client.model.HierarchicalModel<?> hierarchical)
            return Map.of("root", (Bone) (Object) hierarchical.root());
        var found = new LinkedHashMap<String, ModelPart>();
        for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || (field.getType() != ModelPart.class && field.getType() != ModelPart[].class)) continue;
                try { field.setAccessible(true); var value = field.get(model);
                    if (value instanceof ModelPart part) found.putIfAbsent(field.getName(), part);
                    else if (value instanceof ModelPart[] array)
                        for (int i = 0; i < array.length; i++) if (array[i] != null) found.putIfAbsent(field.getName()+"_"+i, array[i]);
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        }
        // Keep only root parts so locators do not visit a nested bone twice.
        Set<ModelPart> nested = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var part : found.values()) part.getAllParts().filter(child -> child != part).forEach(nested::add);
        var result = new LinkedHashMap<String, Bone>();
        found.forEach((name, part) -> { if (!nested.contains(part)) result.put(name, (Bone) (Object) part); });
        return Collections.unmodifiableMap(result);
    }
}
