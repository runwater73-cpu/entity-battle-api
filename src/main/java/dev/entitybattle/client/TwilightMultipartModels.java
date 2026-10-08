package dev.entitybattle.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Optional Twilight Forest 4.8 visual methods; no boss AI, attacks, or world entities. */
final class TwilightMultipartModels implements EntityBattleNativeModels.Adapter {
    private static final Map<Class<?>, Map<String, Method>> METHODS = new HashMap<>();
    private static final Map<Class<?>, Map<String, Field>> FIELDS = new HashMap<>();
    private final String kind;
    private TwilightMultipartModels(String kind) { this.kind = kind; }

    static void register() {
        for (String kind : new String[]{"naga", "hydra", "snow_queen", "minoshroom"})
            EntityBattleNativeModels.register(ResourceLocation.fromNamespaceAndPath("twilightforest", kind), new TwilightMultipartModels(kind));
    }

    @Override public void initialize(Mob model, boolean gui) {
        var parts = model.getParts();
        if (kind.equals("naga")) {
            // Constructor parts are invisible at (0,0,0). Activate without native spawn particles.
            for (int i = 0; i < parts.length; i++) {
                call(parts[i], "activate");
                Vec3 offset = gui ? new Vec3(Math.sin(i * 0.55) * 4, 0, 2 + i * 0.7)
                        : new Vec3(0, 0, (i + 1) * 2).yRot((float) Math.toRadians(-model.getYRot()));
                parts[i].setPos(model.position().add(offset));
                parts[i].setYRot(model.getYRot());
                previous(parts[i]);
            }
        } else if (kind.equals("hydra")) {
            // The parent renderer's inventory preview heads would duplicate the real parts.
            setField(model, "renderFakeHeads", false);
            // Only the original three active heads; additional heads stay hidden.
            var containers = (Object[]) field(model, "hc");
            for (int i = 0; i < containers.length; i++) {
                var head = (net.minecraft.world.entity.Entity) field(containers[i], "headEntity");
                call(head, i < 3 ? "activate" : "deactivate");
                for (var neck : (Object[]) call(containers[i], "getNeckArray")) {
                    call(neck, i < 3 ? "activate" : "deactivate");
                }
                if (i < 3) {
                    call(containers[i], "setHeadPosition");
                    call(containers[i], "setNeckPosition");
                    previous(head);
                    for (var neck : (Object[]) call(containers[i], "getNeckArray")) previous((net.minecraft.world.entity.Entity) neck);
                }
            }
        }
    }

    @Override public boolean isPartVisible(Mob model, net.minecraft.world.entity.Entity part) {
        if (kind.equals("naga")) return part.getBbWidth() > 0;
        if (kind.equals("hydra")) return !part.getClass().getSimpleName().equals("HydraSmallPart")
                && (Boolean) call(part, "isActive");
        return true;
    }

    @Override public boolean renderPart(Mob visual, net.minecraft.world.entity.Entity part, float yaw, float partialTick,
            com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        if (!kind.equals("snow_queen")) return false;
        // The source renderer uses a chunk material with world light/offset uniforms.
        // Reuse the same block model through the entity preview renderer instead.
        pose.pushPose();
        try {
            pose.translate(-.5, 0, -.5);
            net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState(), pose, buffers, light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        } finally { pose.popPose(); }
        return true;
    }

