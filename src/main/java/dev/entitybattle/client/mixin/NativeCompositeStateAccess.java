package dev.entitybattle.client.mixin;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.CompositeState.class)
public interface NativeCompositeStateAccess {
    @Accessor("textureState") RenderStateShard.EmptyTextureStateShard entitybattle$textureState();
    @Accessor("shaderState") RenderStateShard.ShaderStateShard entitybattle$shader();
    @Accessor("transparencyState") RenderStateShard.TransparencyStateShard entitybattle$transparency();
    @Accessor("depthTestState") RenderStateShard.DepthTestStateShard entitybattle$depth();
    @Accessor("cullState") RenderStateShard.CullStateShard entitybattle$cull();
    @Accessor("lightmapState") RenderStateShard.LightmapStateShard entitybattle$lightmap();
    @Accessor("overlayState") RenderStateShard.OverlayStateShard entitybattle$overlay();
    @Accessor("layeringState") RenderStateShard.LayeringStateShard entitybattle$layering();
    @Accessor("outputState") RenderStateShard.OutputStateShard entitybattle$output();
    @Accessor("texturingState") RenderStateShard.TexturingStateShard entitybattle$texturing();
    @Accessor("writeMaskState") RenderStateShard.WriteMaskStateShard entitybattle$writeMask();
    @Accessor("lineState") RenderStateShard.LineStateShard entitybattle$line();
    @Accessor("colorLogicState") RenderStateShard.ColorLogicStateShard entitybattle$colorLogic();
}
