package dev.entitybattle.check;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.entitybattle.api.*;
import dev.entitybattle.battle.EntityNativePokemonConversion;
import dev.entitybattle.compat.HordeAITargets;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Isolated check using the installed TeamRocket actor and join hook. Never shipped. */
@EventBusSubscriber(modid="entitybattle")
public final class MaidSmoke {
    private static final org.slf4j.Logger LOG=LogUtils.getLogger();
    private static boolean ready;
    private static int ticks, submitted;
    private static int observedTurn, turnStarted;
    private static PokemonBattle battle;
    private static UUID switchTo;
    private static boolean targetsChecked;
    private static final boolean KNIGHTS = Boolean.getBoolean("entitybattle.test.knights");
    private static int faintStage, faintTick;
    private static BattlePokemon formerLeader;
    @SubscribeEvent public static void started(ServerStartedEvent e) {ready=true;}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        if(!ready) return;
        try {
            ticks++;
            if(ticks==20) {
                var level=e.getServer().overworld();
                level.setChunkForced(0,0,true);
                if (KNIGHTS) for (int x=-16;x<=20;x++) for (int z=-12;z<=20;z++)
                    level.setBlock(new net.minecraft.core.BlockPos(x,99,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"MaidSmoke"));
                player.setPos(0,100,5);
                var byUUID=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
                byUUID.setAccessible(true);
                ((Map<UUID,net.minecraft.server.level.ServerPlayer>)byUUID.get(e.getServer().getPlayerList())).put(player.getUUID(),player);
                var party=PlayerExtensionsKt.party(player);
                for(int i=0;i<6;i++) party.add(PokemonSpecies.getByIdentifier(id("cobblemon:charizard")).create(100));
                switchTo=party.get(1).getUuid();
                var config=Class.forName("com.journeysouvenirs.examplemod.Config");
                ((net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)config.getField("DISOBEY_ENABLED").get(null)).set(true);
                for(var key:List.of("DISOBEY_TRIGGER_CHANCE","DISOBEY_LOAF_CHANCE"))
                    ((net.neoforged.neoforge.common.ModConfigSpec.DoubleValue)config.getField(key).get(null)).set(1.0);
                var profile=EntityBattleProfiles.get(id(KNIGHTS ? "twilightforest:knight_phantom" : "twilightforest:lich"));
                if (KNIGHTS) {
                    for (var mob:level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(-64,-64,-64,64,320,64)))
                        if(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).equals(profile.entity()))mob.discard();
                    var home=net.minecraft.core.GlobalPos.of(level.dimension(),new net.minecraft.core.BlockPos(7,100,0));
                    for(int i=0;i<6;i++) {
                        var knight=(Mob)BuiltInRegistries.ENTITY_TYPE.get(profile.entity()).create(level);
                        knight.getClass().getMethod("setNumber",int.class).invoke(knight,i);
                        knight.setPos(i,100,0);knight.setNoAi(true);knight.setNoGravity(true);level.addFreshEntity(knight);
                        knight.getClass().getMethod("setRestrictionPoint",net.minecraft.core.GlobalPos.class).invoke(knight,home);
                        if(i==0) {
                            // The representative is converted after all six sources are present below.
                            knight.getPersistentData().putBoolean("maid_test_representative",true);
                        }
                    }
                }
                var source=(Mob)BuiltInRegistries.ENTITY_TYPE.get(profile.entity()).create(level);
                if(KNIGHTS)source=level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(-16,99,-16,20,104,20),m->m.getPersistentData().getBoolean("maid_test_representative")).get(0);
                source.setPos(0,100,0);
                var boss=EntityNativePokemonConversion.sendOut(source,profile,false).entity();
                var result=BattleBuilder.INSTANCE.pve(player,boss,party.get(0).getUuid());
                if(!(result instanceof SuccessfulBattleStart success)) throw new AssertionError("start "+result);
                battle=success.getBattle();battle.setMute(false);
                // Invoke the mod's join, not a copied implementation. Conditions/UI are outside this check.
                var maid=BuiltInRegistries.ENTITY_TYPE.get(id("touhou_little_maid:maid")).create(level);
                maid.setPos(2,100,5);level.addFreshEntity(maid);
                var maidPokemon=PokemonSpecies.getByIdentifier(id("cobblemon:charizard")).create(35);
                maidPokemon.getMoveSet().clear();
                for(var move:List.of("airslash","dragonpulse")) maidPokemon.getMoveSet().add(Moves.getByName(move).create());
                var join=Arrays.stream(Class.forName("com.jhnwudi666.teamrocket.maid.MaidHordeJoiner").getDeclaredMethods()).filter(m->m.getName().equals("join")).findFirst().orElseThrow();
                join.setAccessible(true);
                join.invoke(null,battle,player,maid,new com.cobblemon.mod.common.pokemon.Pokemon[]{maidPokemon},battle.getSide1().getActors()[0],battle.getSide2().getActors()[0]);
                dev.entitybattle.compat.MaidHordeCompatibility.prepare(battle);
                LOG.info("MAID_SMOKE joined {} actors",battle.getSide1().getActors().length);
            }
            if(battle!=null) {
                var maidActive=battle.getActor("p3").getActivePokemon().get(0);
                if(!targetsChecked && !maidActive.isGone()) {if(!KNIGHTS)checkTargets(maidActive);else if(battle.getSide2().getActors()[0].getPokemonList().size()!=6)throw new AssertionError("Six original opponents expected");targetsChecked=true;}
                if(observedTurn!=battle.getTurn()) {observedTurn=battle.getTurn();turnStarted=ticks;}
                if(ticks%40==0) for(var actor:battle.getActors()) LOG.info("MAID_SMOKE state {} {} active={} request={} must={} responses={}",battle.getTurn(),actor.getShowdownId(),actor.getActivePokemon().size(),actor.getRequest()==null?null:actor.getRequest().getActive(),actor.getMustChoose(),actor.getResponses());
                var actor=(PlayerBattleActor)battle.getSide1().getActors()[0];
                if((!KNIGHTS || battle.getTurn()<4) && actor.getMustChoose() && actor.getRequest()!=null && actor.getRequest().getActive()!=null && !actor.getRequest().getActive().isEmpty() && actor.getActivePokemon().get(0).getBattlePokemon()!=null) {
                    var actions=new ArrayList<ShowdownActionResponse>();
                    if(battle.getTurn()==2) actions.add(new SwitchActionResponse(switchTo));
                    else {
                        if(battle.getTurn()>2) ((net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)Class.forName("com.journeysouvenirs.examplemod.Config").getField("DISOBEY_ENABLED").get(null)).set(false);
                        var target=battle.getSide2().getActivePokemon().stream().filter(p->p.getBattlePokemon()!=null&&p.isAlive()).findFirst().orElseThrow();
                        actions.add(new MoveActionResponse("scaryface",target.getPNX(),null));
                    }
                    LOG.info("MAID_SMOKE submit {} turn {}",actions,battle.getTurn());
                    com.cobblemon.mod.common.net.serverhandling.battle.BattleSelectActionsHandler.INSTANCE.handle(
                            new com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket(battle.getBattleId(),actions),e.getServer(),actor.getEntity());
                    if(battle.getTurn()==1 && !(actions.get(0) instanceof PassActionResponse)) throw new AssertionError("Journey disobedience did not replace action with pass");
                    submitted++;
                }
                if(battle.getTurn()>=3 && !actor.getActivePokemon().get(0).getBattlePokemon().getUuid().equals(switchTo)) throw new AssertionError("Switch not reflected in active player Pokemon");
                if(KNIGHTS && battle.getTurn()>=4 && faintStage==0 && battle.getDispatches().isEmpty()) {
                    var horde=battle.getSide2().getActors()[0];
                    formerLeader=(BattlePokemon)horde.getClass().getMethod("getLeader").invoke(horde);
                    formerLeader.getEffectedPokemon().setCurrentHealth(0);
                    formerLeader.getOriginalPokemon().setCurrentHealth(0);
                    formerLeader.getOriginalPokemon().recall();
                    var anchor=((com.cobblemon.mod.common.api.battles.model.actor.FleeableBattleActor)horde).getWorldAndPosition();
                    if(anchor==null)throw new AssertionError("Survivors lost position after leader recall");
                    battle.checkFlee();if(battle.getEnded())throw new AssertionError("Leader faint falsely ended encounter");
                    faintStage=1;faintTick=ticks;
                    LOG.info("MAID_KNIGHTS CHECK: null leader entity, five live members; native flee check safe");
                } else if(KNIGHTS && faintStage==1 && ticks-faintTick>=30) {
                    var horde=battle.getSide2().getActors()[0];
                    for(var p:horde.getPokemonList()){
                        p.getEffectedPokemon().setCurrentHealth(0);p.getOriginalPokemon().setCurrentHealth(0);p.getOriginalPokemon().recall();
                    }
                    if(((com.cobblemon.mod.common.api.battles.model.actor.FleeableBattleActor)horde).getWorldAndPosition()==null)
                        throw new AssertionError("Final faint dispatch lost encounter anchor");
                    battle.checkFlee();if(battle.getEnded())throw new AssertionError("Final faint became false flee");
                    faintStage=2;faintTick=ticks;
                    LOG.info("MAID_KNIGHTS CHECK: all six entities recalled; waiting for victory without null access");
                } else if(KNIGHTS && faintStage==2 && ticks-faintTick>=30) {
                    com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService().send(battle.getBattleId(),new String[]{">eval battle.win(battle.sides[0]);"});
                    faintStage=3;
                } else if(KNIGHTS && faintStage==3 && battle.getEnded()) {
                    LOG.info("MAID_KNIGHTS PASS: actual TeamRocket 2v6, disobedience/switch/turns, leader-first and all-fainted dispatch windows, normal victory");stop(e);
                } else if(!KNIGHTS && battle.getTurn()>=4) {if(!targetsChecked) throw new AssertionError("Horde empty targets not checked");LOG.info("MAID_SMOKE PASS submitted={}: Journey disobedience, player switch, maid native any-target AI, deterministic empty-slot repairs, 2v3 consecutive turns",submitted);stop(e);}
                if(ticks-turnStarted>=1200) throw new AssertionError("No turn progress for 60 seconds, turn "+battle.getTurn());
            }
        } catch(Throwable failure) {LOG.error("MAID_SMOKE FAIL",failure);stop(e);}
    }
    private static void checkTargets(ActiveBattlePokemon active) {
        var empty=battle.getSide2().getActivePokemon().stream().filter(p->!p.hasPokemon()).toList();
        if(empty.size()!=3) throw new AssertionError("Expected 3 reserved empty Horde slots: "+empty.size());
        for(var id:List.of("airslash","dragonpulse")) {
            var nativeMove=new InBattleMove();nativeMove.setId(id);nativeMove.setMove(id);nativeMove.setPp(10);nativeMove.setMaxpp(10);nativeMove.setTarget(MoveTarget.any);
            var moveset=new ShowdownMoveset();moveset.setMoves(List.of(nativeMove));
            for(var slot:empty) {
                var choice=new MoveActionResponse(id,slot.getPNX(),null);
                if(!choice.isValid(active,moveset,false)) throw new AssertionError("Native validation no longer accepts reserved slot");
                var repaired=HordeAITargets.correct(active,battle,moveset,false,choice);
                assertLivingEnemy(active,moveset,repaired,id);
            }
            for(int i=0;i<128;i++) assertLivingEnemy(active,moveset,
                    new RandomBattleAI().choose(active,battle,battle.getSide1(),moveset,false),id);
            for(int i=0;i<32;i++) assertLivingEnemy(active,moveset,
                    new StrongBattleAI(1).choose(active,battle,battle.getSide1(),moveset,false),id);
            var living=battle.getSide2().getActivePokemon().stream().filter(p->!p.isGone()&&p.isAlive()).findFirst().orElseThrow();
            var valid=new MoveActionResponse(id,living.getPNX(),null);
            if(HordeAITargets.correct(active,battle,moveset,false,valid)!=valid || !living.getPNX().equals(valid.getTargetPnx())) throw new AssertionError("Valid target was altered");
        }
        LOG.info("MAID_SMOKE CHECK: 6 deterministic empty-slot repairs; 256 random and 64 strong native any-target choices; valid targets preserved");
    }
    private static void assertLivingEnemy(ActiveBattlePokemon active,ShowdownMoveset moveset,ShowdownActionResponse response,String id) {
        if(!(response instanceof MoveActionResponse move) || !id.equals(move.getMoveName()) || !move.isValid(active,moveset,false)) throw new AssertionError("Invalid or changed move "+response);
        var target=battle.getActorAndActiveSlotFromPNX(move.getTargetPnx()).getSecond();
        if(target.isGone() || !target.isAlive() || active.isAllied(target)) throw new AssertionError("Empty or allied AI target "+move.getTargetPnx());
    }
    private static void stop(ServerTickEvent.Post e) {if(battle!=null) {battle.saveBattleLog();if(!battle.getEnded())battle.stop();} ready=false;e.getServer().halt(false);}
    private static ResourceLocation id(String s) {return ResourceLocation.parse(s);}
}
