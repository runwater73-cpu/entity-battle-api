package dev.entitybattle.check;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.entitybattle.api.EntityPokemonOrigin;
import dev.entitybattle.client.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;

/** Actual native renderer calls in an isolated client; never part of the release JAR. */
@EventBusSubscriber(modid="entitybattle",value=Dist.CLIENT)
public final class MultipartSmoke {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final List<String> TYPES=List.of("naga","hydra","snow_queen","minoshroom","alpha_yeti","ur_ghast","knight_phantom","lich");
    private static final List<PokemonEntity> POKEMON=new ArrayList<>();
    private static final List<FloatingState> STATES=new ArrayList<>();
    private static boolean opened,prepared,finished;
    private static volatile boolean worldVerified;
    private static UUID sourceUuid;
    private static int ticks,frames;
    @SubscribeEvent public static void server(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if(worldVerified || finished)return;
        try {
            var level=event.getServer().overworld();
            if(sourceUuid==null) {
                level.setChunkForced(0,0,true);
                var nativeBoss=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("twilightforest:naga")).create(level);
                nativeBoss.setPos(0,100,0);nativeBoss.setNoAi(true);sourceUuid=nativeBoss.getUUID();level.addFreshEntity(nativeBoss);return;
            }
            var replacement=level.getEntitiesOfClass(PokemonEntity.class,new net.minecraft.world.phys.AABB(-32,0,-32,32,300,32),p->EntityPokemonOrigin.sourceUuid(p.getPokemon()).filter(sourceUuid::equals).isPresent());
            if(replacement.isEmpty())return;
            var boss=replacement.get(0);require(boss.isPersistenceRequired(),"Auto-converted boss retains persistence");
            var aging=new com.cobblemon.mod.common.api.entity.Despawner<PokemonEntity>() {
                public void beginTracking(PokemonEntity entity){}
                public boolean shouldDespawn(PokemonEntity entity){return true;}
            };
            var old=boss.getDespawner();boss.setDespawner(aging);boss.checkDespawn();
            require(!boss.isRemoved(),"Boss survives a positive Cobblemon aging decision");boss.setDespawner(old);
            require(boss.saveWithoutId(new CompoundTag()).getBoolean("PersistenceRequired"),"Boss persistence saved in entity NBT");
            var source=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("twilightforest:hedge_spider")).create(level);source.setPos(10,100,0);
            var ordinary=dev.entitybattle.battle.EntityNativePokemonConversion.sendOut(source,dev.entitybattle.api.EntityBattleProfiles.get(source.getType()),false).entity();
            ordinary.setDespawner(aging);ordinary.checkDespawn();require(ordinary.isRemoved(),"Ordinary converted mob still ages normally");
            boss.getPokemon().recall();worldVerified=true;
            LOG.info("MULTIPART_SMOKE CHECK: actual world Naga spawn conversion; boss NBT persistence/aging protection; ordinary mob despawn control");
        }catch(Throwable failure){LOG.error("MULTIPART_SMOKE FAIL world lifetime",failure);finished=true;event.getServer().halt(false);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var client=Minecraft.getInstance(); if(finished)return;
        try {
            if(!opened && client.screen instanceof TitleScreen) {
                opened=true;client.createWorldOpenFlows().openWorld("multipart-smoke-world",()->{throw new AssertionError("World failed to open");});return;
            }
            if(opened && !prepared && client.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup) {
                // Only our copied diagnostic world is ever opened by this harness.
                var callback=net.minecraft.client.gui.screens.BackupConfirmScreen.class.getDeclaredField("onProceed");callback.setAccessible(true);
                ((net.minecraft.client.gui.screens.BackupConfirmScreen.Listener)callback.get(backup)).proceed(false,false);
            }
            if(client.level==null) {
                if(opened && ++ticks%200==0)LOG.info("MULTIPART_SMOKE waiting for isolated world: {}",client.screen==null?null:client.screen.getClass().getName());
                if(ticks>2400)throw new AssertionError("Isolated world load timed out");
                return;
            }
            if(!prepared) {
                prepared=true;
                for(String type:TYPES) {
                    var mob=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("twilightforest:"+type)).create(client.level);
                    var pokemon=PokemonSpecies.getByIdentifier(id("entitybattle:twilightforest_"+type)).create(35);pokemon.setClient$common(true);
                    EntityPokemonOrigin.bind(pokemon,mob);
                    var entity=new PokemonEntity(client.level,pokemon,CobblemonEntities.POKEMON); entity.setPos(100,120,100);entity.xo=100;entity.yo=120;entity.zo=100;
                    EntityPokemonNativeVisuals.setSource(entity.getUUID(),id("twilightforest:"+type),new CompoundTag());
                    POKEMON.add(entity);var state=new FloatingState();state.setCurrentAspects(pokemon.getAspects());STATES.add(state);
                }
                client.setScreen(new Preview());
                Class.forName("xaero.hud.minimap.radar.icon.RadarIconManager");
            }
            if(!(client.screen instanceof Preview))client.setScreen(new Preview());
            for(var entity:POKEMON)entity.tickCount=++ticks;
            if(ticks>2400)throw new AssertionError("Preview timeout");
        } catch(Throwable failure) {LOG.error("MULTIPART_SMOKE FAIL",failure);finished=true;client.stop();}
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("EntityBattle isolated visual check"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
            if(finished)return;
            try {
                graphics.fill(0,0,width,height,0xFF202630);
                for(int i=0;i<POKEMON.size();i++) {
                    var entity=POKEMON.get(i); String type=TYPES.get(i);
                    String move=switch(type){case "naga"->"bodyslam";case "hydra"->"flamethrower";case "snow_queen"->"icebeam";case "minoshroom"->"rockslide";case "alpha_yeti"->"icehammer";case "ur_ghast"->"fireblast";case "knight_phantom"->"shadowclaw";default->"shadowball";};
                    if(frames==0)TwilightBossPoses.receive(entity.getUUID(),move,-1);
                    var model=EntityPokemonNativeVisuals.modelFor(entity);
                    require(model!=null,"Native model "+type);
                    EntityBattleNativeModels.prepare(model,entity);
                    if(type.equals("naga"))require(Arrays.stream(model.getParts()).filter(p->EntityBattleNativeModels.isPartVisible(model,p)).count()==12,"All twelve naga segments visible");
                    if(type.equals("hydra"))require(Arrays.stream(model.getParts()).filter(p->p.getClass().getSimpleName().equals("HydraHead")&&EntityBattleNativeModels.isPartVisible(model,p)).count()==3,"Three hydra heads active");
                    if(type.equals("snow_queen"))require(Arrays.stream(model.getParts()).allMatch(p->p.position().distanceTo(model.position())<4),"Queen shields positioned at model");
                    if(frames==0) {
                        String getter=switch(type){case "naga","minoshroom","ur_ghast"->"isCharging";case "alpha_yeti"->"isRampaging";case "knight_phantom"->"isChargingAtPlayer";case "snow_queen"->"isBreathing";default->null;};
                        if(getter!=null)require((Boolean)model.getClass().getMethod(getter).invoke(model),"Native attack pose "+type);
                    }
                    graphics.pose().pushPose();
                    float profileScale=Math.min(width/8F,height/4F)*.65F;
                    graphics.pose().translate((i%4+.5)*width/4,(i/4+.5)*height/2-profileScale,0);
                    require(EntityBattlePortraits.drawProfile(entity.getPokemon().getSpecies().getResourceIdentifier(),graphics.pose(),
                            new Quaternionf().rotationXYZ(0.15F,-0.6F,0),STATES.get(i),partial,profileScale,
                            ProfileTransformType.PROFILE,false,1,1,1,1,15),"Real GUI render "+type);
                    graphics.pose().popPose();
                    graphics.drawCenteredString(font,type,(i%4*2+1)*width/8,i/4*height/2+6,0xFFFFFF);
                }
                graphics.flush();frames++;
                if(frames>=40 && worldVerified) {
                    try(var picture=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                        picture.writeToFile(Minecraft.getInstance().gameDirectory.toPath().resolve("multipart-preview.png"));
                    }
                    var naga=POKEMON.get(0);naga.getPokemon().setCurrentHealth((naga.getPokemon().getMaxHealth()+1)/2);
                    naga.tickCount++;var model=EntityPokemonNativeVisuals.modelFor(naga);EntityBattleNativeModels.prepare(model,naga);
                    require(Arrays.stream(model.getParts()).filter(p->EntityBattleNativeModels.isPartVisible(model,p)).count()==7,"HP ratio reduces only visual naga segments");
                    checkRadar(graphics);
                    LOG.info("MULTIPART_SMOKE PASS: eight actual native GUI renders, naga segments/HP, hydra heads/necks, queen shields, move states, eight Xaero native cached icons");
                    finished=true;Minecraft.getInstance().stop();
                }
            }catch(Throwable failure){LOG.error("MULTIPART_SMOKE FAIL",failure);finished=true;Minecraft.getInstance().stop();}
        }
    }
    private static void checkRadar(GuiGraphics graphics) throws Exception {
        Object hud=Class.forName("xaero.common.HudMod").getField("INSTANCE").get(null);
        Object minimap=hud.getClass().getMethod("getMinimap").invoke(hud);
        Object fbo=minimap.getClass().getMethod("getMinimapFBORenderer").invoke(minimap);
        var field=fbo.getClass().getDeclaredField("radarIconManager");field.setAccessible(true);
        Object manager=field.get(fbo);
        var get=manager.getClass().getMethod("get",net.minecraft.world.entity.Entity.class,float.class,boolean.class,boolean.class,GuiGraphics.class,com.mojang.blaze3d.pipeline.RenderTarget.class);
        Object failed=manager.getClass().getField("FAILED").get(null),dot=manager.getClass().getField("DOT").get(null);
        List<String> failures=new ArrayList<>();
        for(var pokemon:POKEMON) {
            manager.getClass().getMethod("allowPrerender").invoke(manager);
            var icon=get.invoke(manager,pokemon,0.5F,true,false,graphics,Minecraft.getInstance().getMainRenderTarget());
            var nativeModel=EntityPokemonNativeVisuals.modelFor(pokemon.getPokemon());
            var nativeIcon=get.invoke(manager,nativeModel,0.5F,true,false,graphics,Minecraft.getInstance().getMainRenderTarget());
            LOG.info("MULTIPART_SMOKE ICON {}: converted={}, native={}, same={}",nativeModel.getType(),icon==null?"null":icon==failed?"FAILED":icon==dot?"DOT":"model",nativeIcon==null?"null":nativeIcon==failed?"FAILED":nativeIcon==dot?"DOT":"model",icon==nativeIcon);
            if(icon==null || icon==failed || icon==dot)failures.add(nativeModel.getType().toString());
            require(icon==nativeIcon,"Converted icon shares native type/variant cache");
        }
        require(failures.isEmpty(),"Actual Xaero icons failed: "+failures);
        LOG.info("MULTIPART_SMOKE CHECK: Xaero generated eight source-model icons; converted and native cache identities match");
    }
    private static ResourceLocation id(String id){return ResourceLocation.parse(id);}
    private static void require(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
