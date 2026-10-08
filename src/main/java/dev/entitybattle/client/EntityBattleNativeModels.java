package dev.entitybattle.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/** Client API for native animation state and parts, shared by world and GUI rendering. */
public final class EntityBattleNativeModels {
    private record State(long tick) {}
    private static final Map<ResourceLocation, Adapter> ADAPTERS = new HashMap<>();
    private static final Map<Mob, State> STATES = new WeakHashMap<>();
    private record Measurement(AABB bounds, ResourceLocation texture) {}
    private static final Map<Mob, Measurement> MEASUREMENTS = new WeakHashMap<>();
    private static boolean registered;

    private EntityBattleNativeModels() {}

    /** Register on the client thread. The supplied mob is never added to a level. */
    public static void register(ResourceLocation source, Adapter adapter) {
        ensureDefaults();
        ADAPTERS.put(source, java.util.Objects.requireNonNull(adapter));
    }

    private static void ensureDefaults() {
        if (registered) return;
        registered = true;
        TwilightMultipartModels.register();
        DeepAetherMultipartModels.register();
    }

    public static void prepare(Mob model, PokemonEntity pokemon) {
        ensureDefaults();
        long tick = pokemon == null ? model.level().getGameTime() : pokemon.tickCount;
        var state = STATES.get(model);
        var adapter = ADAPTERS.get(BuiltInRegistries.ENTITY_TYPE.getKey(model.getType()));
        // Mark before initialization: part collision hooks must recognize render-only parents.
        if (state == null) {
            STATES.put(model, new State(tick));
            if (adapter != null) adapter.initialize(model, pokemon == null);
        } else if (state.tick == tick) return;
        if (pokemon == null) model.tickCount = (int) tick;
        if (adapter != null) adapter.update(model, pokemon);
        STATES.put(model, new State(tick));
    }

    public static boolean isVisual(Entity entity) { return entity.level().isClientSide() && entity instanceof Mob mob && STATES.containsKey(mob); }
    /** Current synchronized move, or empty outside its visual window. */
    public static String moveFor(PokemonEntity pokemon) { return TwilightBossPoses.moveFor(pokemon.getUUID()); }
    public static void clear() { STATES.clear(); MEASUREMENTS.clear(); }

    public static boolean isPartVisible(Mob model, Entity part) {
        if (part == null || part.isRemoved() || part.isInvisible()) return false;
        var adapter = ADAPTERS.get(BuiltInRegistries.ENTITY_TYPE.getKey(model.getType()));
        return adapter == null || adapter.isPartVisible(model, part);
    }

    /** Supports native body entities that do not use NeoForge's PartEntity array. */
    public static Entity[] parts(Mob model) {
        var adapter = ADAPTERS.get(BuiltInRegistries.ENTITY_TYPE.getKey(model.getType()));
        return adapter == null ? model.getParts() : adapter.parts(model);
    }

    public static boolean rendersParent(Mob model) {
        var adapter = ADAPTERS.get(BuiltInRegistries.ENTITY_TYPE.getKey(model.getType()));
        return adapter == null || adapter.rendersParent();
    }

