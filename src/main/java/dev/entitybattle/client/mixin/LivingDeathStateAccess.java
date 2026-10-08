package dev.entitybattle.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Health zero alone does not prove that NeoForge accepted the native death callback. */
@Mixin(LivingEntity.class)
public interface LivingDeathStateAccess {
    @Accessor("dead") boolean entitybattle$isDead();
}
