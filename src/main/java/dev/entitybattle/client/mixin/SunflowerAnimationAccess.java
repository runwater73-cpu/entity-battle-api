package dev.entitybattle.client.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Animation clocks on the render-only source, never on a world Boss. */
@Pseudo
@Mixin(targets = "com.bmt.kaleidoscope_twilight.common.entity.boss.UmbralSunflower", remap = false)
public interface SunflowerAnimationAccess {
    @Accessor("DATA_SWORD_AURA_ANIM_TIME")
    static EntityDataAccessor<Integer> entitybattle$swordAuraTime() { throw new AssertionError(); }
    @Accessor("DATA_SWORD_ANIM_TIME")
    static EntityDataAccessor<Integer> entitybattle$swordTime() { throw new AssertionError(); }
    @Accessor("DATA_SHIELD_ANIM_TIME")
    static EntityDataAccessor<Integer> entitybattle$shieldTime() { throw new AssertionError(); }
    @Accessor("DATA_GROUND_SPIKE_ANIM_TIME")
    static EntityDataAccessor<Integer> entitybattle$spikeTime() { throw new AssertionError(); }
}
