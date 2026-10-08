package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.PoseType;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.entitybattle.client.EntityBattlePortraits;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** One native-model path for medicine, pasture, storage, trade, summary, TM and dex. */
@Mixin(value = PokemonGuiUtilsKt.class, remap = false)
public abstract class PokemonGuiModelMixin {
    @WrapOperation(method = "drawProfilePokemon(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Quaternionf;Lcom/cobblemon/mod/common/entity/PoseType;Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;FFLcom/cobblemon/mod/common/client/gui/ProfileTransformType;ZZFFFFFFI)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private static void entitybattle$uniformDepth(PoseStack stack, float x, float y, float z, Operation<Void> original,
            ResourceLocation species, PoseStack guiStack, Quaternionf rotation, PoseType pose, PosableState state) {
        dev.entitybattle.client.EntityBattleModelRepository.guiScale(species, state, stack, x, y, z, original);
    }
    @Inject(method = "drawProfilePokemon(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Quaternionf;Lcom/cobblemon/mod/common/entity/PoseType;Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;FFLcom/cobblemon/mod/common/client/gui/ProfileTransformType;ZZFFFFFFI)V",
            at = @At("HEAD"), cancellable = true)
    private static void entitybattle$renderNative(ResourceLocation species, PoseStack pose,
            Quaternionf rotation, PoseType poseType, PosableState state, float delta,
            float scale, ProfileTransformType transform, boolean baseScale, boolean quirks,
            float red, float green, float blue, float alpha, float headYaw, float headPitch,
            int light, CallbackInfo callback) {
        if (dev.entitybattle.client.EntityBattleModelRepository.usesRepository(species, state)) return;
        if (EntityBattlePortraits.drawProfile(species, pose, rotation, state, delta, scale,
                transform, baseScale, red, green, blue, alpha, light)) callback.cancel();
    }
}
