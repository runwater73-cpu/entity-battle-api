package dev.entitybattle.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.entitybattle.battle.EntityBossSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Mark the actual Sun Spirit summon, rather than deleting unrelated fire minions. */
@Pseudo
@Mixin(targets="com.aetherteam.aether.entity.monster.dungeon.boss.SunSpirit", remap=false)
public abstract class AetherMinionOwnerMixin {
    @WrapOperation(method="hurt", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"), require=0)
    private boolean entitybattle$owner(Level level, Entity minion, Operation<Boolean> original) {
        if (!level.isClientSide()) minion.getPersistentData().putUUID(EntityBossSources.OWNER, ((Mob)(Object)this).getUUID());
        return original.call(level, minion);
    }
}
