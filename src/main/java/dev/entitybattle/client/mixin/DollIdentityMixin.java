package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import dev.entitybattle.compat.DollModelIds;
import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Encoded models remain normal Pokémon dolls for the optional mod's gameplay checks. */
@Pseudo
@Mixin(targets = "com.cobblemondoll.examplemod.doll.DollIdentity", remap = false)
public abstract class DollIdentityMixin {
    private static Method entitybattle$modelId;
    private static DollModelIds.Identity entitybattle$identity(ItemStack item) {
        try {
            if (entitybattle$modelId == null) entitybattle$modelId = Class.forName("com.cobblemondoll.examplemod.doll.DollIdentity")
                    .getMethod("modelIdOf", ItemStack.class);
            String id = (String) entitybattle$modelId.invoke(null, item);
            return id == null ? null : DollModelIds.decode(id);
        } catch (ReflectiveOperationException exception) { return null; }
    }
    @Inject(method = "speciesNameOf", at = @At("HEAD"), cancellable = true)
    private static void entitybattle$name(ItemStack item, CallbackInfoReturnable<String> callback) {
        var identity = entitybattle$identity(item);
        if (identity != null) callback.setReturnValue(identity.species());
    }
    @Inject(method = "speciesOf", at = @At("HEAD"), cancellable = true)
    private static void entitybattle$species(ItemStack item, CallbackInfoReturnable<Species> callback) {
        var identity = entitybattle$identity(item);
        if (identity != null) {
            var id = ResourceLocation.tryParse(identity.species());
            callback.setReturnValue(id == null ? null : PokemonSpecies.getByIdentifier(id));
        }
    }
}
