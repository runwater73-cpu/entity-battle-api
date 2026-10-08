package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.entitybattle.client.EntityBattleClientProfiles;
import dev.entitybattle.compat.DollModelIds;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.cobblemondoll.examplemod.client.SelectedPokemonCache", remap = false)
public abstract class DollSelectedPokemonMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private static void entitybattle$fullSpecies(CallbackInfo callback) {
        try {
            var client = net.minecraft.client.Minecraft.getInstance();
            var storage = CobblemonClient.INSTANCE.getStorage();
            int slot = storage.getSelectedSlot();
            if (slot < 0 || slot >= storage.getParty().getSlots().size()) return;
            Pokemon pokemon = storage.getParty().getSlots().get(slot);
            if (pokemon == null || pokemon.getSpecies() == null) return;
            ResourceLocation species = pokemon.getSpecies().getResourceIdentifier();
            if (EntityBattleClientProfiles.sourceForSpecies(species) == null
                    && !DollModelIds.containsNativeData(pokemon.getAspects())) return;
            var field = Class.forName("com.cobblemondoll.examplemod.client.SelectedPokemonCache")
                    .getDeclaredField("lastSelectedSpeciesId");
            field.setAccessible(true);
            field.set(null, species.toString());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The optional cache is allowed to keep its normal path-only identity.
        }
    }
}
