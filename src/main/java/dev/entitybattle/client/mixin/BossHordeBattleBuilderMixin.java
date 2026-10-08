package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.entitybattle.battle.EntityBossHordes;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BattleBuilder.class, priority = 1000, remap = false)
public abstract class BossHordeBattleBuilderMixin {
    // Injection order must precede Horde Encounters' cancellable HEAD hook.
    // Mixin priority alone does not guarantee callback execution order.
    @Inject(method = "pve*(Lnet/minecraft/server/level/ServerPlayer;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Ljava/util/UUID;Lcom/cobblemon/mod/common/battles/BattleFormat;ZZFLcom/cobblemon/mod/common/api/storage/party/PartyStore;)Lcom/cobblemon/mod/common/battles/BattleStartResult;",
            at = @At("HEAD"), order = 500, remap = false, cancellable = true)
    private void entitybattle$prepareBossHorde(ServerPlayer player, PokemonEntity target, UUID leadingPokemon,
                                               BattleFormat format, boolean cloneParties, boolean healFirst,
                                               float fleeDistance, PartyStore party,
                                               CallbackInfoReturnable<BattleStartResult> callback) {
        if (!EntityBossHordes.prepare(player, target)) {
            callback.setReturnValue(new com.cobblemon.mod.common.battles.ErroredBattleStart());
        }
    }
}
