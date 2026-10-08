package dev.entitybattle.compat;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.ErroredBattleStart;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Adds a native Cobblemon trainer at Twilight Forest's existing final-castle marker. */
public final class TwilightLordTrainer {
    public static final ResourceLocation CLASS = ResourceLocation.fromNamespaceAndPath("entitybattle", "twilight_lord");
    public static final String CASTLE_MARKER = "final_castle_wip";
    private static final String NPC_UUID = "entitybattle:twilight_lord_uuid";
    private static final String ANCHOR_UUID = "entitybattle:twilight_lord_anchor";
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private record Pending(ServerLevel level, long due) {}
    private TwilightLordTrainer() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof NPCEntity npc
                && CLASS.equals(npc.getNpc().getId()) && npc.getParty() != null) {
            var sources = dev.entitybattle.api.EntityBattleProfiles.visualSpeciesMap();
            for (var pokemon : npc.getParty()) {
                ResourceLocation source = sources.get(pokemon.getSpecies().getResourceIdentifier());
                if (source != null) dev.entitybattle.api.EntityPokemonOrigin.setPresentation(pokemon, source, null);
            }
        }
        if (event.getEntity() instanceof Interaction marker && event.getLevel() instanceof ServerLevel level
                && level.dimension().location().equals(ResourceLocation.fromNamespaceAndPath("twilightforest", "twilight_forest"))) {
            // Chunk entity lists must finish loading before checking for a previously saved trainer.
            PENDING.put(marker.getUUID(), new Pending(level, level.getGameTime() + 40));
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        for (var entry : Map.copyOf(PENDING).entrySet()) {
            Pending pending = entry.getValue();
            if (pending.level().getServer() != event.getServer() || pending.level().getGameTime() < pending.due()) continue;
            PENDING.remove(entry.getKey());
            if (pending.level().getEntity(entry.getKey()) instanceof Interaction marker
                    && marker.getTags().contains(CASTLE_MARKER)) ensureTrainer(marker);
        }
    }

    /** The marker and trainer occupy the same chunk and retain their UUID association across saves. */
    public static NPCEntity ensureTrainer(Interaction marker) {
        if (!(marker.level() instanceof ServerLevel level) || marker.isRemoved()) return null;
        var data = marker.getPersistentData();
        if (data.hasUUID(NPC_UUID) && level.getEntity(data.getUUID(NPC_UUID)) instanceof NPCEntity saved
                && CLASS.equals(saved.getNpc().getId())) return saved;
        for (NPCEntity existing : level.getEntitiesOfClass(NPCEntity.class, new AABB(marker.blockPosition()).inflate(8), npc ->
                CLASS.equals(npc.getNpc().getId()) && npc.getPersistentData().hasUUID(ANCHOR_UUID)
                        && marker.getUUID().equals(npc.getPersistentData().getUUID(ANCHOR_UUID)))) {
            data.putUUID(NPC_UUID, existing.getUUID());
            return existing;
        }
        // A saved UUID may refer to an entity still being imported from the chunk. Never
        // replace it speculatively; admins can deliberately replace a removed trainer.
        if (data.hasUUID(NPC_UUID)) return null;
        var npcClass = NPCClasses.getByIdentifier(CLASS);
        if (npcClass == null) return null;
        NPCEntity npc = new NPCEntity(level);
        npc.setNpc(npcClass);
        npc.initialize(50);
        npc.setPos(marker.position());
        npc.setYRot(marker.getYRot());
        npc.setPersistenceRequired();
        npc.getPersistentData().putUUID(ANCHOR_UUID, marker.getUUID());
        if (!level.addFreshEntity(npc)) return null;
        data.putUUID(NPC_UUID, npc.getUUID());
        LogUtils.getLogger().info("Placed Twilight Lord trainer {} at final-castle marker {} in {}",
                npc.getUUID(), marker.blockPosition(), level.dimension().location());
        return npc;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        NPCEntity npc;
        if (event.getTarget() instanceof NPCEntity target && CLASS.equals(target.getNpc().getId())) npc = target;
        else if (event.getTarget() instanceof Interaction marker && marker.getTags().contains(CASTLE_MARKER)
                && marker.getPersistentData().hasUUID(NPC_UUID) && marker.level() instanceof ServerLevel level
                && level.getEntity(marker.getPersistentData().getUUID(NPC_UUID)) instanceof NPCEntity target
                && CLASS.equals(target.getNpc().getId())) npc = target;
        else return;
        if (event.getHand() != InteractionHand.MAIN_HAND) { event.setCanceled(true); return; }
        boolean editing = event.getEntity().isCreative() && event.getItemStack().is(CobblemonItems.NPC_EDITOR);
        if (editing && event.getTarget() == npc) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (event.getEntity() instanceof ServerPlayer player) {
            if (editing) npc.edit(player);
            else challenge(player, npc);
        }
    }

    /** Reuses official party validation, AI, animation, battle rules and cleanup. */
    public static com.cobblemon.mod.common.api.battles.model.PokemonBattle challenge(ServerPlayer player, NPCEntity npc) {
        if (!CLASS.equals(npc.getNpc().getId()) || !npc.isAlive() || player.isSpectator()
                || player.level() != npc.level() || player.distanceToSqr(npc) > 64) return null;
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null
                || npc.getBattleIds().stream().anyMatch(id -> BattleRegistry.getBattle(id) != null)) {
            player.displayClientMessage(Component.translatable("entitybattle.trainer.busy"), true);
            return null;
        }
        var result = BattleBuilder.INSTANCE.pvn(player, npc);
        if (result instanceof ErroredBattleStart error) error.sendTo(player, message -> message);
        return result instanceof SuccessfulBattleStart success ? success.getBattle() : null;
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) { PENDING.clear(); }
}
