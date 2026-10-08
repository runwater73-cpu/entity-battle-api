package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import dev.entitybattle.api.EntityBattleSupportMoves;
import dev.entitybattle.compat.HordeAITargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {RandomBattleAI.class, StrongBattleAI.class}, remap = false)
public abstract class NativeSupportTargetMixin {
    @Inject(method = "choose", at = @At("RETURN"), cancellable = true, remap = false)
    private void entitybattle$supportTarget(ActiveBattlePokemon active, PokemonBattle battle,
            BattleSide side, ShowdownMoveset moveset, boolean switching,
            CallbackInfoReturnable<ShowdownActionResponse> callback) {
        var selected = HordeAITargets.correct(active, battle, moveset, switching, callback.getReturnValue());
        callback.setReturnValue(EntityBattleSupportMoves.correct((BattleAI)(Object)this,
                active, battle, side, moveset, switching, selected));
    }
}
