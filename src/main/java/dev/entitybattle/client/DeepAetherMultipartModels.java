package dev.entitybattle.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.entitybattle.compat.DeepAetherBossSource;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Complete native body in a coiled display pose; no entities or attack AI enter the level. */
final class DeepAetherMultipartModels implements EntityBattleNativeModels.Adapter {
    private record Access(Method head, Method mouth) {}
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            try { return new Access(type.getMethod("setControllingSegment", boolean.class), type.getMethod("setMouthOpen", boolean.class)); }
            catch (NoSuchMethodException missing) { throw new IllegalStateException("Unsupported Deep Aether segment visuals", missing); }
        }
    };
    private final Map<Mob, Entity[]> bodies = new WeakHashMap<>();
    static void register() { EntityBattleNativeModels.register(DeepAetherBossSource.CONTROLLER, new DeepAetherMultipartModels()); }
    @Override public boolean rendersParent() { return false; }
    @Override public Entity[] parts(Mob visual) { return bodies.get(visual); }
    @Override public void initialize(Mob visual, boolean gui) {
        Entity[] body = new Entity[22];
        var type = BuiltInRegistries.ENTITY_TYPE.get(DeepAetherBossSource.SEGMENT);
        for (int i = 0; i < body.length; i++) {
            if (!(type.create(visual.level()) instanceof Mob segment)) throw new IllegalStateException("Missing Deep Aether body entity");
            segment.setNoAi(true);
            segment.setNoGravity(true);
            flag(ACCESS.get(segment.getClass()).head(), segment, i == 0);
            body[i] = segment;
        }
        bodies.put(visual, body);
        visual.noCulling = true;
    }
    @Override public void update(Mob visual, PokemonEntity pokemon) {
        Entity[] body = bodies.get(visual);
        if (body == null) return;
        String move = pokemon == null ? "" : EntityBattleNativeModels.moveFor(pokemon);
        boolean attacking = move.equals("hurricane") || move.equals("airslash") || move.equals("icywind");
        double age = visual.tickCount * .018;
        double sway = attacking ? .18 : .06;
        for (int i = 0; i < body.length; i++) {
            Mob part = (Mob) body[i];
            boolean fresh = part.tickCount == 0;
            part.xo = part.getX(); part.yo = part.getY(); part.zo = part.getZ();
            part.yRotO = part.getYRot(); part.xRotO = part.getXRot();
            double angle = i * .48 + Math.sin(age) * sway;
            double radius = 1.6 + i * .055;
            Vec3 offset = new Vec3(Math.sin(angle) * radius, 4.4 - i * .16 + Math.sin(age + i * .2) * sway,
                    Math.cos(angle) * radius).yRot((float) Math.toRadians(-visual.yBodyRot));
            part.setPos(visual.position().add(offset));
            float yaw = visual.yBodyRot + (float) Math.toDegrees(-angle) + 90;
            part.setYRot(yaw); part.setXRot(0);
            part.yBodyRot = part.yBodyRotO = part.yHeadRot = part.yHeadRotO = yaw;
            part.tickCount = visual.tickCount;
            part.hurtTime = visual.hurtTime;
            // Parent recall/faint scale already belongs to Cobblemon, never native segment AI.
            part.deathTime = 0;
            flag(ACCESS.get(part.getClass()).mouth(), part, i == 0 && attacking);
            if (fresh) { part.xo = part.getX(); part.yo = part.getY(); part.zo = part.getZ(); }
        }
    }
    private static void flag(Method method, Mob part, boolean value) {
        try { method.invoke(part, value); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Deep Aether visual flag failed", failure); }
    }
}
