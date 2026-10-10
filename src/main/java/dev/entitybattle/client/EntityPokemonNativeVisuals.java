package dev.entitybattle.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.entitybattle.EntityBattleMod;
import dev.entitybattle.api.EntityPokemonOrigin;
import dev.entitybattle.api.EntityPokemonPresentation;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.slf4j.Logger;

/** Client-only presentation of a real PokemonEntity using its source mob renderer. */
@EventBusSubscriber(modid = EntityBattleMod.ID, value = Dist.CLIENT)
public final class EntityPokemonNativeVisuals {
    private static final Logger LOGGER = LogUtils.getLogger();
    private record Visual(ResourceLocation source, CompoundTag appearance) {}
    private record Model(Mob mob, int lastTick) {}
    private record GuiModel(ResourceLocation species, Set<String> aspects, Mob mob) {}
    private record PartyModel(ResourceLocation source, CompoundTag appearance, Mob mob) {}

    private static final Map<UUID, Visual> SOURCES = new HashMap<>();
    private static final Map<PokemonEntity, Model> MODELS = new WeakHashMap<>();
    private static final Map<Pokemon, PartyModel> PARTY_MODELS = new WeakHashMap<>();
    private static final Map<PosableState, Map<ResourceLocation, GuiModel>> GUI_MODELS = new WeakHashMap<>();
    private static final Set<ResourceLocation> FAILED = new HashSet<>();

    private EntityPokemonNativeVisuals() {}

