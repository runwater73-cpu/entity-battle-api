package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import kotlin.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A fainted horde leader has no world entity; surviving members still anchor the encounter. */
@Pseudo
@Mixin(targets = "com.necro.asymmetric.battles.common.api.actor.HordeBattleActor", remap = false)
public abstract class HordeWorldPositionMixin {
    @Shadow private BattlePokemon leader;
    @Unique private Pair<ServerLevel, Vec3> entitybattle$lastPosition;

    @Inject(method = "<init>(Ljava/util/UUID;Lcom/cobblemon/mod/common/battles/pokemon/BattlePokemon;Ljava/util/List;FLcom/cobblemon/mod/common/api/battles/model/ai/BattleAI;)V", at = @At("RETURN"))
    private void entitybattle$rememberStart(CallbackInfo callback) {
        entitybattle$remember(leader.getEntity());
    }

    @Inject(method = "getWorldAndPosition", at = @At("HEAD"), cancellable = true)
    private void entitybattle$liveAnchor(CallbackInfoReturnable<Pair<ServerLevel, Vec3>> callback) {
        var owner = leader.getEffectedPokemon().getOwnerPlayer();
        if (owner != null) {
            entitybattle$lastPosition = new Pair<>(owner.serverLevel(), owner.position());
            callback.setReturnValue(entitybattle$lastPosition);
            return;
        }
        if (entitybattle$remember(leader.getEntity())) {
            callback.setReturnValue(entitybattle$lastPosition);
            return;
        }
        var members = ((BattleActor) (Object) this).getPokemonList();
        for (var member : members) {
            if (member.getEffectedPokemon().getCurrentHealth() > 0 && entitybattle$remember(member.getEntity())) {
                callback.setReturnValue(entitybattle$lastPosition);
                return;
            }
        }
        // Keep the final faint -> victory dispatch window from becoming a false flee.
        // If living members lost their entities, native flee handling must still abort.
        boolean allFainted = members.stream().allMatch(p -> p.getEffectedPokemon().getCurrentHealth() <= 0);
        callback.setReturnValue(allFainted ? entitybattle$lastPosition : null);
    }

    @Unique private boolean entitybattle$remember(PokemonEntity entity) {
        if (entity == null || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) return false;
        entitybattle$lastPosition = new Pair<>(level, entity.position());
        return true;
    }
}
