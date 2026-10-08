package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.pokemon.Species;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.entitybattle.compat.CobbledexSpecies;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Static viewer icons still capture the model through Cobblemon's public GUI draw. */
@Pseudo
@Mixin(targets = "com.cobbledex.IconCapture", remap = false)
public abstract class CobbledexIconMixin {
    @WrapOperation(method = "captureSpeciesToPng", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/pokemon/PokemonSpecies;getByName(Ljava/lang/String;)Lcom/cobblemon/mod/common/pokemon/Species;"))
    private Species entitybattle$iconSpecies(String name, Operation<Species> original) {
        Species species = original.call(name);
        return species != null ? species : CobbledexSpecies.resolve(name);
    }
}
