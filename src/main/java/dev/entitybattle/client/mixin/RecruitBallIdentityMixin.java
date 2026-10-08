package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.entitybattle.compat.TeamRocketRecruitment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the API individual in the optional mod's existing full-NBT ball path. */
@Pseudo
@Mixin(targets = "com.jhnwudi666.teamrocket.recruit.RecruitBallItem", remap = false)
public abstract class RecruitBallIdentityMixin {
    @WrapOperation(method = "fillFromParty", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/storage/party/PlayerPartyStore;get(I)Lcom/cobblemon/mod/common/pokemon/Pokemon;"))
    private static Pokemon entitybattle$remember(PlayerPartyStore party, int slot, Operation<Pokemon> original,
            @Share("individual") LocalRef<CompoundTag> individual, @Local(argsOnly = true) ServerPlayer player) {
        Pokemon pokemon = original.call(party, slot);
        // Serialize before the optional mod removes the party member: encoding failure
        // then follows its existing failure path without losing the individual.
        if (pokemon != null && TeamRocketRecruitment.managed(pokemon))
            individual.set(pokemon.saveToNBT(player.registryAccess(), new CompoundTag()));
        return pokemon;
    }

    @WrapOperation(method = "fillFromParty", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resources/ResourceLocation;getPath()Ljava/lang/String;"))
    private static String entitybattle$identifier(ResourceLocation id, Operation<String> original) {
        return TeamRocketRecruitment.speciesKey(id);
    }

    @Inject(method = "fillFromParty", at = @At("RETURN"))
    private static void entitybattle$saveIndividual(ServerPlayer player, ItemStack ball, int slot,
            CallbackInfoReturnable<Boolean> callback, @Share("individual") LocalRef<CompoundTag> individual) {
        CompoundTag encoded = individual.get();
        if (!callback.getReturnValueZ() || encoded == null) return;
        CompoundTag data = ball.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.put("pokemon_nbt", encoded);
        ball.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    @WrapOperation(method = {"buildPokemon", "buildFusedPokemon"}, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/pokemon/PokemonProperties;setSpecies(Ljava/lang/String;)V"))
    private static void entitybattle$oldBallSpecies(PokemonProperties properties, String species, Operation<Void> original) {
        original.call(properties, TeamRocketRecruitment.canonicalSpecies(species));
    }

    @WrapOperation(method = {"buildPokemon", "buildFusedPokemon"}, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/pokemon/PokemonProperties;create(Lnet/minecraft/server/level/ServerPlayer;)Lcom/cobblemon/mod/common/pokemon/Pokemon;"))
    private static Pokemon entitybattle$create(PokemonProperties properties, ServerPlayer player, Operation<Pokemon> original) {
        String name = properties.getSpecies();
        var id = name == null ? null : ResourceLocation.tryParse(name);
        // A removed API source must fail without consuming the ball or silently drawing a random species.
        if (id != null && id.getNamespace().equals("entitybattle")) {
            var species = PokemonSpecies.getByIdentifier(id);
            if (species == null || !species.getImplemented()) return null;
        }
        Pokemon pokemon = original.call(properties, player);
        TeamRocketRecruitment.presentation(pokemon);
        return pokemon;
    }
}
