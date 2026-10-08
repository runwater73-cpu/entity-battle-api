package dev.entitybattle.client;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import dev.entitybattle.client.mixin.NativeCompositeStateAccess;
import dev.entitybattle.client.mixin.NativeRenderTypeAccess;
import dev.entitybattle.client.mixin.NativeTextureStateAccess;
import java.util.*;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Optional texture extensions for additional native skins, with original material preserved. */
public final class EntityBattleModelTextures {
    public record Context(ResourceLocation species, PosableState state, Mob visual, ResourceLocation texture) {}
    @FunctionalInterface public interface Resolver { ResourceLocation resolve(Context context); }
    private static final Map<ResourceLocation, Resolver> RESOLVERS = new LinkedHashMap<>();
    private record Key(RenderType material, ResourceLocation texture) {}
    private static final Map<Key, RenderType> MATERIALS = new LinkedHashMap<>(128, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, RenderType> entry) { return size() > 512; }
    };
    private static boolean defaults;
    private EntityBattleModelTextures() {}

    public static void register(ResourceLocation id, Resolver resolver) { RESOLVERS.put(id, Objects.requireNonNull(resolver)); }
    public static void clear() { MATERIALS.clear(); dev.entitybattle.compat.TeamRocketModelTextures.clear(); }

    public static ResourceLocation resolve(Context context) {
        if (!defaults) { defaults = true; dev.entitybattle.compat.TeamRocketModelTextures.register(); }
        ResourceLocation texture = context.texture();
        for (var resolver : RESOLVERS.values()) {
            ResourceLocation resolved = resolver.resolve(new Context(context.species(), context.state(), context.visual(), texture));
            if (resolved != null) texture = resolved;
        }
        return texture;
    }

    public static Optional<ResourceLocation> texture(RenderType type) {
        if (type instanceof NativeRenderTypeAccess access
                && ((NativeCompositeStateAccess) (Object) access.entitybattle$state()).entitybattle$textureState()
                        instanceof NativeTextureStateAccess texture) return texture.entitybattle$texture();
        return Optional.empty();
    }

    public static RenderType withTexture(RenderType material, ResourceLocation texture) {
        if (texture(material).filter(texture::equals).isPresent() || !(material instanceof NativeRenderTypeAccess)) return material;
        return MATERIALS.computeIfAbsent(new Key(material, texture), key -> {
            if (material.isOutline()) return RenderType.outline(texture);
            var state = (NativeCompositeStateAccess) (Object) ((NativeRenderTypeAccess) material).entitybattle$state();
            // A multi-texture shader needs its own adapter; keep all of its original samplers.
            if (!(state.entitybattle$textureState() instanceof RenderStateShard.TextureStateShard)) return material;
            var replacement = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setShaderState(state.entitybattle$shader()).setTransparencyState(state.entitybattle$transparency())
                    .setDepthTestState(state.entitybattle$depth()).setCullState(state.entitybattle$cull())
                    .setLightmapState(state.entitybattle$lightmap()).setOverlayState(state.entitybattle$overlay())
                    .setLayeringState(state.entitybattle$layering()).setOutputState(state.entitybattle$output())
                    .setTexturingState(state.entitybattle$texturing()).setWriteMaskState(state.entitybattle$writeMask())
                    .setLineState(state.entitybattle$line()).setColorLogicState(state.entitybattle$colorLogic())
                    .createCompositeState(material.outline().isPresent());
            return RenderType.create("entitybattle_native_texture", material.format(), material.mode(), material.bufferSize(),
                    material.affectsCrumbling(), material.sortOnUpload(), replacement);
        });
    }
}
