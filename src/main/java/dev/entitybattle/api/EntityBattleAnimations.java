package dev.entitybattle.api;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class EntityBattleAnimations {
    private static final Map<EntityType<?>, EntityBattleAnimationAdapter> ADAPTERS = new IdentityHashMap<>();
    private static final Map<EntityType<?>, Map<String, Consumer<LivingEntity>>> MOVE_ACTIONS = new IdentityHashMap<>();

    private EntityBattleAnimations() {}

    public static synchronized void register(EntityType<?> type, EntityBattleAnimationAdapter adapter) {
        ADAPTERS.put(Objects.requireNonNull(type), Objects.requireNonNull(adapter));
    }

    /** Binds a move ID by source entity type. The callback receives the live entity:
     * a PokemonEntity after conversion, or the Mob before conversion. */
    public static synchronized void registerMove(EntityType<?> type, String moveId, Consumer<LivingEntity> action) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(action);
        String id = Objects.requireNonNull(moveId).toLowerCase(Locale.ROOT);
        if (id.isBlank()) throw new IllegalArgumentException("Move ID cannot be blank");
        MOVE_ACTIONS.computeIfAbsent(type, ignored -> new HashMap<>()).put(id, action);
    }

    public static synchronized EntityBattleAnimationAdapter get(EntityType<?> type) {
        return ADAPTERS.getOrDefault(type, EntityBattleAnimationAdapter.DEFAULT);
    }

    public static void playMove(LivingEntity entity, String moveId) {
        String id = moveId.toLowerCase(Locale.ROOT);
        EntityType<?> sourceType = entity instanceof PokemonEntity pokemon
                ? EntityPokemonOrigin.entityType(pokemon.getPokemon()).orElse(entity.getType())
                : entity.getType();
        Consumer<LivingEntity> action;
        EntityBattleAnimationAdapter adapter;
        synchronized (EntityBattleAnimations.class) {
            action = MOVE_ACTIONS.getOrDefault(sourceType, Map.of()).get(id);
            adapter = ADAPTERS.getOrDefault(sourceType, EntityBattleAnimationAdapter.DEFAULT);
        }
        if (action != null) action.accept(entity);
        else adapter.onMove(entity, id);
        if (entity instanceof PokemonEntity pokemon && !pokemon.level().isClientSide())
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(pokemon,
                    new dev.entitybattle.battle.EntityBattleNetwork.NativePose(pokemon.getUUID(), id, -1));
    }
}
