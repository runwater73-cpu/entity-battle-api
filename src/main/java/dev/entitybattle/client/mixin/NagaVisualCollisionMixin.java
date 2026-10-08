package dev.entitybattle.client.mixin;

import dev.entitybattle.client.EntityBattleNativeModels;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native segment movement also calls collision logic; render-only parts cannot push or hurt. */
@Pseudo
@Mixin(targets = "twilightforest.entity.boss.NagaSegment", remap = false)
public abstract class NagaVisualCollisionMixin {
    @Inject(method = "collideWithOthers", at = @At("HEAD"), cancellable = true)
    private void entitybattle$visualOnly(CallbackInfo callback) {
        if (EntityBattleNativeModels.isVisual(((PartEntity<?>) (Object) this).getParent())) callback.cancel();
    }
}
