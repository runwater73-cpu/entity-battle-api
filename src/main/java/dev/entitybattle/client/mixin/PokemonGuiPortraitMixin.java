package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.entitybattle.client.EntityBattlePortraits;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** HUD and dialogue portraits share Cobblemon's portrait renderer. */
@Mixin(value = GuiUtilsKt.class, remap = false)
public abstract class PokemonGuiPortraitMixin {
    @WrapOperation(method = "drawPosablePortrait(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;FFZLcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;FFFFFFZFFFF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private static void entitybattle$portraitDepth(PoseStack stack, float x, float y, float z, Operation<Void> original,
            ResourceLocation species, PoseStack guiStack, float scale, float contextScale, boolean reverse, PosableState state) {
        dev.entitybattle.client.EntityBattleModelRepository.guiScale(species, state, stack, x, y, z, original);
    }

    @WrapOperation(method = "drawProfile(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;FF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private static void entitybattle$profileDepth(PoseStack stack, float x, float y, float z, Operation<Void> original,
            ResourceLocation species, PoseStack guiStack, PosableState state) {
        dev.entitybattle.client.EntityBattleModelRepository.guiScale(species, state, stack, x, y, z, original);
    }
    @Inject(method = "drawPosablePortrait(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;FFZLcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;FFFFFFZFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private static void entitybattle$nativePortrait(ResourceLocation species, PoseStack pose,
            float scale, float contextScale, boolean reversed, PosableState state, float delta,
            float swing, float swingAmount, float age, float headYaw, float headPitch,
            boolean quirks, float red, float green, float blue, float alpha, CallbackInfo callback) {
        if (dev.entitybattle.client.EntityBattleModelRepository.usesRepository(species, state)) return;
        if (EntityBattlePortraits.drawPortrait(species, pose, state, scale, reversed,
                delta, red, green, blue, alpha)) callback.cancel();
    }
}
