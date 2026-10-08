package dev.entitybattle.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageSource;

/** Optional source lifecycle. Combat remains owned by Cobblemon. */
public interface EntityBattleSourceAdapter {
    /** Null allows conversion. Also gates automatic Boss replacement, preserving native story interaction. */
    default Component denial(Mob source) { return null; }
    /** Optional opt-out from automatic world conversion; independent of source eligibility. */
    default boolean manualWorld() { return false; }
    /** Called after an interrupted encounter restores the original, before native AI resumes. */
    default void reset(Mob source) {}
    /** Uses the real source's death callback; return false if the death was cancelled. */
    default boolean complete(Mob source, ServerPlayer winner) {
        return complete(source, winner.serverLevel().damageSources().playerAttack(winner));
    }
    /** Also preserves source-specific death for ordinary world damage. */
    default boolean complete(Mob source, DamageSource damage) {
        if (damage.getEntity() instanceof ServerPlayer player) source.setLastHurtByPlayer(player);
        source.setHealth(0);
        source.die(damage);
        return ((dev.entitybattle.client.mixin.LivingDeathStateAccess) source).entitybattle$isDead();
    }
}
