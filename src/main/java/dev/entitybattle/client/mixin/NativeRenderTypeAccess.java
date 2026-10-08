package dev.entitybattle.client.mixin;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface NativeRenderTypeAccess {
    @Accessor("state") RenderType.CompositeState entitybattle$state();
}
