package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.dispatch.DispatchResult;
import com.cobblemon.mod.common.battles.interpreter.instructions.ActivateInstruction;
import dev.entitybattle.battle.TwilightBossMoveEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ActivateInstruction.class,remap=false)
public abstract class BossAbilityVisualMixin {
    @Inject(method="postActionEffect$lambda$0",at=@At("RETURN"))
    private static void entitybattle$ragePose(ActivateInstruction instruction,PokemonBattle battle,
                                             CallbackInfoReturnable<DispatchResult> callback) {
        var effect=instruction.getMessage().effectAt(1);
        if(effect==null)return;
        var pokemon=instruction.getMessage().battlePokemon(0,battle);
        if(pokemon!=null&&pokemon.getEntity()!=null){
            if(effect.getId().equals("entitybattlelamentrage"))TwilightBossMoveEffects.onRage(pokemon.getEntity());
            dev.entitybattle.battle.OtherBossMoveEffects.onAbility(pokemon.getEntity(),effect.getId());
        }
    }
}
