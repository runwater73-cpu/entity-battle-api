package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.entitybattle.client.EntityPokemonNativeVisuals;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Xaero's icon renderer/cache sees the native type and variant, while radar tracks the real Pokemon. */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.radar.icon.RadarIconManager", remap = false)
public abstract class XaeroNativeIconMixin {
    @ModifyVariable(method = "get(Lnet/minecraft/world/entity/Entity;FZZLnet/minecraft/client/gui/GuiGraphics;Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lxaero/common/icon/XaeroIcon;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Entity entitybattle$sourceIcon(Entity entity) {
        if (entity instanceof PokemonEntity pokemon) {
            var nativeModel = EntityPokemonNativeVisuals.modelFor(pokemon.getPokemon());
            if (nativeModel != null) {
                dev.entitybattle.client.TwilightBossPoses.preparePortrait(nativeModel);
                return nativeModel;
            }
        }
        return entity;
    }
}
