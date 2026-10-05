package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.entitybattle.client.EntityBattlePortraits;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = PartyOverlay.class, remap = false)
public abstract class PartyOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void entitybattle$beginPartyRender(net.minecraft.client.gui.GuiGraphics graphics,
                                                net.minecraft.client.DeltaTracker delta,
                                                CallbackInfo callback) {
        EntityBattlePortraits.beginParty(graphics);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void entitybattle$endPartyRender(net.minecraft.client.gui.GuiGraphics graphics,
                                              net.minecraft.client.DeltaTracker delta,
                                              CallbackInfo callback) {
        EntityBattlePortraits.endParty();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;getAspects()Ljava/util/Set;"),
            remap = false)
    private Set<String> entitybattle$rememberPartyPokemon(Pokemon pokemon) {
        EntityBattlePortraits.beginPartyPokemon(pokemon);
        return pokemon.getAspects();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/gui/GuiUtilsKt;drawPosablePortrait$default(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;FFZLcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;FFFFFFZFFFFILjava/lang/Object;)V"),
            remap = false)
    private void entitybattle$drawPartyPortrait(ResourceLocation species,
                                                com.mojang.blaze3d.vertex.PoseStack pose,
                                                float x, float y, boolean flipped, PosableState state,
                                                float scale, float offsetX, float offsetY,
                                                float rotationX, float rotationY, float rotationZ,
                                                boolean shiny, float red, float green, float blue,
                                                float alpha, int mask, Object marker) {
        EntityBattlePortraits.draw(species, pose, x, y, flipped, state, scale,
                offsetX, offsetY, rotationX, rotationY, rotationZ, shiny,
                red, green, blue, alpha, mask, marker);
    }
}
