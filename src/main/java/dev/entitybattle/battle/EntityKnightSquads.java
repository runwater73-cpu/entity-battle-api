package dev.entitybattle.battle;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonData;
import dev.entitybattle.api.EntityPokemonOrigin;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Original-room squad adapter; no Twilight Forest classes are linked without that mod. */
public final class EntityKnightSquads {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation KNIGHT = ResourceLocation.parse("twilightforest:knight_phantom");
    private static final String SOURCE = "entitybattle:knight_source";
    private static final String GROUP = "entitybattle:knight_group";
    private static final String REPRESENTATIVE = "entitybattle:knight_representative";
    private static final String WAS_NATIVE = "entitybattle:knight_was_native";
    private static final String RESTORING = "entitybattle:knight_restoring";
    private static final String NATIVE_GROUP = "entitybattle:knight_settlement";
    private static final Map<UUID, Squad> SQUADS = new HashMap<>();
    private EntityKnightSquads() {}

    public static boolean isKnight(Pokemon p) {
        return p.isWild() && KNIGHT.equals(EntityPokemonOrigin.entityId(p).orElse(null));
    }
    public static boolean isMember(Pokemon p) { return p.getPersistentData().hasUUID(GROUP); }
    public static boolean isRestoringSource(Mob mob) { return mob.getPersistentData().getBoolean(RESTORING); }
    public static boolean hasNativeSettlement(Mob mob) { return mob.getPersistentData().hasUUID(NATIVE_GROUP); }
    public static List<Mob> settlementRoster(Mob self,List<Mob> original) {
        UUID group=self.getPersistentData().getUUID(NATIVE_GROUP);
        return new ArrayList<>(original.stream().filter(m->hasNativeSettlement(m)
                &&group.equals(m.getPersistentData().getUUID(NATIVE_GROUP))).toList());
    }

