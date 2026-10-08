package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.entitybattle.client.EntityBattleModelRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PosableModel.class, remap = false)
public abstract class NativePosableRenderMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void entitybattle$begin(RenderContext context, PoseStack stack, VertexConsumer consumer,
            int light, int overlay, int color, CallbackInfo callback) {
        EntityBattleModelRepository.beginRender((PosableModel) (Object) this);
    }
}
