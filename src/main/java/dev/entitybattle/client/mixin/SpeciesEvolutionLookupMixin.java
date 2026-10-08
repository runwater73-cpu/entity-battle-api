package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cobblemon 1.8.1's G-Max setter reads evolution result identifiers via getByName.
 * Preserve its ordinary name lookup; resolve explicit identifiers via the registry. */
@Mixin(value = PokemonSpecies.class, remap = false)
public abstract class SpeciesEvolutionLookupMixin {
    @Inject(method = "getByName", at = @At("HEAD"), cancellable = true)
    private static void entitybattle$evolutionIdentifier(String name, CallbackInfoReturnable<Species> callback) {
        if (name == null || !name.contains(":")) return;
        var id = ResourceLocation.tryParse(name);
        callback.setReturnValue(id == null ? null : PokemonSpecies.getByIdentifier(id));
    }
}