    /** Render the parent plus all visible native parts in the parent's coordinate system. */
    public static void render(Mob model, float yaw, float partialTick, PoseStack pose,
                              MultiBufferSource buffers, int light) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (rendersParent(model)) dispatcher.render(model, 0, 0, 0, yaw, partialTick, pose, buffers, light);
        renderParts(model, partialTick, pose, buffers, light);
    }

    private static void renderParts(Mob model, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        var parts = parts(model);
        if (parts == null) return;
        double x = Mth.lerp(partialTick, model.xo, model.getX());
        double y = Mth.lerp(partialTick, model.yo, model.getY());
        double z = Mth.lerp(partialTick, model.zo, model.getZ());
        for (var part : parts) {
            if (!isPartVisible(model, part)) continue;
            pose.pushPose();
            try {
                pose.translate(Mth.lerp(partialTick, part.xo, part.getX()) - x,
                        Mth.lerp(partialTick, part.yo, part.getY()) - y,
                        Mth.lerp(partialTick, part.zo, part.getZ()) - z);
                renderPart(model, part, Mth.rotLerp(partialTick, part.yRotO, part.getYRot()), partialTick, pose, buffers, light);
            } finally { pose.popPose(); }
        }
    }

    /** The caller has translated to the part. Optional adapters can replace world-only materials. */
    public static void renderPart(Mob model, Entity part, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light) {
        var adapter = ADAPTERS.get(BuiltInRegistries.ENTITY_TYPE.getKey(model.getType()));
        if (adapter != null && adapter.renderPart(model, part, yaw, partialTick, pose, buffers, light)) return;
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(part).render(part, yaw, partialTick, pose, buffers, light);
    }

    public static AABB bounds(Mob model) {
        AABB bounds = model.getBoundingBox();
        var parts = parts(model);
        if (parts != null) for (var part : parts)
            if (isPartVisible(model, part)) bounds = bounds.minmax(part.getBoundingBox());
        return bounds.move(-model.getX(), -model.getY(), -model.getZ());
    }

    /** Actual rendered geometry includes crowns, equipment and parts outside the collision box.
     * Measure once per display copy, at a neutral facing, without submitting GPU buffers. */
    public static AABB guiBounds(Mob model) {
        return measure(model).bounds();
    }

    /** Conditional renderers can report a texture even while drawing a delegated model.
     * Observe the actual material once, alongside the existing geometry measurement. */
    public static ResourceLocation texture(Mob model) { return measure(model).texture(); }

    private static Measurement measure(Mob model) {
        return MEASUREMENTS.computeIfAbsent(model, visual -> {
            var geometry = new GeometryBounds();
            var materials = new java.util.LinkedHashMap<ResourceLocation, Integer>();
            var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(visual);
            var reported = renderer.getTextureLocation(visual);
            float yaw = visual.getYRot(), pitch = visual.getXRot(), oldPitch = visual.xRotO;
            float body = visual.yBodyRot, oldBody = visual.yBodyRotO;
            float head = visual.yHeadRot, oldHead = visual.yHeadRotO;
            try {
                visual.setYRot(0); visual.setXRot(0); visual.xRotO = 0;
                visual.yBodyRot = visual.yBodyRotO = visual.yHeadRot = visual.yHeadRotO = 0;
                var stack = new PoseStack();
                MultiBufferSource buffers = type -> {
                    var texture = EntityBattleModelTextures.texture(type).orElse(null);
                    return new com.mojang.blaze3d.vertex.VertexConsumer() {
                        public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x,float y,float z) {
                            geometry.addVertex(x,y,z);
                            if (texture != null && isBodyTexture(texture)) materials.merge(texture, 1, Integer::sum);
                            return this;
                        }
                        public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r,int g,int b,int a) { return this; }
                        public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u,float v) { return this; }
                        public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u,int v) { return this; }
                        public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u,int v) { return this; }
                        public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x,float y,float z) { return this; }
                    };
                };
                // Geometry only: dispatcher shadows/fire/debug bounds are not part of this Bone.
                if (rendersParent(visual)) renderer.render(visual, 0, .5F, stack, buffers, 0xf000f0);
                renderParts(visual, .5F, stack, buffers, 0xf000f0);
                var selected = materials.containsKey(reported) ? reported : materials.entrySet().stream()
                        .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(reported);
                return new Measurement(geometry.result(bounds(visual)), selected);
            } catch (RuntimeException unsupportedPreview) {
                return new Measurement(bounds(visual), reported);
            } finally {
                visual.setYRot(yaw); visual.setXRot(pitch); visual.xRotO = oldPitch;
                visual.yBodyRot = body; visual.yBodyRotO = oldBody;
                visual.yHeadRot = head; visual.yHeadRotO = oldHead;
            }
        });
    }

    private static boolean isBodyTexture(ResourceLocation texture) {
        String path = texture.getPath();
        return path.startsWith("textures/") && !path.contains("/armor/")
                && !path.startsWith("textures/item/") && !path.startsWith("textures/block/")
                && !path.startsWith("textures/atlas/") && !path.startsWith("textures/misc/");
    }

    public static float guiExtent(Mob model) {
        var size = guiBounds(model);
        return (float) Math.max(.5, Math.max(size.getYsize(), Math.hypot(size.getXsize(), size.getZsize())) * 1.15);
    }

    private static final class GeometryBounds implements com.mojang.blaze3d.vertex.VertexConsumer {
        private double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        private double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        public GeometryBounds addVertex(float x, float y, float z) {
            if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)) {
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
            return this;
        }
        AABB result(AABB fallback) {
            return minX == Double.POSITIVE_INFINITY ? fallback : new AABB(minX,minY,minZ,maxX,maxY,maxZ);
        }
        public GeometryBounds setColor(int r,int g,int b,int a) { return this; }
        public GeometryBounds setUv(float u,float v) { return this; }
        public GeometryBounds setUv1(int u,int v) { return this; }
        public GeometryBounds setUv2(int u,int v) { return this; }
        public GeometryBounds setNormal(float x,float y,float z) { return this; }
    }

    public interface Adapter {
        default void initialize(Mob visual, boolean gui) {}
        default Entity[] parts(Mob visual) { return visual.getParts(); }
        default boolean rendersParent() { return true; }
        /** Some native parts inherit parent invisibility and have a separate activation flag. */
        default boolean isPartVisible(Mob visual, Entity part) { return true; }
        /** Return true after drawing a part whose native renderer requires unavailable world render state. */
        default boolean renderPart(Mob visual, Entity part, float yaw, float partialTick,
                PoseStack pose, MultiBufferSource buffers, int light) { return false; }
        /** At most once per game tick. Do not call Mob.tick(), attack, or add parts to the level. */
        void update(Mob visual, PokemonEntity pokemon);
    }
}
