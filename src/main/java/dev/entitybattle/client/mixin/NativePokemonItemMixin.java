package dev.entitybattle.client.mixin;

import com.cobblemon.mod.common.client.render.item.PokemonItemRenderer;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.entitybattle.client.EntityBattleModelRepository;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Item rendering has no GUI profile's 24-unit translation. Keep the registered Bone centered. */
@Mixin(value=PokemonItemRenderer.class,remap=false)
public abstract class NativePokemonItemMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",ordinal=1))
    private void entitybattle$guiCenter(PoseStack stack,float x,float y,float z,Operation<Void> original,
            ItemStack item,ItemDisplayContext mode,@Local PosableModel model) {
        original.call(stack,x,y,z);
        if(mode!=ItemDisplayContext.GUI||!EntityBattleModelRepository.isNative(model))return;
        var placement=PokemonItemRenderer.Companion.getPositions().get(mode);
        double outerY=((Number)placement.getScale().getY()).doubleValue();
        double translatedY=((Number)placement.getTranslation().getY()).doubleValue();
        stack.translate(0,(.5-outerY*translatedY)/(outerY*y),0);
    }
    @WrapOperation(method="render$lambda$0",at=@At(value="INVOKE",target="Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableModel;render(Lcom/cobblemon/mod/common/client/render/models/blockbench/repository/RenderContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private static void entitybattle$itemOrigin(PosableModel model,RenderContext context,PoseStack stack,
            VertexConsumer consumer,int light,int overlay,int color,Operation<Void> original) {
        if(!EntityBattleModelRepository.isNative(model)){original.call(model,context,stack,consumer,light,overlay,color);return;}
        stack.pushPose();
        try {stack.translate(0,1.5,0);original.call(model,context,stack,consumer,light,overlay,color);}
        finally {stack.popPose();}
    }
}
