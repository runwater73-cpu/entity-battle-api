package dev.entitybattle.check;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.render.item.PokemonItemRenderer;
import com.cobblemon.mod.common.item.PokemonItem;
import com.mojang.blaze3d.vertex.*;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.client.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="entitybattle",value=Dist.CLIENT)
public final class ItemIconSmoke {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static boolean opened,prepared,finished;
    private static int ticks,frames;
    private record Sample(String source,ItemStack item){}
    private static final List<Sample> SAMPLES=new ArrayList<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var client=Minecraft.getInstance();if(finished)return;
        try{
            if(!opened&&client.screen instanceof TitleScreen){opened=true;client.createWorldOpenFlows().openWorld("multipart-smoke-world",()->{throw new AssertionError("World load");});}
            if(client.screen instanceof BackupConfirmScreen backup){var f=backup.getClass().getDeclaredField("onProceed");f.setAccessible(true);((BackupConfirmScreen.Listener)f.get(backup)).proceed(false,false);}
            if(client.level==null){if(++ticks>2800)throw new AssertionError("Timeout");return;}
            if(!prepared){prepared=true;EntityBattleClientProfiles.replace(EntityBattleProfiles.entityIds(),EntityBattleProfiles.visualSpeciesMap(),false);
                checkPoses();
                for(String source:List.of("minecraft:cow","minecraft:zombie","minecraft:slime","twilightforest:naga","twilightforest:hydra","twilightforest:lich","aether:slider","aether:valkyrie_queen","aether:sun_spirit","minecraft:wither","minecraft:warden","kaleidoscope_twilight:umbral_sunflower","deep_aether:eots_controller")){
                    var species=PokemonSpecies.getByIdentifier(EntityBattleProfiles.get(ResourceLocation.parse(source)).species());
                    SAMPLES.add(new Sample(source,PokemonItem.from(species.create(60))));
                }
                client.setScreen(new Preview());
            }
        }catch(Throwable failure){LOG.error("ITEM_ICONS FAIL",failure);finished=true;client.stop();}
    }
    private static void checkPoses() throws Exception {
        var level=Minecraft.getInstance().level;
        for(String source:List.of("aether:slider","aether:sun_spirit","minecraft:wither","minecraft:warden")){
            var profile=EntityBattleProfiles.get(ResourceLocation.parse(source));
            var p=PokemonSpecies.getByIdentifier(profile.species()).create(60);
            var e=new com.cobblemon.mod.common.entity.pokemon.PokemonEntity(level,p,com.cobblemon.mod.common.CobblemonEntities.POKEMON);
            var visual=(net.minecraft.world.entity.Mob)net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(source)).create(level);
            dev.entitybattle.api.EntityPokemonOrigin.bind(p,visual);
            int phase=source.equals("aether:sun_spirit")?3:2;
            TwilightBossPoses.receive(e.getUUID(),source.equals("minecraft:warden")?"boomburst":"",phase);
            TwilightBossPoses.apply(e,visual);
            if(source.equals("aether:slider")&&!(Boolean)visual.getClass().getMethod("isAwake").invoke(visual))throw new AssertionError("Slider awake pose");
            if(source.equals("aether:sun_spirit")&&!(Boolean)visual.getClass().getMethod("isFrozen").invoke(visual))throw new AssertionError("Sun cold texture");
            if(visual instanceof net.minecraft.world.entity.boss.wither.WitherBoss w&&!w.isPowered())throw new AssertionError("Wither armour state");
            if(visual instanceof net.minecraft.world.entity.monster.warden.Warden w){
                if(!w.sonicBoomAnimationState.isStarted())throw new AssertionError("Native sonic animation");
                TwilightBossPoses.receive(e.getUUID(),"hammerarm",-1);TwilightBossPoses.apply(e,visual);
                if(!w.attackAnimationState.isStarted()||w.sonicBoomAnimationState.isStarted())throw new AssertionError("Native melee animation");
            }
            TwilightBossPoses.receive(e.getUUID(),"",0);TwilightBossPoses.apply(e,visual);
            if(source.equals("aether:sun_spirit")&&(Boolean)visual.getClass().getMethod("isFrozen").invoke(visual))throw new AssertionError("Cold phase reset");
            if(visual instanceof net.minecraft.world.entity.boss.wither.WitherBoss w&&w.isPowered())throw new AssertionError("Armour phase reset");
            if(visual instanceof net.minecraft.world.entity.monster.warden.Warden w&&(w.attackAnimationState.isStarted()||w.sonicBoomAnimationState.isStarted()))throw new AssertionError("Animation clocks stop");
            TwilightBossPoses.remove(e.getUUID());
        }
        LOG.info("OTHER_BOSS_POSES PASS: actual source public flags and Warden animation clocks, phase and move reset");
        var source=ResourceLocation.parse("kaleidoscope_twilight:umbral_sunflower");
        var p=PokemonSpecies.getByIdentifier(EntityBattleProfiles.get(source).species()).create(45);
        var e=new com.cobblemon.mod.common.entity.pokemon.PokemonEntity(level,p,com.cobblemon.mod.common.CobblemonEntities.POKEMON);
        var visual=(net.minecraft.world.entity.Mob)net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(source).create(level);
        EntityBattleNativeModels.prepare(visual,e);
        var moves=List.of("leafblade","sacredsword","bulldoze","protect");
        var clocks=List.of("getSwordAnimationTime","getSwordAuraAnimationTime","getGroundSpikeAnimationTime","getShieldAnimationTime");
        var phases=List.of("getSwordAnimPhase","getSwordAuraAnimPhase","getGroundSpikeAnimPhase","getShieldAnimPhase");
        for(int i=0;i<moves.size();i++){
            TwilightBossPoses.receive(e.getUUID(),moves.get(i),-1);TwilightBossPoses.apply(e,visual);
            int first=(Integer)visual.getClass().getMethod(clocks.get(i)).invoke(visual);
            if(first<=0)throw new AssertionError("Sunflower native animation start "+moves.get(i));
            for(int j=0;j<clocks.size();j++)if(j!=i&&(Integer)visual.getClass().getMethod(clocks.get(j)).invoke(visual)!=0)throw new AssertionError("Other animation clock stayed active");
            level.setGameTime(level.getGameTime()+15);TwilightBossPoses.apply(e,visual);
            int next=(Integer)visual.getClass().getMethod(clocks.get(i)).invoke(visual);
            if(next<=0||next>=first||(Float)visual.getClass().getMethod(phases.get(i)).invoke(visual)<=0)throw new AssertionError("Native animation did not advance "+moves.get(i));
            level.setGameTime(level.getGameTime()+16);TwilightBossPoses.apply(e,visual);
            if((Integer)visual.getClass().getMethod(clocks.get(i)).invoke(visual)!=0)throw new AssertionError("Sunflower native animation did not stop");
        }
        TwilightBossPoses.remove(e.getUUID());
        LOG.info("SUNFLOWER_POSES PASS: four distinct native source animation clocks start, advance and stop without source AI");
        source=ResourceLocation.parse("deep_aether:eots_controller");
        p=PokemonSpecies.getByIdentifier(EntityBattleProfiles.get(source).species()).create(65);
        e=new com.cobblemon.mod.common.entity.pokemon.PokemonEntity(level,p,com.cobblemon.mod.common.CobblemonEntities.POKEMON);
        visual=(net.minecraft.world.entity.Mob)net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(source).create(level);
        long count=java.util.stream.StreamSupport.stream(level.entitiesForRendering().spliterator(),false).count();
        EntityBattleNativeModels.prepare(visual,e);
        var parts=EntityBattleNativeModels.parts(visual);
        if(parts==null||parts.length!=22||EntityBattleNativeModels.rendersParent(visual))throw new AssertionError("Complete EOTS segmented model");
        for(int i=0;i<parts.length;i++) {
            if((Boolean)parts[i].getClass().getMethod("isControllingSegment").invoke(parts[i])!=(i==0))throw new AssertionError("Exactly one native head");
            if(level.getEntity(parts[i].getId())!=null)throw new AssertionError("Display body added to level");
        }
        e.tickCount++;visual.tickCount=e.tickCount;
        TwilightBossPoses.receive(e.getUUID(),"hurricane",-1);EntityBattleNativeModels.prepare(visual,e);
        if(!(Boolean)parts[0].getClass().getMethod("isMouthOpen").invoke(parts[0]))throw new AssertionError("Native EOTS attack mouth");
        level.setGameTime(level.getGameTime()+31);e.tickCount++;visual.tickCount=e.tickCount;EntityBattleNativeModels.prepare(visual,e);
        if((Boolean)parts[0].getClass().getMethod("isMouthOpen").invoke(parts[0]))throw new AssertionError("EOTS attack animation expires");
        if(java.util.stream.StreamSupport.stream(level.entitiesForRendering().spliterator(),false).count()!=count)throw new AssertionError("EOTS visual spawned world entity");
        TwilightBossPoses.remove(e.getUUID());
        LOG.info("DEEP_AETHER_POSES PASS: 22 native body segments, one head, attack mouth and reset; no world body entities");
    }
    private static class Geometry implements VertexConsumer {
        int vertices;float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        public VertexConsumer addVertex(float x,float y,float z){vertices++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private static class Preview extends Screen {
        Preview(){super(Component.literal("JEI item origin"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            if(finished)return;
            try{
                g.fill(0,0,width,height,0xff334151);
                for(int i=0;i<SAMPLES.size();i++){
                    var sample=SAMPLES.get(i);int x=25+i%7*42,y=45+i/7*95;
                    g.fill(x,y,x+32,y+32,0xff617589);g.renderItem(sample.item(),x+8,y+8);
                    if(frames==0){
                        var geometry=new Geometry();new PokemonItemRenderer().render(sample.item(),ItemDisplayContext.GUI,new PoseStack(),type->geometry,0xf000f0,0);
                        LOG.info("ITEM_ICON GEOMETRY {} X={}..{} Y={}..{}",sample.source(),geometry.minX,geometry.maxX,geometry.minY,geometry.maxY);
                        if(geometry.vertices==0||geometry.minX<-.1||geometry.maxX>1.1||geometry.minY<-.1||geometry.maxY>1.1)throw new AssertionError("Item model exceeds nominal item box "+sample.source());
                    }
                }
                g.flush();
                if(++frames==20){try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Minecraft.getInstance().gameDirectory.toPath().resolve("jei-item-origin.png"));}LOG.info("ITEM_ICONS PASS: thirteen actual PokemonItem renders in nominal item bounds; generic origin fix");finished=true;Minecraft.getInstance().stop();}
            }catch(Throwable failure){LOG.error("ITEM_ICONS FAIL",failure);finished=true;Minecraft.getInstance().stop();}
        }
    }
}
