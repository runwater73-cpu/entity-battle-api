package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.entitybattle.client.EntityBattlePortraits;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleOverlay.class, remap = false)
public abstract class BattleOverlayMixin {
    @Inject(method = "drawTile", at = @At("HEAD"), remap = false)
    private void entitybattle$beginTile(GuiGraphics graphics, float delta,
                                        ActiveClientBattlePokemon active, boolean left,
                                        int slot, PokedexEntryProgress dexState,
                                        boolean selected, boolean visible, boolean compact,
                                        CallbackInfo callback) {
        EntityBattlePortraits.beginTile(graphics, active);
    }

    @Inject(method = "drawTile", at = @At("RETURN"), remap = false)
    private void entitybattle$endTile(GuiGraphics graphics, float delta,
                                      ActiveClientBattlePokemon active, boolean left,
                                      int slot, PokedexEntryProgress dexState,
                                      boolean selected, boolean visible, boolean compact,
                                      CallbackInfo callback) {
        EntityBattlePortraits.endTile();
    }

    @Redirect(method = "drawBattleTile", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/gui/GuiUtilsKt;drawPosablePortrait$default(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;FFZLcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;FFFFFFZFFFFILjava/lang/Object;)V"),
            remap = false)
    private void entitybattle$drawPortrait(ResourceLocation species, PoseStack pose,
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
