package dev.entitybattle.battle;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.mojang.logging.LogUtils;
import dev.entitybattle.EntityBattleMod;
import dev.entitybattle.EntityBattleConfig;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonOrigin;
import dev.entitybattle.client.EntityBattleClientProfiles;
import dev.entitybattle.client.EntityPokemonNativeVisuals;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;

public final class EntityBattleNetwork {
    private static final float TARGET_TRACE_STEP = 0.05F;
    private static final Logger LOGGER = LogUtils.getLogger();
    private EntityBattleNetwork() {}

    public record Challenge(int entityId, UUID pokemonId) implements CustomPacketPayload {
        public static final Type<Challenge> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(EntityBattleMod.ID, "challenge"));
        public static final StreamCodec<FriendlyByteBuf, Challenge> CODEC = new StreamCodec<>() {
            @Override public Challenge decode(FriendlyByteBuf buffer) {
                return new Challenge(buffer.readVarInt(), buffer.readUUID());
            }

            @Override public void encode(FriendlyByteBuf buffer, Challenge packet) {
                buffer.writeVarInt(packet.entityId());
                buffer.writeUUID(packet.pokemonId());
            }
        };

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ProfileSync(Set<ResourceLocation> entityIds,
                              java.util.Map<ResourceLocation, ResourceLocation> visualSpecies,
                              boolean rChallengeEnabled)
            implements CustomPacketPayload {
        public static final Type<ProfileSync> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(EntityBattleMod.ID, "profile_sync"));
        public static final StreamCodec<FriendlyByteBuf, ProfileSync> CODEC = new StreamCodec<>() {
            @Override public ProfileSync decode(FriendlyByteBuf buffer) {
                int count = buffer.readVarInt();
                Set<ResourceLocation> ids = new java.util.HashSet<>();
                for (int i = 0; i < count; i++) ids.add(buffer.readResourceLocation());
                int visuals = buffer.readVarInt();
                java.util.Map<ResourceLocation, ResourceLocation> species = new java.util.HashMap<>();
                for (int i = 0; i < visuals; i++) {
                    species.put(buffer.readResourceLocation(), buffer.readResourceLocation());
                }
                return new ProfileSync(Set.copyOf(ids), java.util.Map.copyOf(species), buffer.readBoolean());
            }

            @Override public void encode(FriendlyByteBuf buffer, ProfileSync packet) {
                buffer.writeVarInt(packet.entityIds().size());
                packet.entityIds().forEach(buffer::writeResourceLocation);
                buffer.writeVarInt(packet.visualSpecies().size());
                packet.visualSpecies().forEach((species, entity) -> {
                    buffer.writeResourceLocation(species);
                    buffer.writeResourceLocation(entity);
                });
                buffer.writeBoolean(packet.rChallengeEnabled());
            }
        };

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record NativeVisual(UUID worldEntityId, ResourceLocation entityId,
                               CompoundTag appearance) implements CustomPacketPayload {
        public static final Type<NativeVisual> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(EntityBattleMod.ID, "native_visual"));
        public static final StreamCodec<FriendlyByteBuf, NativeVisual> CODEC = new StreamCodec<>() {
            @Override public NativeVisual decode(FriendlyByteBuf buffer) {
                UUID worldEntityId = buffer.readUUID();
                ResourceLocation entityId = buffer.readResourceLocation();
                CompoundTag appearance = buffer.readNbt();
                return new NativeVisual(worldEntityId, entityId,
                        appearance == null ? new CompoundTag() : appearance);
            }

            @Override public void encode(FriendlyByteBuf buffer, NativeVisual packet) {
                buffer.writeUUID(packet.worldEntityId());
                buffer.writeResourceLocation(packet.entityId());
                buffer.writeNbt(packet.appearance());
            }
        };

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record NativePose(UUID entityUuid, String move, int phase) implements CustomPacketPayload {
        public static final Type<NativePose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EntityBattleMod.ID,"native_pose"));
        public static final StreamCodec<FriendlyByteBuf,NativePose> CODEC = new StreamCodec<>() {
            @Override public NativePose decode(FriendlyByteBuf buffer) {return new NativePose(buffer.readUUID(),buffer.readUtf(64),buffer.readByte());}
            @Override public void encode(FriendlyByteBuf buffer,NativePose packet) {buffer.writeUUID(packet.entityUuid());buffer.writeUtf(packet.move(),64);buffer.writeByte(packet.phase());}
        };
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(Challenge.TYPE, Challenge.CODEC, (packet, context) ->
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()
                            || !EntityBattleConfig.ENABLE_R_CHALLENGE.get()) return;
                    Entity target = player.serverLevel().getEntity(packet.entityId());
                    if (!(target instanceof Mob mob)) return;
                    Mob source = dev.entitybattle.api.EntityBattleSources.resolve(mob);
                    if (source == null || EntityBattleProfiles.get(source.getType()) == null
                            || EntityBattleProfiles.worldMode(EntityBattleProfiles.get(source.getType()))
                                    != dev.entitybattle.api.EntityBattleProfile.WorldMode.NATIVE_MOB) return;
                    float range = Cobblemon.INSTANCE.getConfig().getBattleWildMaxDistance();
                    LivingEntity aimed = PlayerExtensionsKt.traceFirstEntityCollision(
                            player, range, TARGET_TRACE_STEP, LivingEntity.class, player, ClipContext.Fluid.NONE);
                    if (aimed != mob) return;
                    LOGGER.info("Entity battle challenge received: player={}, entity={}, pokemon={}",
                            player.getUUID(), mob.getUUID(), packet.pokemonId());
                    boolean started = EntityBattleSessions.start(player, packet.pokemonId(), source);
                    LOGGER.info("Entity battle challenge result: entity={}, started={}", mob.getUUID(), started);
                }))
                .playToClient(ProfileSync.TYPE, ProfileSync.CODEC, (packet, context) ->
                        context.enqueueWork(() -> EntityBattleClientProfiles.replace(
                                packet.entityIds(), packet.visualSpecies(), packet.rChallengeEnabled())))
                .playToClient(NativeVisual.TYPE, NativeVisual.CODEC, (packet, context) ->
                        context.enqueueWork(() -> EntityPokemonNativeVisuals.setSource(
                                packet.worldEntityId(), packet.entityId(), packet.appearance())))
                .playToClient(NativePose.TYPE, NativePose.CODEC, (packet, context) ->
                        context.enqueueWork(() -> dev.entitybattle.client.TwilightBossPoses.receive(packet.entityUuid(),packet.move(),packet.phase())));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof PokemonEntity pokemon)) return;
        EntityPokemonOrigin.entityId(pokemon.getPokemon()).ifPresent(id ->
                PacketDistributor.sendToPlayer(player, new NativeVisual(pokemon.getUUID(), id,
                        EntityPokemonOrigin.appearance(pokemon.getPokemon()).orElseGet(CompoundTag::new))));
        if(pokemon.getPersistentData().getBoolean(TwilightBossMoveEffects.RAGE))
            PacketDistributor.sendToPlayer(player,new NativePose(pokemon.getUUID(),"",1));
        int phase=pokemon.getPersistentData().getInt(OtherBossMoveEffects.PHASE);
        if(phase>0)PacketDistributor.sendToPlayer(player,new NativePose(pokemon.getUUID(),"",phase));
    }

    /** Evolution may retain the world entity while changing its native model. */
    public static void syncNativeVisual(PokemonEntity entity) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, new NativeVisual(entity.getUUID(),
                EntityPokemonOrigin.entityId(entity.getPokemon()).orElse(
                        ResourceLocation.fromNamespaceAndPath(EntityBattleMod.ID, "unbound")),
                EntityPokemonOrigin.appearance(entity.getPokemon()).orElseGet(CompoundTag::new)));
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ProfileSync packet = new ProfileSync(EntityBattleProfiles.nativeBattleEntityIds(),
                EntityBattleProfiles.visualSpeciesMap(),
                EntityBattleConfig.ENABLE_R_CHALLENGE.get());
        event.getRelevantPlayers().forEach(player -> {
            PacketDistributor.sendToPlayer(player, packet);
        });
    }
}
