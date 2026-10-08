package dev.entitybattle.client;

import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.joml.Quaternionf;

/** Shared native rendering at Cobblemon's two GUI model entry points. */
public final class EntityBattlePortraits {
    private EntityBattlePortraits() {}
    public static boolean drawProfile(ResourceLocation species, PoseStack pose, Quaternionf rotation,
            PosableState state, float delta, float scale, ProfileTransformType transform,
            boolean baseScale, float red, float green, float blue, float alpha, int light) {
        Mob model = EntityPokemonNativeVisuals.modelFor(species, state);
        if (model == null) return false;
        if (scale <= 0F || alpha <= 0F) return true;
        // Respect each caller's pose and scissor instead of locating a particular screen.
        var client = Minecraft.getInstance();
        var dispatcher = client.getEntityRenderDispatcher();
        var buffers = client.renderBuffers().bufferSource();
        Quaternionf previousCamera = new Quaternionf(dispatcher.cameraOrientation());
        float[] previousColor = RenderSystem.getShaderColor().clone();
        pose.pushPose();
        try {
            TwilightBossPoses.preparePortrait(model);
            EntityBattleNativeModels.prepare(model, null);
            var bounds = EntityBattleNativeModels.bounds(model);
            float extent = (float) Math.max(0.5, Math.max(bounds.getYsize(), Math.max(bounds.getXsize(), bounds.getZsize()) * 1.15));
            float fitted = transform == ProfileTransformType.NONE ? scale : scale * 1.8F / extent;
            buffers.endBatch();
            RenderSystem.applyModelViewMatrix();
            if (transform != ProfileTransformType.NONE) pose.translate(0, scale, 50);
            else pose.translate(0, 0, 50);
            pose.scale(fitted, -fitted, fitted);
            pose.mulPose(rotation);
            if (transform != ProfileTransformType.NONE) pose.translate(-bounds.getCenter().x, -bounds.getCenter().y, -bounds.getCenter().z);
            model.setYRot(0); model.setXRot(0);
            model.yBodyRot = model.yBodyRotO = model.yHeadRot = model.yHeadRotO = 0;
            Lighting.setupForEntityInInventory();
            RenderSystem.setShaderColor(red, green, blue, alpha);
            dispatcher.overrideCameraOrientation(new Quaternionf(rotation).conjugate());
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> EntityBattleNativeModels.render(model, 0, delta,
                    pose, buffers, LightTexture.pack(light, 15)));
            buffers.endBatch();
            return true;
        } catch (RuntimeException exception) {
            EntityPokemonNativeVisuals.reportFailure(model, exception);
            return false;
        } finally {
            dispatcher.overrideCameraOrientation(previousCamera);
            dispatcher.setRenderShadow(true);
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }

    public static boolean drawPortrait(ResourceLocation species, PoseStack pose, PosableState state,
            float scale, boolean reversed, float delta, float red, float green, float blue, float alpha) {
        float profileScale = Math.min(24F, scale * 1.4F) / 1.8F;
        pose.pushPose();
        try {
            pose.translate(0, 18F - profileScale, 0);
            return drawProfile(species, pose, new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(5), (float) Math.toRadians(reversed ? -32 : 32), 0),
                    state, delta, profileScale, ProfileTransformType.PROFILE,
                    false, red, green, blue, alpha, 13);
        } finally {
            pose.popPose();
        }
    }
}
