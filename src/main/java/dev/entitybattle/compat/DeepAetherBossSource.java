package dev.entitybattle.compat;

import dev.entitybattle.api.EntityBattleSources;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** One native controller owns its whole body and the original brass-dungeon settlement. */
public final class DeepAetherBossSource {
    public static final ResourceLocation CONTROLLER = ResourceLocation.parse("deep_aether:eots_controller");
    public static final ResourceLocation SEGMENT = ResourceLocation.parse("deep_aether:eots_segment");
    private static final Map<Class<?>, Method> METHODS = new ConcurrentHashMap<>();
    private DeepAetherBossSource() {}
    public static void register() {
        EntityBattleSources.register(CONTROLLER, new AetherBossSource());
        EntityBattleSources.registerPart(SEGMENT, DeepAetherBossSource::controller);
    }
    private static Mob controller(Mob part) {
        try {
            var method = METHODS.computeIfAbsent(part.getClass(), type -> {
                try { return type.getMethod("getController"); }
                catch (NoSuchMethodException missing) { throw new IllegalStateException("Unsupported Deep Aether controller API", missing); }
            });
            return method.invoke(part) instanceof Mob source ? source : null;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Deep Aether body lookup failed", failure); }
    }
}
