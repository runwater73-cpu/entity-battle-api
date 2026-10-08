package dev.entitybattle.check;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import dev.entitybattle.api.*;
import dev.entitybattle.battle.*;
import dev.entitybattle.compat.DeepAetherBossSource;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native story callbacks, automatic conversion and settlement in an isolated server. */
@EventBusSubscriber(modid="entitybattle")
public final class DeepAetherBossSmoke {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final AABB AREA=new AABB(-20,85,-20,20,125,20);
    private static final String[] STORIES={"aether:slider","aether:valkyrie_queen","aether:sun_spirit","deep_aether:eots_controller"};
    private static ServerLevel level;
    private static ServerPlayer player;
    private static Mob source;
    private static UUID sourceId;
    private static PokemonEntity boss;
    private static PokemonBattle battle;
    private static boolean ready;
    private static int ticks,stage,since,caseIndex,deadline;
    private static Mob unrelated;
    private static Entity unrelatedPart;
    private static List<Entity> nativeParts=List.of();
    @SubscribeEvent public static void start(ServerStartedEvent event){level=event.getServer().overworld();ready=true;}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!ready)return;
        try {
            ticks++;
            if(ticks==20){
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
                for(var entity:level.getEntitiesOfClass(Entity.class,AREA))entity.discard();
                for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)level.setBlock(new BlockPos(x,99,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
                player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"DeepAetherSmoke"));player.setPos(4,106,7);
                var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);
                ((Map<UUID,ServerPlayer>)field.get(event.getServer().getPlayerList())).put(player.getUUID(),player);
                var p=PokemonSpecies.getByIdentifier(id("cobblemon:charizard")).create(100);
                p.getMoveSet().clear();p.getMoveSet().add(com.cobblemon.mod.common.api.moves.Moves.getByName("protect").create());
                Cobblemon.INSTANCE.getStorage().getParty(player).add(p);
                stage=0;
            }
            if(ticks==40){
                source=mob(STORIES[0]);sourceId=source.getUUID();stage=1;since=ticks;
            }
            if(stage==1&&ticks-since>=3){
                require(source.isAlive()&&!source.isRemoved()&&find(sourceId)==null,"Automatic mode waits for native story "+STORIES[caseIndex]);
                require(EntityBattleProfiles.worldMode(EntityBattleProfiles.get(source.getType()))==EntityBattleProfile.WorldMode.POKEMON_ENTITY,"Story source uses automatic Boss strategy");
                require(EntityBattleSources.denial(source)!=null,"Unstarted source is ineligible");
                require(!EntityNativePokemonConversion.convertPermanently(source,EntityBattleProfiles.get(source.getType())),"Converter cannot bypass story");
                require(!EntityBattleSessions.start(player,Cobblemon.INSTANCE.getStorage().getParty(player).get(0).getUuid(),source),"R cannot bypass story");
                if(caseIndex==3){
                    dungeon(source);
                    // A real dungeon tracks the participating player before awakening. Without
                    // that callback the source deliberately creates four fewer segments.
                    source.getClass().getMethod("onDungeonPlayerAdded",Player.class).invoke(source,player);
                    level.setBlock(new BlockPos(1,100,0),BuiltInRegistries.BLOCK.get(id("deep_aether:locked_nimbus_stone")).defaultBlockState(),3);
                    unrelated=mob(STORIES[3]);unrelated.setPos(12,106,12);
                    unrelatedPart=BuiltInRegistries.ENTITY_TYPE.get(DeepAetherBossSource.SEGMENT).create(level);
                    unrelatedPart.getClass().getMethod("setController",source.getClass()).invoke(unrelatedPart,unrelated);
                    unrelatedPart.setPos(12,105,12);((Mob)unrelatedPart).setNoAi(true);level.addFreshEntity(unrelatedPart);
                }
                qualify(source);
                require(EntityBattleSources.denial(source)==null,"Original story unlocks automatic conversion");
                if(caseIndex==3){
                    var parts=ownedParts(source);nativeParts=List.copyOf(parts);require(parts.size()>=22,"Original awakening created full segmented body");
                    require(parts.stream().allMatch(p->EntityBattleSources.resolve((Mob)p)==source),"Body interactions resolve to one controller");
                    // Stop native combat simulation in this fixture; production removes these next tick.
                    parts.forEach(p->((Mob)p).setNoAi(true));
                }
                stage=2;since=ticks;
            }else if(stage==2&&ticks-since>=2&&(source.isRemoved()||ticks-since>300)){
                if(!source.isRemoved()) {
                    var diagnostic=EntityPokemonData.getOrCreate(source,EntityBattleProfiles.get(source.getType()));
                    LOG.info("DEEP_AETHER_BOSS conversion diagnostic sourceAlive={} removed={} registered={} denial={} pokemon={} hp={} implemented={}",source.isAlive(),source.isRemoved(),level.getEntity(source.getUUID()),EntityBattleSources.denial(source),diagnostic,diagnostic==null?null:diagnostic.getCurrentHealth(),diagnostic==null?null:diagnostic.getSpecies().getImplemented());
                    if(diagnostic!=null){var sent=diagnostic.sendOut(level,source.position(),null,e->kotlin.Unit.INSTANCE);LOG.info("DEEP_AETHER_BOSS direct sendOut result={} world={} state={}",sent,sent==null?null:level.getEntity(sent.getUUID()),diagnostic.getState());}
                }
                boss=find(sourceId);require(source.isRemoved()&&boss!=null,"Unlocked Boss automatically becomes PokemonEntity "+STORIES[caseIndex]);
                boss.setNoAi(true);
                require(EntityNativePokemonConversion.isPermanent(boss.getPokemon())&&EntityBossSources.managed(boss.getPokemon()),"Permanent Pokemon retains source callback NBT");
                if(caseIndex<3){boss.getPokemon().recall();boss.discard();caseIndex++;source=mob(STORIES[caseIndex]);sourceId=source.getUUID();stage=1;since=ticks;}
                else {
                    require(nativeParts.stream().allMatch(Entity::isRemoved)&&!unrelatedPart.isRemoved(),"Only the converted controller's native body is removed");
                    require(boss.getPokemon().getLevel()>=63&&boss.getPokemon().getLevel()<=67&&boss.getPokemon().getMoveSet().getMoves().size()==4,"Level 65 +/- 2 and four native moves");
                    require(boss.getPokemon().getAbility().getName().equals("berserk"),"Native Berserk ability");
                    var snapshot=boss.getPokemon().getPersistentData().getCompound("entitybattle:boss_source");
                    require(snapshot.contains("Dungeon")&&snapshot.getBoolean("Awake")&&snapshot.getBoolean("BossFight"),"Original dungeon and awake story NBT retained");
                    require(boss.getPokemon().getPersistentData().getList("entitybattle:source_helpers",10).isEmpty(),"Native segments are not replayed as independent helpers");
                    startBattle();
                    require(BuiltInRegistries.ITEM.getKey(boss.getPokemon().heldItem().getItem()).equals(id("cobblemon:sharp_beak")),"Real Sharp Beak equipment is assigned at battle preparation");
                    stage=3;deadline=ticks+1000;
                }
            }else if(stage==3&&battle.getTurn()>=2&&battle.getDispatches().isEmpty()){
                require(battle.getSide2().getActors()[0].getPokemonList().size()==1,"One actual Boss opponent, body is visual only");
                battle.stop();stage=4;since=ticks;
            }else if(stage==4&&ticks-since>60){
                require(level.getEntity(sourceId)==null&&boss.isAlive()&&!boss.isRemoved(),"Abort preserves the automatic Pokemon Boss");
                require(loot()==0&&rewards()==0&&locked(),"Abort has no key, reward or dungeon unlock");
                for(var p:Cobblemon.INSTANCE.getStorage().getParty(player))p.recall();
                startBattle();stage=5;deadline=ticks+1000;
            }else if(stage==5&&battle.getTurn()>=2&&battle.getDispatches().isEmpty()){
                ShowdownService.Companion.getService().send(battle.getBattleId(),new String[]{">eval battle.sides[1].active[0].faint(); battle.faintMessages(); battle.win(battle.sides[0]);"});
                stage=6;since=ticks;
            }else if(stage==6&&battle.getEnded()&&ticks-since>80){
                require(loot()==1&&rewards()==1&&!locked(),"Native victory gives one brass key, one reward and original dungeon unlock");
                require(level.getEntitiesOfClass(ItemEntity.class,AREA).stream().noneMatch(i->i.getItem().is(BuiltInRegistries.ITEM.get(id("cobblemon:sharp_beak")))),"Combat equipment does not drop");
                require(!unrelatedPart.isRemoved(),"Unrelated original body remains after settlement");
                // A source without a plot requirement should convert as soon as it spawns.
                source=mob("kaleidoscope_twilight:umbral_sunflower");sourceId=source.getUUID();stage=7;since=ticks;
            }else if(stage==7&&ticks-since>=3){
                boss=find(sourceId);require(source.isRemoved()&&boss!=null&&EntityNativePokemonConversion.isPermanent(boss.getPokemon()),"Sunflower follows automatic Boss world mode");
                source=mob("twilightforest:quest_ram");source.setNoAi(false);sourceId=source.getUUID();stage=8;since=ticks;
                var profile=EntityBattleProfiles.get(source.getType());
                require(!profile.boss()&&profile.catchable()&&EntityBattleProfiles.worldMode(profile)==EntityBattleProfile.WorldMode.NATIVE_MOB&&!source.isNoAi(),"Quest Ram remains an ordinary catchable native Mob with real AI");
                require(EntityBattleSources.denial(source)!=null&&!EntityNativePokemonConversion.convertPermanently(source,profile),"Quest Ram cannot bypass its wool task");
                var accept=source.getClass().getMethod("tryAccept",ItemStack.class);
                for(var color:net.minecraft.world.item.DyeColor.values())require((Boolean)accept.invoke(source,new ItemStack(BuiltInRegistries.ITEM.get(id("minecraft:"+color.getName()+"_wool")))),"Original Quest Ram accepts "+color.getName()+" wool");
            }else if(stage==8){
                if((Boolean)source.getClass().getMethod("getRewarded").invoke(source)){
                    require(!source.isRemoved()&&!source.isNoAi()&&find(sourceId)==null,"Task completion keeps native Ram and AI until the player chooses conversion");
                    var profile=EntityBattleProfiles.get(source.getType());
                    require(EntityBattleSources.denial(source)==null&&EntityNativePokemonConversion.convertPermanently(source,profile),"Original task reward unlocks voluntary conversion");
                    var p=find(sourceId).getPokemon();
                    require(!p.isUncatchable()&&!profile.boss(),"Converted Ram is eligible for ordinary Cobblemon capture and has no Boss reward");
                    var display=(Mob)source.getType().create(level);EntityPokemonOrigin.restoreAppearance(p,display);
                    require((Integer)display.getClass().getMethod("countColorsSet").invoke(display)==16,"All collected wool colours survive conversion display");
                    LOG.info("DEEP_AETHER_BOSS PASS: four original story gates, automatic Boss conversion, body ownership, actual singles rounds, abort, original key and dungeon callback; sunflower auto; Quest Ram AI, original task and capture policy");
                    ready=false;event.getServer().halt(false);
                }else if(ticks-since>700)throw new AssertionError("Native Quest Ram reward timeout; source="+level.getEntity(sourceId));
            }
            if(battle!=null&&!battle.getEnded()&&(stage==3||stage==5)){
                if(ticks>deadline)throw new AssertionError("Battle timeout stage="+stage);
                var actor=battle.getSide1().getActors()[0];
                if(actor.getRequest()!=null&&actor.getMustChoose()&&actor.getRequest().getActive()!=null&&!actor.getRequest().getActive().isEmpty())
                    com.cobblemon.mod.common.net.serverhandling.battle.BattleSelectActionsHandler.INSTANCE.handle(
                        new com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket(battle.getBattleId(),new ArrayList<>(List.of(new com.cobblemon.mod.common.battles.MoveActionResponse("protect",null,null)))),event.getServer(),player);
            }
            if(ticks>3500)throw new AssertionError("Server timeout stage="+stage);
        }catch(Throwable failure){LOG.error("DEEP_AETHER_BOSS FAIL",failure);ready=false;if(battle!=null&&!battle.getEnded())battle.stop();event.getServer().halt(false);}
    }
    private static void qualify(Mob mob)throws Exception{
        if(caseIndex==0){
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);mob.hurt(level.damageSources().playerAttack(player),1);
            require(EntityBattleSources.denial(mob)!=null,"Wrong tool does not awaken Slider");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));mob.hurt(level.damageSources().playerAttack(player),1);
        }else if(caseIndex==1){
            var method=mob.getClass().getMethod("handleNpcInteraction",Player.class,byte.class);
            method.invoke(mob,player,(byte)1);require(!(Boolean)mob.getClass().getMethod("isReady").invoke(mob),"Queen denies challenge without medals");
            var medal=BuiltInRegistries.ITEM.get(id("aether:victory_medal"));player.getInventory().add(new ItemStack(medal,10));
            method.invoke(mob,player,(byte)1);require((Boolean)mob.getClass().getMethod("isReady").invoke(mob)&&player.getInventory().countItem(medal)==0,"Queen's original dialogue consumes ten medals");
            require(EntityBattleSources.denial(mob)!=null,"Paid Queen retains original attack confirmation");mob.hurt(level.damageSources().playerAttack(player),1);
        }else if(caseIndex==2){
            var interact=mob.getClass().getDeclaredMethod("mobInteract",Player.class,InteractionHand.class);interact.setAccessible(true);
            for(int i=0;i<12&&EntityBattleSources.denial(mob)!=null;i++){mob.getClass().getMethod("setChatCooldown",int.class).invoke(mob,0);interact.invoke(mob,player,InteractionHand.MAIN_HAND);}
        }else mob.hurt(level.damageSources().playerAttack(player),1);
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    }
    private static Mob mob(String id){
        var m=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id(id)).create(level);require(m!=null,"Installed source "+id);
        // Keep the sample away from section borders while forced chunks finish loading.
        m.setPos(4,106,4);m.setNoAi(true);m.setNoGravity(true);require(level.addFreshEntity(m),"Source spawn accepted");return m;
    }
    private static void dungeon(Mob mob)throws Exception{
        var type=Class.forName("com.aetherteam.nitrogen.entity.BossRoomTracker");
        Object tracker=type.getConstructor(Mob.class,Vec3.class,AABB.class,List.class).newInstance(mob,mob.position(),new AABB(-8,98,-8,8,119,8),new ArrayList<>(List.of(player.getUUID())));
        mob.getClass().getMethod("setDungeon",type).invoke(mob,tracker);
    }
    private static List<Entity> ownedParts(Mob owner){
        var parts=new ArrayList<Entity>();for(var e:level.getAllEntities())if(e instanceof Mob m&&m.getType()==BuiltInRegistries.ENTITY_TYPE.get(DeepAetherBossSource.SEGMENT)&&EntityBattleSources.resolve(m)==owner)parts.add(e);return parts;
    }
    private static PokemonEntity find(UUID id){return level.getEntitiesOfClass(PokemonEntity.class,AREA,e->EntityPokemonOrigin.sourceUuid(e.getPokemon()).filter(id::equals).isPresent()).stream().findFirst().orElse(null);}
    private static void startBattle(){
        var p=Cobblemon.INSTANCE.getStorage().getParty(player).get(0);p.setCurrentHealth(p.getMaxHealth());
        player.setPos(boss.getX(),boss.getY(),boss.getZ()+3);
        var result=BattleBuilder.INSTANCE.pve(player,boss,p.getUuid());require(result instanceof SuccessfulBattleStart,"Official PokemonEntity wild battle starts");
        battle=((SuccessfulBattleStart)result).getBattle();
    }
    private static boolean locked(){return level.getBlockState(new BlockPos(1,100,0)).is(BuiltInRegistries.BLOCK.get(id("deep_aether:locked_nimbus_stone")));}
    private static int loot(){return level.getEntitiesOfClass(ItemEntity.class,AREA).stream().filter(i->i.getItem().is(BuiltInRegistries.ITEM.get(id("deep_aether:brass_dungeon_key")))).mapToInt(i->i.getItem().getCount()).sum();}
    private static int rewards(){int count=0;var species=EntityBattleProfiles.get(DeepAetherBossSource.CONTROLLER).species();for(var p:Cobblemon.INSTANCE.getStorage().getParty(player))if(p.getLevel()==1&&p.getSpecies().getResourceIdentifier().equals(species))count++;for(var p:Cobblemon.INSTANCE.getStorage().getPC(player))if(p.getLevel()==1&&p.getSpecies().getResourceIdentifier().equals(species))count++;return count;}
    private static ResourceLocation id(String s){return ResourceLocation.parse(s);}
    private static void require(boolean c,String label){if(!c)throw new AssertionError(label);LOG.info("DEEP_AETHER_BOSS CHECK {}",label);}
}
