package dev.entitybattle.client;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.entitybattle.EntityBattleMod;
import dev.entitybattle.battle.EntityBattleNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/** Extends Cobblemon's party-send key to eligible native mobs. */
@EventBusSubscriber(modid = EntityBattleMod.ID, value = Dist.CLIENT)
public final class EntityBattleTargetKey {
    private static final float TARGET_TRACE_STEP = 0.05F;
    private static final Logger LOGGER = LogUtils.getLogger();
    private EntityBattleTargetKey() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EntityBattleClientProfiles.clear();
        EntityPokemonNativeVisuals.clear();
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS
                || event.getKey() != PartySendBinding.INSTANCE.getKey().getValue()
                || !EntityBattleClientProfiles.rChallengeEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null || client.player.isSpectator()
                || CobblemonClient.INSTANCE.getBattle() != null
                || !PartySendBinding.INSTANCE.canAction()) return;
        int slot = CobblemonClient.INSTANCE.getStorage().getSelectedSlot();
        Pokemon pokemon = slot < 0 ? null : CobblemonClient.INSTANCE.getStorage().getParty().get(slot);
        if (pokemon == null || pokemon.isFainted()) return;
        float range = Cobblemon.INSTANCE.getConfig().getBattleWildMaxDistance();
        LivingEntity target = PlayerExtensionsKt.traceFirstEntityCollision(
                client.player, range, TARGET_TRACE_STEP, LivingEntity.class, client.player, ClipContext.Fluid.NONE);
        if (!(target instanceof Mob mob)
                || !EntityBattleClientProfiles.contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()))) return;
        LOGGER.info("Entity battle R target: entity={}, pokemon={}", mob.getUUID(), pokemon.getUuid());
        // Prevent Cobblemon's release handler from treating this same press as a normal send-out.
        PartySendBinding.INSTANCE.actioned();
        PacketDistributor.sendToServer(new EntityBattleNetwork.Challenge(mob.getId(), pokemon.getUuid()));
        LOGGER.info("Entity battle R challenge sent: entity={}", mob.getUUID());
    }
}
