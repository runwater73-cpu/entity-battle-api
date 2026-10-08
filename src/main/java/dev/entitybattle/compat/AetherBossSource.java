package dev.entitybattle.compat;

import dev.entitybattle.api.EntityBattleSourceAdapter;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;

/** Converts only after native dialogue, medals, or awakening have unlocked the fight. */
public final class AetherBossSource implements EntityBattleSourceAdapter {
    private record Access(Method fighting, Method reset) {}
    private static final Map<Class<?>, Access> ACCESS = new ConcurrentHashMap<>();
    private static Access access(Mob source) {
        return ACCESS.computeIfAbsent(source.getClass(), type -> {
            try { return new Access(type.getMethod("isBossFight"), type.getMethod("reset")); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported Aether Boss API", failure); }
        });
    }
    @Override public Component denial(Mob source) {
        if (source.level().getDifficulty() == Difficulty.PEACEFUL)
            return Component.translatable("entitybattle.source.peaceful");
        try {
            return Boolean.TRUE.equals(access(source).fighting.invoke(source)) ? null
                    : Component.translatable("entitybattle.source.aether_challenge");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    @Override public void reset(Mob source) {
        try { access(source).reset.invoke(source); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
