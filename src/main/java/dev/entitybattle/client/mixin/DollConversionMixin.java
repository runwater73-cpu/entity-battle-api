package dev.entitybattle.client.mixin;

import dev.entitybattle.compat.DollModelIds;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep visual aspects intact in the optional mod's own conversion flow. */
@Pseudo
@Mixin(targets = "com.cobblemondoll.examplemod.item.DefaultModelItem", remap = false)
public abstract class DollConversionMixin {
    @WrapOperation(method = "handleConvertRequest", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/pokemon/PokemonSpecies;getByName(Ljava/lang/String;)Lcom/cobblemon/mod/common/pokemon/Species;"))
    private static Species entitybattle$species(String name, Operation<Species> original) {
        if (name.contains(":")) {
            var id = ResourceLocation.tryParse(name);
            return id == null ? null : PokemonSpecies.getByIdentifier(id);
        }
        return original.call(name);
    }
    @Inject(method = "parseAspects", at = @At("HEAD"), cancellable = true)
    private static void entitybattle$aspects(String value, CallbackInfoReturnable<Set<String>> callback) {
        if (value == null) return;
        var aspects = new LinkedHashSet<String>();
        for (String aspect : value.split(",")) if (!aspect.isBlank()) aspects.add(aspect.trim());
        if (DollModelIds.containsNativeData(aspects)) callback.setReturnValue(aspects);
    }
    @Inject(method = "buildModelId", at = @At("HEAD"), cancellable = true)
    private static void entitybattle$identity(String species, boolean giant, Set<String> aspects,
            CallbackInfoReturnable<String> callback) {
        String encoded = DollModelIds.encode(species, giant, aspects);
        if (encoded != null) callback.setReturnValue(encoded);
    }
}
