package dev.entitybattle.client.mixin;

import dev.entitybattle.compat.DollModels;
import net.minecraft.client.model.Model;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopedoll.client.custom.CustomDollLoader", remap = false)
public abstract class DollLoaderMixin {
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private static void entitybattle$model(String id, CallbackInfoReturnable<Model> callback) {
        var model = DollModels.model(id); if (model != null) callback.setReturnValue(model);
    }
    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private static void entitybattle$texture(String id, CallbackInfoReturnable<ResourceLocation> callback) {
        var texture = DollModels.texture(id); if (texture != null) callback.setReturnValue(texture);
    }
    @Inject(method = "getLanguage", at = @At("HEAD"), cancellable = true)
    private static void entitybattle$name(String language, String id, CallbackInfoReturnable<String> callback) {
        var name = DollModels.name(id); if (name != null) callback.setReturnValue(name);
    }
}