    @Override public void update(Mob model, PokemonEntity pokemon) {
        if (kind.equals("naga")) {
            var parts = model.getParts();
            float ratio = pokemon == null ? 1F : (float) pokemon.getPokemon().getCurrentHealth() / pokemon.getPokemon().getMaxHealth();
            int count = Mth.clamp((int) (ratio * 10 + (ratio > 0 ? 2 : 0)), 0, parts.length);
            for (int i = 0; i < parts.length; i++) call(parts[i], i < count ? "activate" : "deactivate");
            if (pokemon != null) call(model, "moveSegments");
        } else if (kind.equals("hydra")) {
            String move = pokemon == null ? "" : TwilightBossPoses.moveFor(pokemon.getUUID());
            var containers = (Object[]) field(model, "hc");
            for (int i = 0; i < 3; i++) {
                Object container = containers[i];
                String desired = move.equals("crunch") ? "BITE_BEGINNING"
                        : move.equals("fireblast") ? "MORTAR_BEGINNING"
                        : java.util.Set.of("flamethrower", "heatwave").contains(move) ? "FLAME_BEGINNING" : "IDLE";
                var head = (net.minecraft.world.entity.Entity) field(container, "headEntity");
                String current = ((Enum<?>) field(container, "currentState")).name();
                if (!current.startsWith(desired.split("_")[0])) {
                    enumCall(container, "setNextState", desired);
                    call(container, "endCurrentAction");
                }
                previous(head);
                for (var neck : (Object[]) call(container, "getNeckArray")) previous((net.minecraft.world.entity.Entity) neck);
                // These native methods only advance animation and position/mouth data.
                call(container, "advanceHeadState");
                call(container, "setHeadPosition");
                head.setYRot(model.getYRot());
                call(container, "setNeckPosition");
            }
        } else if (kind.equals("snow_queen")) {
            var parts = model.getParts();
            for (int i = 0; i < parts.length; i++) {
                previous(parts[i]);
                parts[i].setPos(i == parts.length - 1 ? model.position().add(0, -1, 0)
                        : (Vec3) call(model, "getIceShieldPosition", i));
                parts[i].setYRot((Float) call(model, "getIceShieldAngle", i));
            }
        } else if (kind.equals("minoshroom")) {
            float old = (Float) field(model, "clientSideChargeAnimation");
            setField(model, "prevClientSideChargeAnimation", old);
            boolean charging = (Boolean) call(model, "isGroundAttackCharge");
            setField(model, "clientSideChargeAnimation", Mth.clamp(old + (charging ? 0.6F : -1F), 0, 6));
        }
        if (model.getParts() != null) for (var part : model.getParts()) if (part != null) {
            part.tickCount = model.tickCount;
        }
    }

    private static void previous(net.minecraft.world.entity.Entity part) {
        part.xo = part.getX(); part.yo = part.getY(); part.zo = part.getZ();
        part.yRotO = part.getYRot(); part.xRotO = part.getXRot();
    }
    private static Method method(Object object, String name, Class<?>... parameters) {
        String key = name + java.util.Arrays.toString(parameters);
        return METHODS.computeIfAbsent(object.getClass(), ignored -> new HashMap<>()).computeIfAbsent(key, ignored -> {
            for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
                try { var m = type.getDeclaredMethod(name, parameters); m.setAccessible(true); return m; }
                catch (NoSuchMethodException missing) { /* Try inherited native method. */ }
            }
            throw new IllegalStateException("Missing native visual method " + object.getClass().getName() + "." + name);
        });
    }
    private static Object call(Object object, String name, Object... args) {
        Class<?>[] parameters = java.util.Arrays.stream(args).map(a -> a instanceof Integer ? int.class : a.getClass()).toArray(Class<?>[]::new);
        try { return method(object, name, parameters).invoke(object, args); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Native visual method failed: " + name, e); }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void enumCall(Object object, String name, String value) {
        Object state = field(object, "currentState");
        try { method(object, name, state.getClass()).invoke(object, Enum.valueOf((Class) state.getClass(), value)); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(name, e); }
    }
    private static Field findField(Object object, String name) {
        return FIELDS.computeIfAbsent(object.getClass(), ignored -> new HashMap<>()).computeIfAbsent(name, ignored -> {
            for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
                try { var f = type.getDeclaredField(name); f.setAccessible(true); return f; }
                catch (NoSuchFieldException missing) { /* Try inherited native field. */ }
            }
            throw new IllegalStateException("Missing native visual field " + name);
        });
    }
    private static Object field(Object object, String name) {
        try { return findField(object, name).get(object); }
        catch (IllegalAccessException e) { throw new IllegalStateException(name, e); }
    }
    private static void setField(Object object, String name, Object value) {
        try { findField(object, name).set(object, value); }
        catch (IllegalAccessException e) { throw new IllegalStateException(name, e); }
    }
}
