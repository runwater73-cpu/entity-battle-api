package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.entitybattle.client.EntityBattlePortraits;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PCGUI.class, remap = false)
public abstract class PCGUIMixin {
    @Shadow public abstract Pokemon getPreviewPokemon$common();

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/ModelWidget;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
            remap = false)
    private void entitybattle$drawPcPreview(ModelWidget widget, GuiGraphics graphics,
                                             int mouseX, int mouseY, float partialTick) {
        if (EntityBattlePortraits.drawPcPreview(graphics, getPreviewPokemon$common(), widget)) return;
        widget.render(graphics, mouseX, mouseY, partialTick);
    }
}