    public static void rememberSource(Mob source, PokemonEntity converted) {
        if (!KNIGHT.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()))) return;
        // Exclude the bound Pokemon to prevent recursively embedding previous snapshots.
        Tag previous = source.getPersistentData().get("entitybattle:pokemon");
        source.getPersistentData().remove("entitybattle:pokemon");
        try {
            CompoundTag snapshot = source.saveWithoutId(new CompoundTag());
            snapshot.putString("id", KNIGHT.toString());
            converted.getPokemon().getPersistentData().put(SOURCE, snapshot);
        } finally {
            if (previous != null) source.getPersistentData().put("entitybattle:pokemon", previous);
        }
    }

    public static boolean canReward(Pokemon p) {
        if (!isKnight(p)) return true;
        var profile=EntityBattleProfiles.get(KNIGHT);
        if(profile==null||profile.bossBattle()==null
                ||profile.bossBattle().mode()!=EntityBattleProfile.RosterMode.EXISTING_SQUAD)return true;
        var data = p.getPersistentData();
        Squad squad = data.hasUUID(GROUP) ? SQUADS.get(data.getUUID(GROUP)) : null;
        return squad != null && data.getBoolean(REPRESENTATIVE)
                && squad.members.stream().allMatch(m -> m.pokemon().isFainted());
    }

    public static void register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {
            for (var actor : event.getLosers()) for (var p : actor.getPokemonList()) {
                var data = p.getOriginalPokemon().getPersistentData();
                Squad squad = data.hasUUID(GROUP) ? SQUADS.get(data.getUUID(GROUP)) : null;
                if (squad == null || !squad.members.stream().allMatch(m -> m.pokemon().isFainted())) continue;
                squad.winner = event.getWinners().stream().filter(PlayerBattleActor.class::isInstance)
                        .map(PlayerBattleActor.class::cast).map(PlayerBattleActor::getEntity).findFirst().orElse(null);
            }
        });
    }

    public static boolean prepare(ServerPlayer player, PokemonEntity target, EntityBattleProfile profile) {
        if (!KNIGHT.equals(profile.entity())) return reject(player, "entitybattle.squad.unsupported");
        if (!ModList.get().isLoaded("hordeencounters") || !ModList.get().isLoaded("asymmetricbattles"))
            return reject(player, "entitybattle.squad.dependencies");
        if (isMember(target.getPokemon()) || BattleRegistry.getBattleByParticipatingPlayer(player) != null)
            return reject(player, "entitybattle.squad.busy");
        ServerLevel level = (ServerLevel) target.level();
        var leaderSource = target.getPokemon().getPersistentData().getCompound(SOURCE);
        Tag home = leaderSource.get("HomePos");
        UUID origin = EntityPokemonOrigin.sourceUuid(target.getPokemon()).orElse(null);
        if (home == null || origin == null) return reject(player, "entitybattle.squad.home");
        Map<UUID, Candidate> candidates = new HashMap<>();
        Entity leaderNative = level.getEntity(origin);
        candidates.put(origin, new Candidate(target, leaderNative instanceof Mob mob ? mob : null, leaderSource));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(64))) {
            if (!mob.isAlive() || mob.isRemoved()) continue;
            if (mob instanceof PokemonEntity pokemon) {
                if (pokemon == target || !isKnight(pokemon.getPokemon())) continue;
                var snapshot = pokemon.getPokemon().getPersistentData().getCompound(SOURCE);
                UUID uuid = EntityPokemonOrigin.sourceUuid(pokemon.getPokemon()).orElse(null);
                if (uuid != null && home.equals(snapshot.get("HomePos")))
                    candidates.put(uuid, new Candidate(pokemon, null, snapshot));
            } else if (KNIGHT.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()))
                    && !mob.getUUID().equals(origin)) {
                CompoundTag snapshot = mob.saveWithoutId(new CompoundTag());
                if (home.equals(snapshot.get("HomePos"))) candidates.put(mob.getUUID(), new Candidate(null, mob, snapshot));
            }
        }
        int expected = profile.bossBattle().minionCount() + 1;
        if (expected != 6 || candidates.size() != expected
                || candidates.values().stream().map(c -> c.snapshot.getInt("MyNumber")).distinct().count() != 6
                || candidates.values().stream().anyMatch(c -> c.snapshot.getInt("MyNumber") < 0 || c.snapshot.getInt("MyNumber") > 5)) {
            LOG.warn("Knight room {} roster has {} candidates, numbers {}",home,candidates.size(),
                    candidates.values().stream().map(c->c.snapshot.getInt("MyNumber")).toList());
            return reject(player, "entitybattle.squad.incomplete");
        }
        if (candidates.values().stream().anyMatch(c -> c.entity != null
                && (c.entity.getBattleId() != null || isMember(c.entity.getPokemon()))))
            return reject(player, "entitybattle.squad.busy");

        List<Member> members = new ArrayList<>();
        List<PokemonEntity> created = new ArrayList<>();
        UUID group = UUID.randomUUID();
        var previous = new EntityBossHordes.HerdMemory(target.getBrain().getMemory(CobblemonMemories.HERD_SIZE),
                target.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES));
        try {
            for (Candidate candidate : candidates.values().stream()
                    .sorted(Comparator.comparingInt(c -> c.snapshot.getInt("MyNumber"))).toList()) {
                PokemonEntity entity = candidate.entity;
                if (entity == null) {
                    var converted = EntityNativePokemonConversion.sendOut(candidate.nativeMob, profile, false);
                    if (converted == null) throw new IllegalStateException("Could not prepare knight");
                    entity = converted.entity(); created.add(entity);
                }
                members.add(new Member(entity, candidate.nativeMob, candidate.nativeMob != null));
            }
            int encounterLevel = Math.clamp(target.getPokemon().getLevel(), profile.minLevel(), profile.maxLevel());
            for (Member member : members) {
                Pokemon pokemon = member.pokemon();
                double healthRatio = pokemon.getCurrentHealth() / (double) pokemon.getMaxHealth();
                if (pokemon.getLevel() != encounterLevel) {
                    pokemon.setLevel(encounterLevel); dev.entitybattle.api.EntityPokemonData.initializeMoves(pokemon, profile);
                    pokemon.setCurrentHealth(Math.max(1, (int) Math.round(pokemon.getMaxHealth() * healthRatio)));
                }
                boolean representative = pokemon.getPersistentData().getCompound(SOURCE).getInt("MyNumber") == 0;
                pokemon.getPersistentData().putUUID(GROUP, group);
                pokemon.getPersistentData().putBoolean(REPRESENTATIVE, representative);
                pokemon.getPersistentData().putBoolean(WAS_NATIVE, member.wasNative);
                EntityBossHordes.applyHeldItem(member.entity, representative ? profile.heldItem() : profile.bossBattle().minionHeldItem());
                pokemon.onChange(null);
                if (member.entity != target) member.entity.getBrain().setMemory(CobblemonMemories.HERD_LEADER, target.getStringUUID());
            }
            var followers = members.stream().map(m -> (net.minecraft.world.entity.LivingEntity) m.entity).filter(e -> e != target).toList();
            target.getBrain().setMemory(CobblemonMemories.HERD_SIZE, followers.size());
            target.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                    new EntityBossHordes.EncounterMembers(target, followers));
            SQUADS.put(group, new Squad(group, target, members, previous));
            // Remove originals only after every member is ready. Rejected starts roll back next tick.
            for (Member member : members) if (member.nativeMob != null && member.entity != target) member.nativeMob.discard();
            LOG.info("Prepared original six-knight squad at {}", home);
            return true;
        } catch (RuntimeException error) {
            previous.restore(target);
            members.forEach(m -> clearGroup(m.pokemon()));
            created.forEach(e -> e.getPokemon().recall());
            LOG.error("Could not prepare original knight squad", error);
            return reject(player, "entitybattle.squad.failed");
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (Squad squad : List.copyOf(SQUADS.values())) {
            if (squad.leader.level().getServer() != event.getServer()) continue;
            if (squad.battle == null) {
                squad.previous.restore(squad.leader);
                UUID battleId = squad.leader.getBattleId();
                PokemonBattle battle = battleId == null ? null : BattleRegistry.getBattle(battleId);
                if (battle == null || squad.members.stream().anyMatch(m -> !battleId.equals(m.entity.getBattleId()))) {
                    if (battle != null) battle.stop();
                    finish(squad, squad.winner != null); continue;
                }
                squad.battle = battle;
                for (Member member : squad.members) if (member.nativeMob != null && !member.nativeMob.isRemoved()) member.nativeMob.discard();
                battle.getOnEndHandlers().add(ended -> { squad.finished = true; return kotlin.Unit.INSTANCE; });
                LOG.info("Started full original knight squad battle {}", battleId);
            }
            if (squad.finished || squad.battle.getEnded()) finish(squad, squad.winner != null);
        }
    }

    private static void finish(Squad squad, boolean won) {
        boolean complete = true;
        for (Member member : squad.members) {
            if (member.settled) continue;
            Pokemon pokemon = member.pokemon();
            ServerLevel level = (ServerLevel) member.entity.level();
            UUID uuid = EntityPokemonOrigin.sourceUuid(pokemon).orElseThrow();
            if (!won && !member.wasNative) {
                clearGroup(pokemon); pokemon.recall();
                pokemon.setCurrentHealth(Math.max(1, pokemon.getCurrentHealth()));
                member.settled = pokemon.sendOut(level, member.entity.position(), null, e -> kotlin.Unit.INSTANCE) != null;
            } else {
                Mob restored = level.getEntity(uuid) instanceof Mob mob && !(mob instanceof PokemonEntity)
                        ? mob : restore(level, pokemon.getPersistentData().getCompound(SOURCE), pokemon);
                if (restored != null) {
                    clearGroup(pokemon);
                    if (!won) pokemon.setCurrentHealth(Math.max(1, pokemon.getCurrentHealth()));
                    EntityPokemonData.save(restored, pokemon); pokemon.recall();
                    member.settled = true; member.restored = restored;
                }
            }
            complete &= member.settled;
        }
        if (!complete) { LOG.error("Knight squad {} has pending restoration; recovery data retained", squad.id); return; }
        // Restore all originals together before executing their actual collective death callbacks.
        if(won)for(Member member:squad.members)member.restored.getPersistentData().putUUID(NATIVE_GROUP,squad.id);
        if (won) for (Member member : squad.members) {
            Mob mob = member.restored;
            mob.setLastHurtByPlayer(squad.winner);
            mob.setHealth(0);
            mob.die(((ServerLevel) mob.level()).damageSources().playerAttack(squad.winner));
        }
        SQUADS.remove(squad.id);
        LOG.info("Settled knight squad {}: {}", squad.id, won ? "native group victory" : "interrupted encounter");
    }

    private static Mob restore(ServerLevel level, CompoundTag snapshot, Pokemon pokemon) {
        CompoundTag tag = snapshot.copy(); tag.putString("id", KNIGHT.toString());
        Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
        if (!(loaded instanceof Mob mob)) return null;
        EntityBattleWorldBridge.applyPokemonHealthToMob(mob, pokemon);
        mob.getPersistentData().putBoolean(RESTORING, true);
        try { return level.addFreshEntity(mob) ? mob : null; }
        finally { mob.getPersistentData().remove(RESTORING); }
    }

    @SubscribeEvent public static void orphan(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity entity) || entity.tickCount % 20 != 0
                || !(entity.level() instanceof ServerLevel level) || !isMember(entity.getPokemon())) return;
        Pokemon pokemon = entity.getPokemon();
        if (SQUADS.containsKey(pokemon.getPersistentData().getUUID(GROUP))
                || entity.getBattleId() != null && BattleRegistry.getBattle(entity.getBattleId()) != null) return;
        if (!pokemon.getPersistentData().getBoolean(WAS_NATIVE)) { clearGroup(pokemon); return; }
        UUID uuid = EntityPokemonOrigin.sourceUuid(pokemon).orElseThrow();
        Mob mob = level.getEntity(uuid) instanceof Mob existing ? existing
                : restore(level, pokemon.getPersistentData().getCompound(SOURCE), pokemon);
        if (mob != null) {
            clearGroup(pokemon); pokemon.setCurrentHealth(Math.max(1, pokemon.getCurrentHealth()));
            EntityPokemonData.save(mob, pokemon); pokemon.recall();
        }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Squad squad : List.copyOf(SQUADS.values())) {
            if (squad.battle != null && !squad.battle.getEnded()) squad.battle.stop();
            finish(squad, false);
        }
        SQUADS.clear();
    }
    private static void clearGroup(Pokemon pokemon) {
        pokemon.getPersistentData().remove(GROUP); pokemon.getPersistentData().remove(REPRESENTATIVE);
        pokemon.getPersistentData().remove(WAS_NATIVE); pokemon.onChange(null);
    }
    private static boolean reject(ServerPlayer player, String key) {
        LOG.warn("Original knight squad rejected: {}", key);
        player.displayClientMessage(Component.translatable(key), false); return false;
    }
    private record Candidate(PokemonEntity entity, Mob nativeMob, CompoundTag snapshot) {}
    private static final class Member {
        final PokemonEntity entity; final Mob nativeMob; final boolean wasNative;
        boolean settled; Mob restored;
        Member(PokemonEntity entity, Mob nativeMob, boolean wasNative) {
            this.entity = entity; this.nativeMob = nativeMob; this.wasNative = wasNative;
        }
        Pokemon pokemon() { return entity.getPokemon(); }
    }
    private static final class Squad {
        final UUID id; final PokemonEntity leader; final List<Member> members;
        final EntityBossHordes.HerdMemory previous;
        PokemonBattle battle; ServerPlayer winner; boolean finished;
        Squad(UUID id, PokemonEntity leader, List<Member> members, EntityBossHordes.HerdMemory previous) {
            this.id = id; this.leader = leader; this.members = List.copyOf(members); this.previous = previous;
        }
    }
}
