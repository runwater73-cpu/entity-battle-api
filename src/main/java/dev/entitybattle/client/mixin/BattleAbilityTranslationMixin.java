package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import dev.entitybattle.battle.EntityBattleMessages;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep localization at the shared battle message boundary for every battle layout. */
@Mixin(value = PokemonBattle.class, remap = false)
public abstract class BattleAbilityTranslationMixin {
    @ModifyVariable(method = "broadcastChatMessage", at = @At("HEAD"), argsOnly = true)
    private Component entitybattle$translateAbility(Component message) {
        return EntityBattleMessages.translate((PokemonBattle) (Object) this, message);
    }
}
