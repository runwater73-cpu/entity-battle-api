package dev.entitybattle.check;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonServerDelegate;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.entitybattle.EntityBattleConfig;
import dev.entitybattle.api.*;
import dev.entitybattle.battle.*;
import java.util.*;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated integration checks. This directory is excluded from release builds. */
@EventBusSubscriber(modid="entitybattle")
public final class TwilightSmoke {
    private static final org.slf4j.Logger LOG=LogUtils.getLogger();
    private static final List<String> BOSSES=List.of("naga","lich","minoshroom","hydra","ur_ghast","alpha_yeti","snow_queen");
    private static boolean ready,dropChecked,victorySent;
    private static int ticks,index=-1,deadline,stage;
    private static ServerPlayer player;
    private static PokemonBattle battle;
    private static PokemonEntity boss;
    private static List<PokemonEntity> followers=List.of();
    private static List<Mob> originals;
    private static Set<UUID> originalIds;
    private static int inventoryBefore;
    private static BlockPos home;
    private static Mob otherRoom;
    @SubscribeEvent public static void start(ServerStartedEvent event){ready=true;}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(!ready)return;
        try{
            ticks++;
            var level=e.getServer().overworld();
            if(ticks==20){
                level.setChunkForced(0,0,true);
                for(int x=-16;x<=20;x++)for(int z=-12;z<=20;z++)
                    level.setBlock(new BlockPos(x,99,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                require(EntityBattleConfig.BOSS_WORLD_MODE.get()==EntityBattleProfile.WorldMode.NATIVE_MOB,"Isolated server started in native Mob mode");
                player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"TwilightSmoke"));
                player.setPos(0,100,5);
                var byUUID=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
                byUUID.setAccessible(true);
                ((Map<UUID,ServerPlayer>)byUUID.get(e.getServer().getPlayerList())).put(player.getUUID(),player);
                var party=Cobblemon.INSTANCE.getStorage().getParty(player);
                for(int i=0;i<6;i++){
                    var p=PokemonSpecies.getByIdentifier(id("cobblemon:charizard")).create(100);
                    p.getMoveSet().clear();
                    for(String move:List.of("protect","scaryface","softboiled","nightshade"))
                        p.getMoveSet().add(com.cobblemon.mod.common.api.moves.Moves.getByName(move).create());
                    party.add(p);
                }
                party.get(0).sendOut(level,new net.minecraft.world.phys.Vec3(0,100,3),null,s->kotlin.Unit.INSTANCE);
                next(e);
            }
            if(battle==null)return;
            if(ticks>deadline)throw new AssertionError("Timed out stage="+stage+" index="+index+" turn="+battle.getTurn());
            if(stage==0 && battle.getSide2().getActivePokemon().stream().anyMatch(p->p.getBattlePokemon()!=null)){
                require(battle.getSide2().getActors()[0].getClass().getName().contains("HordeBattleActor"),"Native Horde actor "+BOSSES.get(index));
                require(battle.getSide2().getActors()[0].getPokemonList().size()==3,"Three opponents "+BOSSES.get(index));
                var profile=EntityBattleProfiles.get(id("twilightforest:"+BOSSES.get(index)));
                followers=battle.getSide2().getActors()[0].getPokemonList().stream().map(p->p.getEntity()).filter(p->p!=boss).toList();
                for(var f:followers){
                    require(f.getPokemon().isUncatchable(),"Follower not catchable");
                    require(BuiltInRegistries.ITEM.getKey(f.getPokemon().heldItem().getItem()).equals(profile.bossBattle().minionHeldItem()),"Configured follower equipment "+profile.bossBattle().minionHeldItem());
                    require(f.getPokemon().getLevel()==boss.getPokemon().getLevel()+profile.bossBattle().levelOffset(),"Follower level offset");
                    require(f.getPokemon().getMoveSet().getMoves().size()==4,"Follower learned four moves");
                }
                require(boss.getPokemon().getMoveSet().getMoves().size()==4,"Boss learned four moves");
                verifyDrop(boss,false);
                for(var f:followers)verifyDrop(f,false);
                if(!dropChecked){
                    var normal=PokemonSpecies.getByIdentifier(id("cobblemon:charizard")).create(35).sendOut(level,new net.minecraft.world.phys.Vec3(10,100,10),null,s->kotlin.Unit.INSTANCE);
                    normal.getPokemon().swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(id("cobblemon:leftovers"))),false,false);
                    verifyDrop(normal,true);normal.getPokemon().recall();dropChecked=true;
                }
                battle.stop();stage=1;deadline=ticks+50;
            }else if(stage==1 && battle.getEnded() && ticks%10==0){
                require(followers.stream().allMatch(PokemonEntity::isRemoved),"Temporary followers removed");
                boss.getPokemon().recall();battle=null;next(e);
            }else if(stage==2){
                if(battle.getEnded())throw new AssertionError("Six-knight battle ended during normal turn test");
                var actor=(PlayerBattleActor)battle.getSide1().getActors()[0];
                if(battle.getTurn()>=3){battle.stop();stage=3;deadline=ticks+100;return;}
                if(actor.getMustChoose() && actor.getRequest()!=null && actor.getRequest().getActive()!=null){
                    var target=battle.getSide2().getActivePokemon().stream().filter(p->p.hasPokemon()&&p.isAlive()).findFirst().orElse(null);
                    if(target!=null){
                        var party=Cobblemon.INSTANCE.getStorage().getParty(player);
                        List<ShowdownActionResponse> actions=List.of(battle.getTurn()==2
                                ?new SwitchActionResponse(party.get(1).getUuid())
                                :new MoveActionResponse("protect",null,null));
                        com.cobblemon.mod.common.net.serverhandling.battle.BattleSelectActionsHandler.INSTANCE.handle(
                                new com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket(battle.getBattleId(),actions),e.getServer(),player);
                    }
                }
            }else if(stage==3 && ticks%10==0){
                require(originalIds.stream().allMatch(uuid->level.getEntity(uuid) instanceof Mob mob&&mob.isAlive()),"All six original UUIDs restored after interrupted battle");
                originals=originalIds.stream().map(uuid->(Mob)level.getEntity(uuid)).toList();
                require(EntityBattleSessions.start(player,Cobblemon.INSTANCE.getStorage().getParty(player).get(1).getUuid(),originals.get(0)),"Re-enter original squad");
                battle=BattleRegistry.getBattleByParticipatingPlayer(player);stage=4;deadline=ticks+1300;
            }else if(stage==4 && !victorySent && battle.getSide2().getActivePokemon().stream().filter(p->p.hasPokemon()).count()==6){
                require(battle.getSide2().getActors()[0].getPokemonList().size()==6,"All original six knight opponents");
                require(battle.getSide2().getActors()[0].getPokemonList().stream().map(p->p.getOriginalPokemon().getLevel()).distinct().count()==1,"Squad common level");
                long trophy=battle.getSide2().getActors()[0].getPokemonList().stream().filter(p->BuiltInRegistries.ITEM.getKey(p.getOriginalPokemon().heldItem().getItem()).equals(id("twilightforest:knight_phantom_trophy"))).count();
                require(trophy==1,"Only representative carries trophy");
                for(var p:battle.getSide2().getActors()[0].getPokemonList()){
                    require(!p.getOriginalPokemon().heldItem().isEmpty(),"Every knight equipped");verifyDrop(p.getEntity(),false);
                }
                inventoryBefore=rewardCount();
                com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService().send(battle.getBattleId(),new String[]{">eval battle.sides[1].active.filter(Boolean).forEach(p=>p.faint()); battle.faintMessages(); battle.win(battle.sides[0]);"});
                victorySent=true;
            }else if(stage==4 && victorySent && battle.getEnded() && ticks%10==0){
                require(rewardCount()==inventoryBefore+1,"One level-one squad reward in native storage");
                require(originalIds.stream().allMatch(uuid->level.getEntity(uuid)==null||((Mob)level.getEntity(uuid)).isDeadOrDying()),"Originals executed native death");
                require(otherRoom.isAlive()&&!otherRoom.isRemoved(),"Neighboring room knight survives our squad victory");
                stage=5;deadline=ticks+300;
            }else if(stage==5 && ticks%10==0 && originalIds.stream().allMatch(uuid->level.getEntity(uuid)==null)){
                require(level.getBlockState(home).getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                        ||level.getBlockState(home.below()).getBlock() instanceof net.minecraft.world.level.block.ChestBlock,"Native collective death created original treasure chest");
                LOG.info("TWILIGHT_SMOKE PASS: seven 1+2 encounters, native equipment suppression/control, six original knights, normal turns/switch, rollback, once-only reward and native chest");
                ready=false;e.getServer().halt(false);
            }
        }catch(Throwable t){LOG.error("TWILIGHT_SMOKE FAIL",t);ready=false;if(battle!=null){battle.saveBattleLog();if(!battle.getEnded())battle.stop();}e.getServer().halt(false);}
    }
    private static void next(ServerTickEvent.Post e)throws Exception{
        index++;var level=e.getServer().overworld();
        var party=Cobblemon.INSTANCE.getStorage().getParty(player);
        for(var p:party)p.setCurrentHealth(p.getMaxHealth());
        if(index<BOSSES.size()){
            var profile=EntityBattleProfiles.get(id("twilightforest:"+BOSSES.get(index)));
            require(profile!=null,"Profile loaded "+BOSSES.get(index));
            var source=(Mob)BuiltInRegistries.ENTITY_TYPE.get(profile.entity()).create(level);source.setPos(0,100,0);
            boss=EntityNativePokemonConversion.sendOut(source,profile,false).entity();
            var result=BattleBuilder.INSTANCE.pve(player,boss,party.get(0).getUuid());
            require(result instanceof SuccessfulBattleStart,"pve "+BOSSES.get(index));
            battle=((SuccessfulBattleStart)result).getBattle();stage=0;deadline=ticks+400;
        }else{
            originals=new ArrayList<>();
            home=new BlockPos(7,100,Math.floorMod(System.nanoTime(),50));
            // Prior diagnostic runs can leave original knights in this isolated fixture.
            for(var mob:level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(-64,-64,-64,64,320,64))) {
                if(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).equals(id("twilightforest:knight_phantom")))mob.discard();
            }
            for(int i=0;i<6;i++){
                Mob source=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("twilightforest:knight_phantom")).create(level);
                source.getClass().getMethod("setNumber",int.class).invoke(source,i);
                source.setPos(i,100,0);source.setNoAi(true);source.setNoGravity(true);require(level.addFreshEntity(source),"Knight source inserted");originals.add(source);
                // TF's join event initializes a structure home; the diagnostic room is assigned afterwards.
                source.getClass().getMethod("setRestrictionPoint",GlobalPos.class).invoke(source,GlobalPos.of(level.dimension(),home));
                if(i==4){
                    require(!EntityBattleSessions.start(player,party.get(0).getUuid(),originals.get(0)),"Incomplete five-member room rejects battle");
                    require(originals.stream().allMatch(m->m.isAlive()&&!m.isRemoved()),"Rejected start preserves all original sources");
                }
            }
            otherRoom=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("twilightforest:knight_phantom")).create(level);
            otherRoom.setPos(8,100,3);otherRoom.setNoAi(true);otherRoom.setNoGravity(true);level.addFreshEntity(otherRoom);
            otherRoom.getClass().getMethod("setRestrictionPoint",GlobalPos.class).invoke(otherRoom,GlobalPos.of(level.dimension(),home.offset(1,0,0)));
            originalIds=new HashSet<>(originals.stream().map(Mob::getUUID).toList());
            LOG.info("TWILIGHT_SMOKE native entry mode={} alive={} existingBattle={}",EntityBattleProfiles.worldMode(EntityBattleProfiles.get(originals.get(0).getType())),originals.get(0).isAlive(),BattleRegistry.getBattleByParticipatingPlayer(player));
            require(EntityBattleSessions.start(player,party.get(0).getUuid(),originals.get(0)),"Native R entry accepts complete original squad");
            battle=BattleRegistry.getBattleByParticipatingPlayer(player);stage=2;deadline=ticks+1400;
        }
    }
    private static int rewardCount(){
        var party=Cobblemon.INSTANCE.getStorage().getParty(player);var pc=Cobblemon.INSTANCE.getStorage().getPC(player);int count=0;
        for(var p:party)if(p.getSpecies().getResourceIdentifier().equals(id("entitybattle:twilightforest_knight_phantom")))count++;
        for(var p:pc)if(p.getSpecies().getResourceIdentifier().equals(id("entitybattle:twilightforest_knight_phantom")))count++;
        return count;
    }
    private static void verifyDrop(PokemonEntity entity,boolean expected){
        var level=(net.minecraft.server.level.ServerLevel)entity.level();var held=entity.getPokemon().heldItem();
        require(!held.isEmpty(),"Actual held stack exists before death-drop test");
        var area=entity.getBoundingBox().inflate(32);
        int before=level.getEntitiesOfClass(ItemEntity.class,area,e->e.getItem().is(held.getItem())).size();
        ((PokemonServerDelegate)entity.getDelegate()).doDeathDrops();
        int after=level.getEntitiesOfClass(ItemEntity.class,area,e->e.getItem().is(held.getItem())).size();
        require(after-before==(expected?1:0),"Automatic held drop="+expected+" for "+entity.getPokemon().getSpecies().getName());
        require(!entity.getPokemon().heldItem().isEmpty(),"Equipment remains equipped");
    }
    private static ResourceLocation id(String s){return ResourceLocation.parse(s);}
    private static void require(boolean ok,String s){if(!ok)throw new AssertionError(s);LOG.info("TWILIGHT_SMOKE CHECK: {}",s);}
}
