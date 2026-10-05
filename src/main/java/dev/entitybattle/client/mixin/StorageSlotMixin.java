package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.gui.pc.StorageSlot;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.entitybattle.client.EntityBattlePortraits;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StorageSlot.class, remap = false)
public abstract class StorageSlotMixin {
    @Shadow public abstract Pokemon getPokemon();

    @Inject(method = "renderSlot", at = @At("HEAD"), remap = false)
    private void entitybattle$beginPcSlot(GuiGraphics graphics, int x, int y, float partialTick,
                                          CallbackInfo callback) {
        EntityBattlePortraits.beginPcSlot(graphics);
    }

    @Inject(method = "renderSlot", at = @At("RETURN"), remap = false)
    private void entitybattle$endPcSlot(GuiGraphics graphics, int x, int y, float partialTick,
                                        CallbackInfo callback) {
        EntityBattlePortraits.endPcSlot();
    }

    @WrapOperation(method = "renderSlot", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/client/gui/PokemonGuiUtilsKt;drawProfilePokemon$default(Lcom/cobblemon/mod/common/pokemon/RenderablePokemon;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Quaternionf;Lcom/cobblemon/mod/common/entity/PoseType;Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;FFLcom/cobblemon/mod/common/client/gui/ProfileTransformType;ZFFFFFFIILjava/lang/Object;)V"),
            remap = false)
    private void entitybattle$drawPcSlot(RenderablePokemon renderable, PoseStack pose,
                                          Quaternionf rotation, PoseType poseType, PosableState state,
                                          float delta, float scale, ProfileTransformType transform,
                                          boolean shiny, float red, float green, float blue,
                                          float alpha, float headYaw, float headPitch,
                                          int blockLight, int mask, Object marker,
                                          Operation<Void> original) {
        if (EntityBattlePortraits.drawPcSlot(getPokemon())) return;
        original.call(renderable, pose, rotation, poseType, state, delta, scale,
                transform, shiny, red, green, blue, alpha, headYaw, headPitch,
                blockLight, mask, marker);
    }
}
