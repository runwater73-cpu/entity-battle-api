package dev.entitybattle.client.mixin;

import dev.entitybattle.battle.EntityKnightSquads;
import java.util.List;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The original death algorithm scans 64 blocks; confine only our restored squad to its own room. */
@Pseudo
@Mixin(targets="twilightforest.entity.boss.KnightPhantom",remap=false)
public abstract class KnightSettlementRosterMixin {
    @Inject(method="getNearbyKnights",at=@At("RETURN"),cancellable=true,remap=false)
    private void entitybattle$settleOwnSquad(CallbackInfoReturnable<List<Mob>> callback) {
        var self=(Mob)(Object)this;
        if(EntityKnightSquads.hasNativeSettlement(self))
            callback.setReturnValue(EntityKnightSquads.settlementRoster(self,callback.getReturnValue()));
    }
}
