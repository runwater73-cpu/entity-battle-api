package dev.entitybattle.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Non-projectile source attacks expose their real owner for conversion cleanup. */
@Pseudo
@Mixin(targets = {
    "com.bmt.kaleidoscope_twilight.common.entity.GroundSpikeEntity",
    "com.bmt.kaleidoscope_twilight.common.entity.GiantSwordEntity",
    "com.bmt.kaleidoscope_twilight.common.entity.ThrownSwordEntity",
    "com.bmt.kaleidoscope_twilight.common.entity.EarthquakeEntity"
}, remap = false)
public abstract class SunflowerAttackOwnerAccess implements dev.entitybattle.compat.SourceOwnedAttack {
    @Override @Accessor("owner") public abstract LivingEntity entitybattle$owner();
}
