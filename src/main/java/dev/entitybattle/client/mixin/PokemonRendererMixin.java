package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.entitybattle.client.EntityPokemonNativeVisuals;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PokemonRenderer.class, remap = false)
public abstract class PokemonRendererMixin {
    @WrapOperation(method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/MobRenderer;render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            remap = false)
    private void entitybattle$renderNativeModel(PokemonRenderer renderer, LivingEntity entity,
                                                float yaw, float partialTick, PoseStack poseStack,
                                                MultiBufferSource buffers, int packedLight,
                                                Operation<Void> original) {
        if (entity instanceof PokemonEntity pokemon
                && EntityPokemonNativeVisuals.render(pokemon, yaw, partialTick, poseStack, buffers, packedLight)) return;
        original.call(renderer, entity, yaw, partialTick, poseStack, buffers, packedLight);
    }
}
