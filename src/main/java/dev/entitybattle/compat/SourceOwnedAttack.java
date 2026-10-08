package dev.entitybattle.compat;

import net.minecraft.world.entity.LivingEntity;

/** Optional native transient attack ownership, safe to query without its source mod. */
public interface SourceOwnedAttack {
    LivingEntity entitybattle$owner();
}
