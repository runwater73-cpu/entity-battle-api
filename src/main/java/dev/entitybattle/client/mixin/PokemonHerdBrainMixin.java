package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.Collection;
import java.util.LinkedHashSet;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Horde Encounters reads these memories; Cobblemon 1.8.1 does not register them on PokemonEntity. */
@Mixin(value = PokemonEntity.class, remap = false)
public abstract class PokemonHerdBrainMixin {
    @ModifyArg(method = {"brainProvider", "assignNewBrainWithMemoriesAndSensors"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;provider(Ljava/util/Collection;Ljava/util/Collection;)Lnet/minecraft/world/entity/ai/Brain$Provider;"),
            index = 0, remap = false)
    private Collection<MemoryModuleType<?>> entitybattle$addHerdMemories(Collection<MemoryModuleType<?>> original) {
        if (!ModList.get().isLoaded("hordeencounters") || !ModList.get().isLoaded("asymmetricbattles")) return original;
        var memories = new LinkedHashSet<>(original);
        memories.add(CobblemonMemories.HERD_LEADER);
        memories.add(CobblemonMemories.HERD_SIZE);
        memories.add(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        return memories;
    }
}
