package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline;
import com.cobblemon.mod.common.battles.dispatch.DispatchResult;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.entitybattle.api.EntityBattleAnimations;
import dev.entitybattle.api.EntityPokemonOrigin;
import dev.entitybattle.battle.LichMoveEffects;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MoveInstruction.class, remap = false)
public abstract class NativeMoveAnimationMixin {
    // invoke() only queues actions. Animate when the queued move actually executes,
    // alongside Cobblemon's move message, so successive moves keep their timing.
    @Inject(method = "invoke$lambda$1",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;broadcastChatMessage(Lnet/minecraft/network/chat/Component;)V", shift = At.Shift.AFTER),
            remap = false)
    private static void entitybattle$animateSourceMove(MoveInstruction instruction, PokemonBattle battle,
            BattlePokemon target, Effect optionalEffect, ActionEffectTimeline timeline,
            CallbackInfoReturnable<DispatchResult> callback) {
        BattlePokemon user = instruction.getUserPokemon();
        if (user == null || instruction.getMove() == null) return;
        PokemonEntity entity = user.getEntity();
        if (entity == null || !(entity.level() instanceof ServerLevel)
                || EntityPokemonOrigin.entityType(entity.getPokemon()).isEmpty()) return;
        EntityBattleAnimations.playMove(entity, instruction.getMove().getName());
        LichMoveEffects.onMove(entity, instruction.getMove().getName());
        dev.entitybattle.battle.TwilightBossMoveEffects.onMove(entity, instruction.getMove().getName());
        dev.entitybattle.battle.OtherBossMoveEffects.onMove(entity, instruction.getMove().getName());
    }
}
