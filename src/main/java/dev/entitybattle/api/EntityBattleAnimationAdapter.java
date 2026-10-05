package dev.entitybattle.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/** Optional presentation hook for mods with their own animation system. */
public interface EntityBattleAnimationAdapter {
    EntityBattleAnimationAdapter DEFAULT = new EntityBattleAnimationAdapter() {};

    default void onMove(LivingEntity entity) {
        entity.swing(InteractionHand.MAIN_HAND, true);
    }

    /** The ID is Cobblemon's Showdown move ID, for example "scratch". */
    default void onMove(LivingEntity entity, String moveId) {
        onMove(entity);
    }

    default void onHurt(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level) {
            level.broadcastEntityEvent(entity, (byte) 2);
        }
    }
}
