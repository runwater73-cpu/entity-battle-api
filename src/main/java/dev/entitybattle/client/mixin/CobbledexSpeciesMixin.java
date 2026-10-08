package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.pokemon.Species;
import dev.entitybattle.compat.CobbledexSpecies;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional Cobbledex lookup fallback for its cobblemon-only getByName path. */
@Pseudo
@Mixin(targets = "com.cobbledex.PokemonItemCache", remap = false)
public abstract class CobbledexSpeciesMixin {
    @Inject(method = "resolveSpecies", at = @At("RETURN"), cancellable = true, require = 0)
    private void entitybattle$namespacedSpecies(String name, CallbackInfoReturnable<Species> callback) {
        if (callback.getReturnValue() != null) return;
        Species match = CobbledexSpecies.resolve(name);
        if (match != null) callback.setReturnValue(match);
    }
}