    public static void setSource(UUID worldEntityId, ResourceLocation entityId, CompoundTag appearance) {
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(entityId))
            SOURCES.put(worldEntityId, new Visual(entityId, appearance.copy()));
        else SOURCES.remove(worldEntityId);
        MODELS.entrySet().removeIf(entry -> entry.getKey().getUUID().equals(worldEntityId));
    }

    public static void clear() {
        SOURCES.clear();
        MODELS.clear();
        PARTY_MODELS.clear();
        GUI_MODELS.clear();
        FAILED.clear();
        TwilightBossPoses.clear();
        EntityBattleNativeModels.clear();
    }

    public static void clearGuiModels() { GUI_MODELS.clear(); }

    public static void clearResourceModels() {
        MODELS.clear(); PARTY_MODELS.clear(); GUI_MODELS.clear(); FAILED.clear();
        EntityBattleNativeModels.clear();
    }

    public static void reportFailure(Mob mob, RuntimeException exception) {
        fail(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), exception);
        GUI_MODELS.clear();
    }

    public static Mob modelFor(PokemonEntity pokemon) {
        Visual visual = SOURCES.get(pokemon.getUUID());
        // A newly spawned or permanently converted Pokemon can be rendered one frame
        // before the tracking packet arrives. The origin is already serialized on the
        // individual, so use it as a deterministic fallback instead of briefly (or
        // permanently, after a failed cache lookup) falling back to a blank model.
        if (visual == null) {
            ResourceLocation source = EntityPokemonOrigin.entityId(pokemon.getPokemon()).orElse(null);
            if (source == null) source = EntityBattleClientProfiles.sourceForSpecies(
                    pokemon.getPokemon().getSpecies().getResourceIdentifier());
            if (source != null) {
                visual = new Visual(source, EntityPokemonOrigin.appearance(pokemon.getPokemon())
                        .orElseGet(CompoundTag::new));
                SOURCES.put(pokemon.getUUID(), visual);
            }
        }
        if (visual == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(visual.source())
                || FAILED.contains(visual.source())) return null;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        Model state = MODELS.get(pokemon);
        Mob model = state == null ? null : state.mob();
        if (model == null || model.level() != client.level) {
            try {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(visual.source());
                Entity created = type.create(client.level);
                if (!(created instanceof Mob mob)) return null;
                model = mob;
                EntityPokemonOrigin.restoreAppearance(type, model, visual.appearance());
                state = new Model(model, pokemon.tickCount);
                MODELS.put(pokemon, state);
            } catch (RuntimeException exception) {
                fail(visual.source(), exception);
                return null;
            }
        }
        if (state.lastTick() != pokemon.tickCount) {
            /*
             * The display mob is never added to the client level, so its LivingEntity
             * tick/travel loop cannot update WalkAnimationState.  Copying only
             * PokemonEntity.walkAnimation.speed() made ordinary native models (most
             * visibly the iron golem) slide while their legs stayed in the idle pose:
             * source renderers consume both speed and the accumulated position, and
             * several vanilla renderers also use that state for body sway.
             *
             * Run the same displacement based calculation that LivingEntity uses.
             * This keeps the source renderer's animation clock in the same phase as
             * the real PokemonEntity, including follow movement and client lerps.  A
             * flying source includes vertical displacement just like vanilla flying
             * mobs; walking sources intentionally ignore it.
             */
            model.xo = pokemon.xo;
            model.yo = pokemon.yo;
            model.zo = pokemon.zo;
            model.setPos(pokemon.getX(), pokemon.getY(), pokemon.getZ());
            model.calculateEntityAnimation(model instanceof net.minecraft.world.entity.animal.FlyingAnimal);
            MODELS.put(pokemon, new Model(model, pokemon.tickCount));
        }
        model.setPos(pokemon.getX(), pokemon.getY(), pokemon.getZ());
        model.xo = pokemon.xo; model.yo = pokemon.yo; model.zo = pokemon.zo;
        model.setGlowingTag(pokemon.isCurrentlyGlowing());
        // A PokemonEntity's look animation is not a vanilla Mob's head pose. Reuse its
        // facing direction for the body and keep the native model's head level.
        float facing = pokemon.getYRot();
        model.setYRot(facing);
        model.setXRot(0F);
        model.yBodyRot = facing;
        model.yBodyRotO = facing;
        model.yHeadRot = facing;
        model.yHeadRotO = facing;
        model.tickCount = pokemon.tickCount;
        // Native multipart bosses deliberately avoid head-only frustum culling.
        if (model.noCulling) pokemon.noCulling = true;
        model.hurtTime = pokemon.hurtTime;
        model.deathTime = pokemon.deathTime;
        model.attackAnim = pokemon.attackAnim;
        model.oAttackAnim = pokemon.oAttackAnim;
        TwilightBossPoses.apply(pokemon, model);
        return model;
    }

    public static boolean render(PokemonEntity pokemon, float yaw, float partialTick,
                                 PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (EntityBattleModelRepository.usesRepository(pokemon.getPokemon().getSpecies().getResourceIdentifier(),
                (PosableState) pokemon.getDelegate())) return false;
        Mob model = modelFor(pokemon);
        if (model == null) return false;
        try {
            float scale = pokemon.getDelegate() instanceof PokemonClientDelegate delegate
                    ? delegate.getActiveSendoutScale() : 1F;
            if (scale <= 0F || pokemon.isInvisible()) return true;
            poseStack.pushPose();
            try {
                poseStack.scale(scale, scale, scale);
                var offset = TwilightBossPoses.offset(pokemon, partialTick);
                poseStack.translate(offset.x, offset.y, offset.z);
                EntityBattleNativeModels.prepare(model, pokemon);
                EntityBattleNativeModels.render(model, yaw, partialTick, poseStack, buffers, packedLight);
            } finally {
                poseStack.popPose();
            }
            return true;
        } catch (RuntimeException exception) {
            fail(SOURCES.get(pokemon.getUUID()).source(), exception);
            return false;
        }
    }

    public static Mob modelFor(Pokemon pokemon) {
        EntityPokemonPresentation.Visual decoded = EntityPokemonPresentation.decode(pokemon.getAspects());
        ResourceLocation source = decoded != null ? decoded.source()
                : EntityPokemonOrigin.entityId(pokemon).orElse(null);
        CompoundTag appearance = decoded != null ? decoded.appearance()
                : EntityPokemonOrigin.appearance(pokemon).orElseGet(CompoundTag::new);
        return modelFor(source, pokemon, appearance);
    }

    public static Mob modelFor(ResourceLocation species, PosableState state) {
        return modelFor(species, state, EntityBattleClientProfiles.sourceForSpecies(species));
    }

    static Mob modelFor(ResourceLocation species, PosableState state, ResourceLocation preferredSource) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        Set<String> aspects = state.getCurrentAspects();
        var models = GUI_MODELS.computeIfAbsent(state, ignored -> new HashMap<>());
        GuiModel cached = models.get(species);
        if (cached != null && species.equals(cached.species()) && aspects.equals(cached.aspects())
                && (cached.mob() == null || cached.mob().level() == client.level)) {
            return cached.mob() != null && FAILED.contains(BuiltInRegistries.ENTITY_TYPE.getKey(cached.mob().getType()))
                    ? null : cached.mob();
        }
        EntityPokemonPresentation.Visual decoded = EntityPokemonPresentation.decode(aspects);
        if (decoded != null && preferredSource != null && !preferredSource.equals(decoded.source())) decoded = null;
        ResourceLocation source = decoded != null ? decoded.source()
                : preferredSource;
        Mob mob = modelFor(source, null, decoded == null ? new CompoundTag() : decoded.appearance());
        models.put(species, new GuiModel(species, Set.copyOf(aspects), mob));
        return mob;
    }

    private static Mob modelFor(ResourceLocation source, Pokemon pokemon, CompoundTag appearance) {
        if (source == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(source)
                || FAILED.contains(source)) return null;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        PartyModel cached = pokemon == null ? null : PARTY_MODELS.get(pokemon);
        if (cached != null && cached.source().equals(source) && cached.appearance().equals(appearance)
                && cached.mob().level() == client.level) return cached.mob();
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(source);
            Entity created = type.create(client.level);
            if (!(created instanceof Mob mob)) return null;
            EntityPokemonOrigin.restoreAppearance(type, mob, appearance);
            if (pokemon != null) PARTY_MODELS.put(pokemon, new PartyModel(source, appearance.copy(), mob));
            return mob;
        } catch (RuntimeException exception) {
            fail(source, exception);
            return null;
        }
    }

    private static void fail(ResourceLocation source, RuntimeException exception) {
        if (FAILED.add(source)) LOGGER.error("Native renderer failed for {}; using Cobblemon renderer", source, exception);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof PokemonEntity pokemon)) return;
        SOURCES.remove(pokemon.getUUID());
        MODELS.remove(pokemon);
        TwilightBossPoses.remove(pokemon.getUUID());
    }
}
