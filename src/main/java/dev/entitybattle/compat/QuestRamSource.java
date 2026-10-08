package dev.entitybattle.compat;

import dev.entitybattle.api.EntityBattleSourceAdapter;
import java.lang.reflect.Method;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

/** Keeps the original quest and AI intact until its real reward has been delivered. */
public final class QuestRamSource implements EntityBattleSourceAdapter {
    private static final ClassValue<Method> REWARDED = new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) {
            try { return type.getMethod("getRewarded"); }
            catch (NoSuchMethodException missing) { throw new IllegalStateException("Unsupported Quest Ram reward API", missing); }
        }
    };
    @Override public Component denial(Mob source) {
        try { return Boolean.TRUE.equals(REWARDED.get(source.getClass()).invoke(source)) ? null
                : Component.translatable("entitybattle.source.quest_ram_task"); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
