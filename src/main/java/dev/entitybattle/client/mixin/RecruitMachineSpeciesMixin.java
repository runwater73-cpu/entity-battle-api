package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.pokemon.Species;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.entitybattle.compat.TeamRocketRecruitment;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Use the machine's own random roll, rarity, price and timing with correct registry IDs. */
@Pseudo
@Mixin(targets = "com.jhnwudi666.teamrocket.recruit.RecruitBlockEntity", remap = false)
public abstract class RecruitMachineSpeciesMixin {
    @WrapOperation(method = "rollRecruit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resources/ResourceLocation;getPath()Ljava/lang/String;"))
    private String entitybattle$identifier(ResourceLocation id, Operation<String> original) {
        return TeamRocketRecruitment.speciesKey(id);
    }

    @ModifyReturnValue(method = {"biomeSpecies", "allAllowedSpecies"}, at = @At("RETURN"))
    private List<Species> entitybattle$eligibleSources(List<Species> original) {
        return TeamRocketRecruitment.recruitmentPool(original);
    }
}
