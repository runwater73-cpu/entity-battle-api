package dev.entitybattle.client.mixin;

import dev.entitybattle.compat.CobbledexSpecies;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The viewer's common label formatter assumes official species translation keys. */
@Pseudo
@Mixin(targets = "com.cobbledex.SpawnInfoKt", remap = false)
public abstract class CobbledexLabelsMixin {
    @Inject(method = "formatSpeciesName", at = @At("HEAD"), cancellable = true, require = 0)
    private static void entitybattle$label(String name, CallbackInfoReturnable<String> callback) {
        var species = CobbledexSpecies.resolve(name);
        if (species != null) callback.setReturnValue(species.getTranslatedName().getString());
    }
}
