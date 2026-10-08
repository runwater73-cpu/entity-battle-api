package dev.entitybattle.check;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import dev.entitybattle.api.*;
import dev.entitybattle.battle.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real source callbacks and Horde formats in a disposable server; no release classes. */
@EventBusSubscriber(modid="entitybattle")
public final class SourceBossSmoke {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final AABB AREA=new AABB(-20,90,-20,20,125,20);
    private static final String[] IDS={"aether:slider","aether:valkyrie_queen","aether:sun_spirit","minecraft:wither","minecraft:warden"};
    private static ServerLevel level;
    private static ServerPlayer player;
    private static PokemonBattle battle;
    private static PokemonEntity boss;
    private static Mob source;
    private static UUID sourceId;
    private static Object time;
    private static boolean ready;
    private static final int START_INDEX=Integer.getInteger("entitybattle.test.sourceStart",0);
    private static int ticks,index=START_INDEX,stage,deadline,phaseTick,lootBefore,rewardsBefore;
    @SubscribeEvent public static void start(ServerStartedEvent e){level=e.getServer().overworld();ready=true;}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(!ready)return;
        try{
            ticks++;
            if(ticks==20){
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
                for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)level.setBlock(new BlockPos(x,99,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                for(var entity:level.getEntitiesOfClass(Entity.class,AREA))entity.discard();
                player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SourceBossSmoke"));player.setPos(0,100,3);
                var f=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");f.setAccessible(true);
                ((Map<UUID,ServerPlayer>)f.get(e.getServer().getPlayerList())).put(player.getUUID(),player);
                var party=Cobblemon.INSTANCE.getStorage().getParty(player);for(int i=0;i<6;i++)party.add(PokemonSpecies.getByIdentifier(id("cobblemon:charizard")).create(100));
                guardTests();begin();
            }
            if(battle==null)return;
            if(ticks>deadline)throw new AssertionError("Timeout "+IDS[index]+" stage "+stage);
            int count=index==2?6:index==3?4:1;
            if(ticks%100==0)for(var actor:battle.getActors())LOG.info("SOURCE_BOSS state index={} stage={} turn={} active={} mustChoose={} request={} responses={} dispatch={}",index,stage,battle.getTurn(),actor.getActivePokemon(),actor.getMustChoose(),actor.getRequest(),actor.getResponses(),battle.getDispatches().size());
            if(stage==0&&battle.getSide2().getActivePokemon().stream().filter(p->p.hasPokemon()).count()==count){
                if(battle.getTurn()<2){
                    var actor=(com.cobblemon.mod.common.battles.actor.PlayerBattleActor)battle.getSide1().getActors()[0];
                    if(actor.getMustChoose()&&actor.getRequest()!=null&&actor.getRequest().getActive()!=null&&!actor.getRequest().getActive().isEmpty()){
                        var actions=new ArrayList<com.cobblemon.mod.common.battles.ShowdownActionResponse>();
                        actions.add(new com.cobblemon.mod.common.battles.MoveActionResponse("protect",null,null));
                        com.cobblemon.mod.common.net.serverhandling.battle.BattleSelectActionsHandler.INSTANCE.handle(new com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket(battle.getBattleId(),actions),e.getServer(),player);
                        LOG.info("SOURCE_BOSS submitted {} responses={}",actions,actor.getResponses());
                    }
                    return;
                }
                require(battle.getTurn()>=2,"Actual first round completed without a stalled request");
                var enemies=battle.getSide2().getActors()[0].getPokemonList();
                require(enemies.size()==count,"Real roster "+IDS[index]+" count="+count);
                for(var p:enemies){var bp=p.getOriginalPokemon();require(!bp.heldItem().isEmpty(),"Every opponent has held equipment");require(bp.getMoveSet().getMoves().size()==4,"Four configured moves");}
                if(count>1){
                    String name="entitybattle"+boss.getPokemon().getSpecies().getName().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","");
                    send("const main=battle.sides[1].active.find(p=>p && p.baseSpecies.id==='"+name+"'); if(!main)throw new Error('Missing primary Boss'); main.faint(); battle.faintMessages();");stage=1;phaseTick=ticks;
                }else{battle.stop();stage=2;phaseTick=ticks;}
            }else if(stage==1&&ticks-phaseTick>160){
                require(boss.getPokemon().isFainted(),"Actual main Boss knocked out before followers");
                require(!battle.getEnded(),"Main-only defeat leaves followers fighting");
                LOG.info("SOURCE_BOSS partial actualLoot={} oldLoot={} actualReward={} oldReward={} managed={} pending={} state={}",lootCount(),lootBefore,rewardCount(),rewardsBefore,EntityBossSources.managed(boss.getPokemon()),EntityBossEncounters.awaitingSettlement(boss.getPokemon()),boss.getPokemon().getPersistentData());
                require(lootCount()==lootBefore&&rewardCount()==rewardsBefore,"No premature loot/reward");
                player.setPos(400,100,400);battle.checkFlee();require(battle.getEnded(),"Native flee ends battle");stage=2;phaseTick=ticks;
            }else if(stage==2&&ticks-phaseTick>10){
                require(level.getEntity(sourceId) instanceof Mob m&&m.isAlive(),"Same native source UUID restored");
                source=(Mob)level.getEntity(sourceId);require(!EntityBossEncounters.awaitingSettlement(boss.getPokemon()),"Recovery marker cleared");
                require(lootCount()==lootBefore&&rewardCount()==rewardsBefore,"Abort has no loot/reward");
                player.setPos(0,100,3);
                if(index<3){require(!(Boolean)source.getClass().getMethod("isBossFight").invoke(source),"Native Aether fight reset");qualify(source);}
                startBattle();stage=3;deadline=ticks+950;
            }else if(stage==3&&battle.getSide2().getActivePokemon().stream().filter(p->p.hasPokemon()).count()==count){
                if(battle.getTurn()<2){
                    var actor=(com.cobblemon.mod.common.battles.actor.PlayerBattleActor)battle.getSide1().getActors()[0];
                    if(actor.getMustChoose()&&actor.getRequest()!=null&&actor.getRequest().getActive()!=null&&!actor.getRequest().getActive().isEmpty())
                        com.cobblemon.mod.common.net.serverhandling.battle.BattleSelectActionsHandler.INSTANCE.handle(new com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket(battle.getBattleId(),new ArrayList<>(List.of(new com.cobblemon.mod.common.battles.MoveActionResponse("protect",null,null)))),e.getServer(),player);
                    return;
                }
                if(!battle.getDispatches().isEmpty())return;
                send("battle.sides[1].active.filter(Boolean).forEach(p=>p.faint()); battle.faintMessages(); battle.win(battle.sides[0]);");stage=4;phaseTick=ticks;
            }else if(stage==4&&battle.getEnded()&&ticks-phaseTick>40){
                LOG.info("SOURCE_BOSS settlement data={} actualReward={} previous={} loot={} previousLoot={}",boss.getPokemon().getPersistentData(),rewardCount(),rewardsBefore,lootCount(),lootBefore);
                require(rewardCount()==rewardsBefore+1,"Full victory rewards exactly once "+IDS[index]);
                require(lootCount()>lootBefore,"Native death generates real loot "+IDS[index]);
                if(index<3){String material=index==0?"carved_stone":index==1?"angelic_stone":"hellfire_stone";
                    require(level.getBlockState(new BlockPos(1,100,0)).is(BuiltInRegistries.BLOCK.get(id("aether:"+material))),"Original dungeon callback unlocks blocks "+IDS[index]);
                }
                if(index==2)require(!(Boolean)time.getClass().getMethod("isEternalDay").invoke(time),"Original Sun death ends eternal day");
                if(index==3)require(level.getEntitiesOfClass(ItemEntity.class,AREA).stream().anyMatch(i->i.getItem().is(Items.NETHER_STAR)),"Wither custom death callback drops Nether Star");
                LOG.info("SOURCE_BOSS CASE PASS {}",IDS[index]);
                if(++index==IDS.length){LOG.info("SOURCE_BOSS PASS: {} source conversions; native plot gates, actual rounds, source rollback and once-only native settlement",IDS.length-START_INDEX);ready=false;e.getServer().halt(false);return;}
                begin();
            }
        }catch(Throwable failure){LOG.error("SOURCE_BOSS FAIL",failure);ready=false;if(battle!=null&&!battle.getEnded())battle.stop();e.getServer().halt(false);}
    }
    private static void guardTests()throws Exception{
        for(int i=0;i<3;i++){
            Mob m=mob(IDS[i]);var profile=EntityBattleProfiles.get(m.getType());
            require(EntityBattleProfiles.worldMode(profile)==EntityBattleProfile.WorldMode.NATIVE_MOB,"Story Boss retains native world mode");
            require(EntityBattleSources.denial(m)!=null,"Friendly/unstarted Boss denied "+IDS[i]);
            require(!EntityNativePokemonConversion.convertPermanently(m,profile),"Converter cannot bypass story");
            require(!EntityBattleSessions.start(player,Cobblemon.INSTANCE.getStorage().getParty(player).get(0).getUuid(),m),"R cannot bypass story");
            qualify(m);require(EntityBattleSources.denial(m)==null,"Original story unlocks conversion "+IDS[i]);
            m.discard();
        }
        var w=(WitherBoss)mob("minecraft:wither");w.makeInvulnerable();require(EntityBattleSources.denial(w)!=null,"Wither charge protected");w.setInvulnerableTicks(0);require(EntityBattleSources.denial(w)==null,"Ready Wither allowed");w.discard();
        var ward=mob("minecraft:warden");ward.setPose(Pose.EMERGING);require(EntityBattleSources.denial(ward)!=null,"Warden emergence protected");ward.setPose(Pose.STANDING);require(EntityBattleSources.denial(ward)==null,"Standing Warden allowed");ward.discard();
    }
    private static Mob mob(String id){var m=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id(id)).create(level);m.setPos(0,100,0);m.setNoAi(true);m.setNoGravity(true);level.addFreshEntity(m);return m;}
    private static void qualify(Mob m)throws Exception{
        String name=BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath();
        if(name.equals("slider")){player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));m.hurt(level.damageSources().playerAttack(player),1);}
        else if(name.equals("valkyrie_queen")){
            if(!(Boolean)m.getClass().getMethod("isReady").invoke(m)){
                player.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(id("aether:victory_medal")),10));
                m.getClass().getMethod("handleNpcInteraction",net.minecraft.world.entity.player.Player.class,byte.class).invoke(m,player,(byte)1);
                require((Boolean)m.getClass().getMethod("isReady").invoke(m),"Queen accepts ten native medals");
            }
            require(EntityBattleSources.denial(m)!=null,"Ready Queen still needs first legal hit");m.hurt(level.damageSources().playerAttack(player),1);
        }else if(name.equals("sun_spirit")){
            var interact=m.getClass().getDeclaredMethod("mobInteract",net.minecraft.world.entity.player.Player.class,InteractionHand.class);interact.setAccessible(true);
            for(int i=0;i<12&&EntityBattleSources.denial(m)!=null;i++){m.getClass().getMethod("setChatCooldown",int.class).invoke(m,0);interact.invoke(m,player,InteractionHand.MAIN_HAND);}
        }
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    }
    private static void begin()throws Exception{
        // Separate disposable cases by a normal recall, without editing native battle locks.
        for(var p:Cobblemon.INSTANCE.getStorage().getParty(player))p.recall();
        for(var item:level.getEntitiesOfClass(ItemEntity.class,AREA))item.discard();
        player.setPos(0,100,3);source=mob(IDS[index]);sourceId=source.getUUID();
        if(index<3){
            var type=Class.forName("com.aetherteam.nitrogen.entity.BossRoomTracker");
            Object tracker=type.getConstructor(Mob.class,Vec3.class,AABB.class,List.class).newInstance(source,source.position(),new AABB(-8,99,-8,8,112,8),new ArrayList<>(List.of(player.getUUID())));
            source.getClass().getMethod("setDungeon",type).invoke(source,tracker);
            String material=index==0?"carved_stone":index==1?"angelic_stone":"hellfire_stone";
            level.setBlock(new BlockPos(1,100,0),BuiltInRegistries.BLOCK.get(id("aether:locked_"+material)).defaultBlockState(),3);
            qualify(source);
        }
        if(index==2){
            Object holder=Class.forName("com.aetherteam.aether.attachment.AetherDataAttachments").getField("AETHER_TIME").get(null);
            Object attachment=((java.util.function.Supplier<?>)holder).get();
            time=level.getData((net.neoforged.neoforge.attachment.AttachmentType<Object>)attachment);
            time.getClass().getMethod("setEternalDay",boolean.class).invoke(time,true);
            helpersTest();
        }
        lootBefore=lootCount();rewardsBefore=rewardCount();startBattle();stage=0;deadline=ticks+750;
    }
    private static void helpersTest()throws Exception{
        var crystal=(Projectile)BuiltInRegistries.ENTITY_TYPE.get(id("aether:ice_crystal")).create(level);crystal.setOwner(player);crystal.setPos(5,101,5);level.addFreshEntity(crystal);
        crystal.getClass().getMethod("doDamage",Entity.class).invoke(crystal,source);
        var owned=level.getEntitiesOfClass(Mob.class,AREA,m->m.getPersistentData().hasUUID(EntityBossSources.OWNER)&&m.getPersistentData().getUUID(EntityBossSources.OWNER).equals(sourceId));
        require(!owned.isEmpty(),"Actual Sun damage summon receives ownership tag");
        Mob unrelated=mob("aether:fire_minion");var projectile=(Projectile)BuiltInRegistries.ENTITY_TYPE.get(id("aether:ice_crystal")).create(level);projectile.setOwner(source);level.addFreshEntity(projectile);
        var converted=EntityNativePokemonConversion.sendOut(source,EntityBattleProfiles.get(source.getType()),false);require(converted!=null,"Helper conversion snapshot");
        var data=converted.pokemon().getPersistentData().getCompound("entitybattle:boss_source");
        require(!data.getCompound("NeoForgeData").contains("entitybattle:pokemon"),"Source snapshot excludes recursive Pokemon data");
        EntityBossSources.suspend(source,converted.pokemon());source.discard();
        require(owned.stream().allMatch(Entity::isRemoved)&&projectile.isRemoved(),"Only source-owned minions/projectiles suspended");require(!unrelated.isRemoved(),"Unrelated nearby minion retained");
        require(EntityBossSources.rollback(converted.entity()),"Rollback restores native helpers");source=(Mob)level.getEntity(sourceId);
        require(owned.stream().allMatch(m->level.getEntity(m.getUUID()) instanceof Mob),"Native helper UUIDs restored");qualify(source);crystal.discard();unrelated.discard();
    }
    private static void startBattle(){
        var party=Cobblemon.INSTANCE.getStorage().getParty(player);for(var p:party)p.setCurrentHealth(p.getMaxHealth());
        party.get(0).getMoveSet().clear();party.get(0).getMoveSet().add(com.cobblemon.mod.common.api.moves.Moves.getByName("protect").create());
        LOG.info("SOURCE_BOSS start {} denial={} existingBattle={} sourceAlive={} sourceRemoved={} playerPos={} sourcePos={}",IDS[index],EntityBattleSources.denial(source),BattleRegistry.getBattleByParticipatingPlayer(player),source.isAlive(),source.isRemoved(),player.position(),source.position());
        LOG.info("SOURCE_BOSS lead states={}",party.toBattleTeam(false,false,party.get(0).getUuid()).stream().map(p->"hp="+p.getHealth()+" entity="+p.getEntity()+" busy="+(p.getEntity()==null?null:p.getEntity().getBusyLocks())).toList());
        boolean accepted=EntityBattleSessions.start(player,party.get(0).getUuid(),source);
        if(!accepted){
            var diagnostic=EntityNativePokemonConversion.sendOut(source,EntityBattleProfiles.get(source.getType()));
            LOG.info("SOURCE_BOSS diagnostic converted={} mode={}",diagnostic,EntityBattleProfiles.worldMode(EntityBattleProfiles.get(source.getType())));
            if(diagnostic!=null){var result=BattleBuilder.INSTANCE.pve(player,diagnostic.entity(),party.get(0).getUuid());
                if(result instanceof ErroredBattleStart error)LOG.info("SOURCE_BOSS diagnostic errors general={} participant={}",error.getGeneralErrors(),error.getParticipantErrors());
            }
        }
        require(accepted,"Native R conversion starts "+IDS[index]);
        battle=BattleRegistry.getBattleByParticipatingPlayer(player);
        boss=battle.getSide2().getActors()[0].getPokemonList().stream().filter(p->EntityBattleProfiles.get(id(IDS[index])).species().equals(p.getOriginalPokemon().getSpecies().getResourceIdentifier())).findFirst().orElseThrow().getEntity();
        require(source.isRemoved(),"Native source removed after accepted battle");
    }
    private static int lootCount(){return level.getEntitiesOfClass(ItemEntity.class,AREA).stream().mapToInt(i->i.getItem().getCount()).sum();}
    private static int rewardCount(){int count=0;var species=EntityBattleProfiles.get(id(IDS[index])).species();for(var p:Cobblemon.INSTANCE.getStorage().getParty(player))if(p.getSpecies().getResourceIdentifier().equals(species))count++;for(var p:Cobblemon.INSTANCE.getStorage().getPC(player))if(p.getSpecies().getResourceIdentifier().equals(species))count++;
        for(int i=0;i<player.getInventory().getContainerSize();i++){var item=player.getInventory().getItem(i);var data=item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(data!=null&&species.toString().equals(data.copyTag().getString("species"))&&data.copyTag().getInt("level")==1)count+=item.getCount();}
        return count;}
    private static void send(String script){ShowdownService.Companion.getService().send(battle.getBattleId(),new String[]{">eval "+script});}
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private static void require(boolean condition,String label){if(!condition)throw new AssertionError(label);LOG.info("SOURCE_BOSS CHECK {}",label);}
}
