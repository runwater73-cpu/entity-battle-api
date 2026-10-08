package dev.entitybattle.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.entitybattle.compat.DollModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;

@Pseudo
@Mixin(targets = "com.cobblemondoll.examplemod.client.PokemonNativeRenderer", remap = false)
public abstract class DollNativeRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void entitybattle$runtime(@Coerce Object key, PoseStack stack, MultiBufferSource buffers,
            int light, int overlay, Model model, VertexConsumer consumer, CallbackInfoReturnable<Boolean> callback) {
        if (DollModels.render(model, stack, buffers, light, overlay, -1)) callback.setReturnValue(true);
    }
}
