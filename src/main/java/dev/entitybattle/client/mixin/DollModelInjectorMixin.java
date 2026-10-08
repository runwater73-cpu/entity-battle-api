package dev.entitybattle.client.mixin;

import dev.entitybattle.compat.DollModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.cobblemondoll.examplemod.client.DollModelInjector", remap = false)
public abstract class DollModelInjectorMixin {
    @Inject(method = "ensure", at = @At("HEAD"), cancellable = true)
    private static void entitybattle$runtime(String id, CallbackInfo callback) {
        if (DollModels.ensure(id)) callback.cancel();
    }
}
