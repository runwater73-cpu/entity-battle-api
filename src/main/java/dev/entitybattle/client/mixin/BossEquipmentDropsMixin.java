package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonServerDelegate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonOrigin;
import dev.entitybattle.battle.EntityBossHordes;
import dev.entitybattle.battle.EntityKnightSquads;
import dev.entitybattle.battle.EntityBossEncounters;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps battle equipment equipped while suppressing only its automatic death drop. */
@Mixin(value = PokemonServerDelegate.class, remap = false)
public abstract class BossEquipmentDropsMixin {
    @Shadow public abstract PokemonEntity getEntity();

    @Inject(method = "doDeathDrops", at = @At("HEAD"), cancellable = true)
    private void entitybattle$noTemporaryLoot(CallbackInfo callback) {
        if (getEntity().getPokemon().getPersistentData().getBoolean(EntityBossHordes.SUMMONED_MINION)
                || EntityKnightSquads.isMember(getEntity().getPokemon())
                || EntityBossEncounters.awaitingSettlement(getEntity().getPokemon())) {
            callback.cancel();
        }
    }

    @Inject(method = "updatePostDeath", at = @At("HEAD"), cancellable = true)
    private void entitybattle$holdPendingBoss(CallbackInfo callback) {
        // Play the ordinary faint animation, retaining a recoverable entity until the encounter ends.
        if (getEntity().deathTime >= 59 && EntityBossEncounters.awaitingSettlement(getEntity().getPokemon())) {
            callback.cancel();
        }
    }

    @Redirect(method = "doDeathDrops", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;heldItemNoCopy$common()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack entitybattle$keepBossEquipment(Pokemon pokemon) {
        if (pokemon.isWild() && EntityPokemonOrigin.entityId(pokemon).map(EntityBattleProfiles::get)
                .filter(profile -> profile.boss() && profile.species().equals(
                        pokemon.getSpecies().getResourceIdentifier())).isPresent()) return ItemStack.EMPTY;
        return pokemon.heldItem();
    }
}
