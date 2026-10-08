package dev.entitybattle.check;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.entitybattle.api.*;
import dev.entitybattle.battle.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Local integration check, added only by boss-smoke.init.gradle, never shipped. */
@EventBusSubscriber(modid="entitybattle")
public final class BossSmoke {
    private static final Logger LOG=LogUtils.getLogger();
    private static boolean ready;
    private static int ticks;
    private static PokemonBattle battle;
    private static PokemonEntity boss;
    private static List<PokemonEntity> followers;
    private static int cleanupTick=-1;
    private static int animationCount;
    private static boolean rulesSent;
    private static boolean rulesDone;
    private static int animationDeadline;
    private static com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction instruction;
    @SubscribeEvent public static void started(ServerStartedEvent event) { ready=true; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ready) return;
        try {
            ticks++;
            if(ticks==20) {
                Class.forName("com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction");
                var level=event.getServer().overworld();
                level.setChunkForced(0,0,true);
                var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"BossSmoke"));
                player.setPos(0,100,5);
                // Permit native actor lookup; no real player or network is involved.
                var field=event.getServer().getPlayerList().getClass().getSuperclass();
                var playerList=event.getServer().getPlayerList();
                var byUUID=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
                byUUID.setAccessible(true);
                ((Map<UUID,net.minecraft.server.level.ServerPlayer>)byUUID.get(playerList)).put(player.getUUID(),player);
                var party=PlayerExtensionsKt.party(player);
                verifyPresentation(level);
                var first=PokemonSpecies.getByIdentifier(id("cobblemon:charizard")).create(100);
                party.add(first);
                var second=PokemonSpecies.getByIdentifier(id("cobblemon:blastoise")).create(100);
                party.add(second);
                for(int i=0;i<4;i++) party.add(PokemonSpecies.getByIdentifier(id("cobblemon:pikachu")).create(100));
                require(first.sendOut(level,new net.minecraft.world.phys.Vec3(0,100,3),null,entity->kotlin.Unit.INSTANCE)!=null,
                        "Player Pokemon sent out through native API");
                var profile=EntityBattleProfiles.get(id("twilightforest:lich"));
                require(profile!=null,"Lich profile loaded");
                var source=(Mob)BuiltInRegistries.ENTITY_TYPE.get(profile.entity()).create(level);
                source.setPos(0,100,0);
                // Reproduce a narrow Lich tower: summoned allies can be behind walls.
                for(int y=100;y<104;y++) for(int z=-1;z<3;z++) {
                    level.setBlock(new net.minecraft.core.BlockPos(1,y,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                    level.setBlock(new net.minecraft.core.BlockPos(-2,y,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                }
                var converted=EntityNativePokemonConversion.sendOut(source,profile,false);
                require(converted!=null,"Lich converted");
                boss=converted.entity();
                require(boss.getPokemon().getAbility().getName().equals("entitybattletwilightdominion"),"Lich registered ability assigned");
                require(PokemonSpecies.getByIdentifier(id("entitybattle:twilightforest_lich")).create(1).getAbility().getName().equals("entitybattletwilightdominion"),"Level-one player reward species has the same registered ability");
                boss.getPokemon().onChange(null);
                require(boss.getBrain().checkMemory(CobblemonMemories.HERD_SIZE,net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED),"Horde memory registered after AI rebuild");
                require(boss.getBrain().checkMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED),"Visible memory registered");
                var result=BattleBuilder.INSTANCE.pve(player,boss,first.getUuid());
                require(result instanceof SuccessfulBattleStart,"pve succeeded: "+result);
                battle=((SuccessfulBattleStart)result).getBattle();
                LOG.info("BOSS_SMOKE actors: side1={} size={}, side2={} size={}",battle.getSide1().getActors()[0].getClass().getName(),battle.getSide1().getActors()[0].getPokemonList().size(),battle.getSide2().getActors()[0].getClass().getName(),battle.getSide2().getActors()[0].getPokemonList().size());
                require(battle.getSide2().getActors()[0].getClass().getName().contains("HordeBattleActor"),"Both mod hooks produced HordeBattleActor");
                var members=battle.getSide2().getActors()[0].getPokemonList();
                require(members.size()==3,"3 opponent Pokemon, actual "+members.size());
                require(battle.getSide1().getActors()[0].getPokemonList().size()==6,"Full six-Pokemon player party retained");
                followers=members.stream().filter(p->p.getOriginalPokemon().getPersistentData().getBoolean(EntityBossHordes.SUMMONED_MINION)).map(p->p.getEntity()).toList();
                require(followers.stream().allMatch(p->p.getPokemon().isUncatchable()),"Temporary allies uncatchable");
                require(followers.stream().allMatch(p->BuiltInRegistries.ITEM.getKey(p.getPokemon().heldItem().getItem()).equals(id("cobblemon:leftovers"))),"All minions equipped Leftovers");
                require(boss.getPokemon().getLevel()>=33 && boss.getPokemon().getLevel()<=37,"Lich level in 33-37 range");
                require(followers.stream().allMatch(p->p.getPokemon().getLevel()==boss.getPokemon().getLevel()),"Minions follow Lich level");
                require(BuiltInRegistries.ITEM.getKey(boss.getPokemon().heldItem().getItem()).equals(id("twilightforest:lich_trophy")),"Lich trophy equipped");
                LOG.info("BOSS_SMOKE: real pve horde started: {} format {}",battle.getBattleId(),battle.getFormat());
            }
            if(ticks>=25 && !rulesSent) {
                var active=battle.getSide2().getActivePokemon().stream()
                        .filter(p->p.getBattlePokemon()!=null && p.getBattlePokemon().getUuid().equals(boss.getPokemon().getUuid()))
                        .findFirst().orElse(null);
                if(active==null || active.isGone()) {
                    require(ticks<400,"Waiting for Showdown active boss, tick "+ticks);
                    return;
                }
                var original=List.of(move("protect",MoveTarget.self),move("supersonic",MoveTarget.normal),move("shadowball",MoveTarget.normal),move("flameburst",MoveTarget.normal));
                var moveset=new ShowdownMoveset(); moveset.setMoves(original);
                var nativeChoice=new RandomBattleAI().choose(active,battle,battle.getSide2(),moveset,false);
                require(nativeChoice.isValid(active,moveset,false),"Native response remains valid");
                require(moveset.getMoves().size()==4,"All four moves remain candidates without phase filtering");
                var healing = new ShowdownMoveset(); healing.setMoves(List.of(move("healpulse",MoveTarget.any)));
                var healed = new RandomBattleAI().choose(active,battle,battle.getSide2(),healing,false);
                require(healed instanceof MoveActionResponse && active.getActor().getActivePokemon().stream()
                        .anyMatch(ally->ally!=active && ally.getPNX().equals(((MoveActionResponse)healed).getTargetPnx())),"Retained support policy targets another ally, never player or self");
                require(healed.isValid(active,healing,false),"Retained Heal Pulse policy returns a legal native action");
                String verify="""
                    >eval (() => {
                        const lich = battle.getAllActive().find(p => p.baseSpecies.id === 'entitybattletwilightforestlich');
                        const guards = battle.getAllActive().filter(p => p.hasAbility('entitybattlesoulcovenant'));
                        const source = battle.sides[0].active[0];
                        if (!lich || guards.length !== 2) throw new Error('LICH_SERVER_FAIL roster');
                        lich.clearItem();
                        const hp = [lich.hp, ...guards.map(p => p.hp)];
                        const actual = battle.damage(90, lich, source, battle.dex.moves.get('nightshade'));
                        if (actual !== 24 || guards.some((p, i) => hp[i + 1] - p.hp !== 30)) throw new Error('LICH_SERVER_FAIL split');
                        if (hp[0] - lich.hp !== 24) throw new Error('LICH_SERVER_FAIL lich HP');
                        guards.forEach(p => battle.heal(p.maxhp, p, p, battle.dex.moves.get('recover')));
                        battle.heal(lich.maxhp, lich, lich, battle.dex.moves.get('recover'));
                        const beforeMove = [lich.hp, ...guards.map(p => p.hp)];
                        const sourceLevel = source.level;
                        source.level = 90;
                        battle.actions.useMove('nightshade', source, lich);
                        source.level = sourceLevel;
                        if (beforeMove[0] - lich.hp !== 24 || guards.some((p, i) => beforeMove[i + 1] - p.hp !== 30)) throw new Error('LICH_SERVER_FAIL actual move split');
                        guards.forEach(p => battle.heal(p.maxhp, p, p, battle.dex.moves.get('recover')));
                        battle.heal(lich.maxhp, lich, lich, battle.dex.moves.get('recover'));
                        const directHp = [lich.hp, ...guards.map(p => p.hp)];
                        if (battle.directDamage(90, lich, lich, {id: 'strugglerecoil'}) !== 24
                            || guards.some((p, i) => directHp[i + 1] - p.hp !== 30)) throw new Error('LICH_SERVER_FAIL direct split');
                        lich.hp = Math.floor(lich.maxhp / 2) + 4;
                        battle.damage(30, lich, source, battle.dex.conditions.get('psn'));
                        battle.heal(lich.maxhp, lich, lich, battle.dex.moves.get('recover'));
                        if (!lich.entityBattleTwilightSiphon || lich.hp !== lich.maxhp) throw new Error('LICH_SERVER_FAIL latch');
                        if (battle.damage(30, lich, source, battle.dex.moves.get('nightshade')) !== 10) throw new Error('LICH_SERVER_FAIL healed reduction');
                        battle.add('-message', 'LICH_SERVER_RULES_PASS');
                    })();
                    """.replace('\n',' ');
                com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService().send(battle.getBattleId(),new String[]{verify});
                rulesSent=true;
            }
            if(rulesSent && !rulesDone) {
                if(battle.getBattleLog().stream().noneMatch(entry->entry.lines().anyMatch(line->line.equals("|-message|LICH_SERVER_RULES_PASS")))) {
                    if(ticks>=400) throw new AssertionError("Runtime ability validation did not complete");return;
                }
                var active=battle.getSide2().getActivePokemon().stream().filter(p->p.getBattlePokemon()!=null&&p.getBattlePokemon().getUuid().equals(boss.getPokemon().getUuid())).findFirst().orElseThrow();
                EntityBattleAnimations.registerMove(BuiltInRegistries.ENTITY_TYPE.get(id("twilightforest:lich")),"protect",entity->{animationCount++;entity.swing(net.minecraft.world.InteractionHand.MAIN_HAND,true);});
                var instructions=new com.cobblemon.mod.common.battles.dispatch.InstructionSet();
                String identity=active.getPNX()+": "+active.getBattlePokemon().getUuid();
                var message=new com.cobblemon.mod.common.api.battles.interpreter.BattleMessage("|move|"+identity+"|Protect|"+identity);
                instruction=new com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction(instructions,message);
                instructions.getInstructions().add(instruction);
                instruction.invoke(battle);
                require(animationCount==0,"Queued move does not animate prematurely");
                rulesDone=true;
                animationDeadline=ticks+600;
                LOG.info("BOSS_SMOKE: native registry abilities, all-damage share and HP latch passed");
            }
            if(rulesDone && cleanupTick<0) {
                if(ticks%100==0) LOG.info("BOSS_SMOKE animation state count={} futureDone={} holds={}",animationCount,instruction.getFuture().isDone(),instruction.getHolds());
                if(animationCount==1 && instruction.getFuture()!=null && instruction.getFuture().isDone()) {
                    require(animationCount==1,"Actual dispatched move triggers native animation exactly once");
                    for(var chat:battle.getChatLog()) {
                        require(!chat.getString().contains("Entity Battle Soul Covenant")
                                && !chat.getString().contains("entitybattlesoulcovenant！"),"Ability message uses translatable display name");
                    }
                    LOG.info("BOSS_SMOKE CHECK: battle engine rules and GUI presentation transport verified");
                    battle.end();cleanupTick=ticks+10;
                } else {
                    if(ticks>=animationDeadline) throw new AssertionError("Move animation dispatch timeout, count="+animationCount);
                }
            }
            if(ticks==cleanupTick) {
                require(followers.stream().allMatch(PokemonEntity::isRemoved),"All temporary followers cleaned after battle");
                require(!boss.getBrain().hasMemoryValue(CobblemonMemories.HERD_SIZE),"Temporary herd state restored");
                battle.saveBattleLog();
                LOG.info("BOSS_SMOKE PASS: both-mod pve, brain rebuild, 3 opponents, registered abilities, all-damage share, HP latch, cleanup");
                ready=false;
                event.getServer().halt(false);
            }
        } catch(Throwable failure) {
            if(battle!=null) battle.saveBattleLog();
            LOG.error("BOSS_SMOKE FAIL",failure); ready=false;
            event.getServer().halt(false);
        }
    }
    private static InBattleMove move(String id,MoveTarget target) {
        var move=new InBattleMove(); move.setId(id);move.setMove(id);move.setPp(10);move.setMaxpp(10);move.setTarget(target);return move;
    }
    private static void verifyPresentation(net.minecraft.server.level.ServerLevel level) {
        var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(level);
        sheep.setColor(net.minecraft.world.item.DyeColor.RED); sheep.setBaby(true);
        var pokemon=EntityPokemonData.getOrCreate(sheep,EntityBattleProfiles.get(id("minecraft:sheep")));
        var visual=EntityPokemonPresentation.decode(pokemon.getAspects());
        require(visual!=null && visual.source().equals(id("minecraft:sheep")),"Native source is exposed through Cobblemon aspects");
        var restored=net.minecraft.world.entity.EntityType.SHEEP.create(level);
        EntityPokemonOrigin.restoreAppearance(restored.getType(),restored,visual.appearance());
        require(restored.getColor()==net.minecraft.world.item.DyeColor.RED && restored.isBaby(),"Vanilla color and baby appearance roundtrip");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try {
            new com.cobblemon.mod.common.api.callback.PartySelectPokemonDTO(pokemon).writeToBuffer(buffer);
            var medicine=new com.cobblemon.mod.common.api.callback.PartySelectPokemonDTO(buffer);
            require(EntityPokemonPresentation.decode(medicine.getAspects()).appearance().equals(visual.appearance()),"Medicine DTO retains native appearance");
            buffer.clear();
            new com.cobblemon.mod.common.net.messages.client.trade.TradeStartedPacket.TradeablePokemon(pokemon).encode(buffer);
            var trade=com.cobblemon.mod.common.net.messages.client.trade.TradeStartedPacket.TradeablePokemon.Companion.decode(buffer);
            require(EntityPokemonPresentation.decode(trade.getAspects()).appearance().equals(visual.appearance()),"Trade DTO retains native appearance");
            buffer.clear();
            new com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO(
                    pokemon.getUuid(),UUID.randomUUID(),pokemon.getDisplayName(false),"Smoke",pokemon.getSpecies().getResourceIdentifier(),
                    pokemon.getAspects(),net.minecraft.world.item.ItemStack.EMPTY,pokemon.getLevel(),false,Set.of()).encode(buffer);
            var pasture=com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO.Companion.decode(buffer);
            require(EntityPokemonPresentation.decode(pasture.getAspects()).appearance().equals(visual.appearance()),"Pasture DTO retains appearance even without tracking an entity");
            require(!EntityBattleProfiles.visualSpeciesMap().containsKey(id("cobblemon:charizard")),"Native profile fallback never replaces ordinary Cobblemon species");
        } finally {buffer.release();}
    }
    private static ResourceLocation id(String id) {return ResourceLocation.parse(id);}
    private static void require(boolean condition,String message) {if(!condition) throw new AssertionError(message);LOG.info("BOSS_SMOKE CHECK: {}",message);}
}
