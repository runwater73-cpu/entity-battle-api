package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import dev.entitybattle.client.EntityBattleModelRepository;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VaryingModelRepository.class, remap = false)
public abstract class PokemonModelRepositoryMixin {
    @Inject(method = {"getPoser", "getTexture", "getTextureNoSubstitute", "getLayers"}, at = @At("HEAD"))
    private void entitybattle$register(ResourceLocation species, PosableState state, CallbackInfoReturnable<?> callback) {
        EntityBattleModelRepository.ensure(species);
    }
    @Inject(method = "getSprite", at = @At("HEAD"))
    private void entitybattle$registerSprite(ResourceLocation species, PosableState state,
            com.cobblemon.mod.common.client.render.SpriteType type, CallbackInfoReturnable<?> callback) {
        EntityBattleModelRepository.ensure(species);
    }
    @Inject(method = "reload", at = @At("HEAD"))
    private void entitybattle$reload(ResourceManager manager, CallbackInfo callback) {
        EntityBattleModelRepository.reloaded();
    }
}
