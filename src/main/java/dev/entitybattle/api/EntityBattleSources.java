package dev.entitybattle.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Source-specific eligibility and lifecycle, usable without changing a source mod. */
public final class EntityBattleSources {
    private static final Map<ResourceLocation, EntityBattleSourceAdapter> ADAPTERS = new HashMap<>();
    private static final Map<ResourceLocation, java.util.function.Function<Mob, Mob>> PARTS = new HashMap<>();
    private EntityBattleSources() {}
    public static synchronized void register(ResourceLocation entity, EntityBattleSourceAdapter adapter) {
        ADAPTERS.put(Objects.requireNonNull(entity), Objects.requireNonNull(adapter));
    }
    public static synchronized EntityBattleSourceAdapter get(ResourceLocation entity) { return ADAPTERS.get(entity); }
    public static EntityBattleSourceAdapter get(Mob source) { return get(BuiltInRegistries.ENTITY_TYPE.getKey(source.getType())); }
    /** A separately spawned body redirects interaction to one battle source; it has no species. */
    public static synchronized void registerPart(ResourceLocation part, java.util.function.Function<Mob, Mob> controller) {
        PARTS.put(Objects.requireNonNull(part), Objects.requireNonNull(controller));
    }
    public static synchronized java.util.Set<ResourceLocation> partIds() { return java.util.Set.copyOf(PARTS.keySet()); }
    public static Mob resolve(Mob target) {
        var resolver = PARTS.get(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
        if (resolver == null) return target;
        try {
            Mob source = resolver.apply(target);
            return source != null && source.isAlive() && !source.isRemoved() && source.level() == target.level() ? source : null;
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Could not resolve native Boss part {}", target.getType(), failure);
            return null;
        }
    }
    public static Component denial(Mob source) {
        var adapter = get(source);
        if (adapter == null) return null;
        try { return adapter.denial(source); }
        catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Source eligibility failed for {}", source.getType(), failure);
            return Component.translatable("entitybattle.source.unavailable");
        }
    }
}
