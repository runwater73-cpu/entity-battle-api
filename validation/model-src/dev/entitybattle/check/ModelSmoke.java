package dev.entitybattle.check;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonOrigin;
import dev.entitybattle.client.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
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

/** Real repository, common GUI and world draws in a disposable client, never in release. */
@EventBusSubscriber(modid="entitybattle", value=Dist.CLIENT)
public final class ModelSmoke {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private record Example(PokemonEntity entity, FloatingState state, String name) {}
    private static final List<Example> EXAMPLES=new ArrayList<>();
    private static List<Map.Entry<ResourceLocation,ResourceLocation>> batch;
    private static int drawn;
    private static boolean opened,prepared,finished;
    private static int ticks,frames,summaryTicks;
    private static VaryingRenderableSnapshot snapshot;
    private record VaryingRenderableSnapshot(Object resolver) {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var client=Minecraft.getInstance();if(finished)return;
        try {
            if(prepared&&client.screen instanceof com.cobblemon.mod.common.client.gui.summary.Summary&&++summaryTicks==20){
                try(var picture=Screenshot.takeScreenshot(client.getMainRenderTarget())){
                    picture.writeToFile(client.gameDirectory.toPath().resolve("actual-boss-summary.png"));
                }
                LOG.info("MODEL_ACTUAL_SUMMARY PASS: original Summary screen and its six real PartySlotWidget entries");
                finished=true;client.stop();return;
            }
            if(!opened && client.screen instanceof TitleScreen){
                opened=true;client.createWorldOpenFlows().openWorld("multipart-smoke-world",()->{throw new AssertionError("Isolated world failed to open");});return;
            }
            if(opened && !prepared && client.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup){
                var callback=backup.getClass().getDeclaredField("onProceed");callback.setAccessible(true);
                ((net.minecraft.client.gui.screens.BackupConfirmScreen.Listener)callback.get(backup)).proceed(false,false);
            }
            if(client.level==null){if(++ticks>2600)throw new AssertionError("World timeout");return;}
            if(!prepared){
                prepared=true;
                ticks=0;
                EntityBattleClientProfiles.replace(EntityBattleProfiles.entityIds(),EntityBattleProfiles.visualSpeciesMap(),false);
                require(VaryingModelRepository.INSTANCE.getVariations().keySet().containsAll(EntityBattleProfiles.visualSpeciesMap().keySet()),"All profile species registered");
                LOG.info("MODEL_SMOKE registered {} profile species",EntityBattleProfiles.visualSpeciesMap().size());
                batch=EntityBattleProfiles.visualSpeciesMap().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
                for(String type:List.of("cow","zombie","sheep"))add("minecraft:"+type,type);
                for(String type:List.of("naga","hydra","snow_queen","minoshroom","alpha_yeti","ur_ghast","knight_phantom","lich"))
                    add("twilightforest:"+type,type);
                var cow=EXAMPLES.getFirst();
                var payload=Class.forName("com.jhnwudi666.teamrocket.gene.FusionFormFactory").getMethod("payloadAspect",String.class,long.class,float.class);
                add("minecraft:cow","fused cow");
                var fused=EXAMPLES.getLast();var aspects=new HashSet<>(fused.state.getCurrentAspects());
                aspects.add("tr-fused");aspects.add((String)payload.invoke(null,"cobblemon:charizard",271828L,0F));fused.state.setCurrentAspects(aspects);
                fused.entity.getPokemon().setForcedAspects(aspects);
                ((com.cobblemon.mod.common.client.render.models.blockbench.PosableState)fused.entity.getDelegate()).setCurrentAspects(aspects);
                var sheepPokemon=EXAMPLES.get(2).entity.getPokemon();
                var guiSheep=(net.minecraft.world.entity.animal.Sheep)EntityPokemonNativeVisuals.modelFor(sheepPokemon.getSpecies().getResourceIdentifier(),EXAMPLES.get(2).state);
                require(guiSheep.isBaby()&&guiSheep.getColor()==net.minecraft.world.item.DyeColor.RED,"Lightweight aspects retain red baby sheep");
                Class.forName("com.jhnwudi666.teamrocket.client.fusion.FusionVariationInjector").getMethod("tick").invoke(null);
                var cowSpecies=cow.entity.getPokemon().getSpecies().getResourceIdentifier();
                require(VaryingModelRepository.INSTANCE.getTexture(cowSpecies,fused.state).getNamespace().equals("teamrocket"),"TeamRocket naturally patches native resolver");
                verifyFusion(fused.state,cowSpecies);
                verifyFusion((com.cobblemon.mod.common.client.render.models.blockbench.PosableState)fused.entity.getDelegate(),cowSpecies);
                var official=new FloatingState();official.setCurrentAspects(Set.of("tr-fused",(String)payload.invoke(null,cowSpecies.toString(),314159L,0F)));
                verifyFusion(official,id("cobblemon:charizard"));
                snapshot=new VaryingRenderableSnapshot(VaryingModelRepository.INSTANCE.getVariations().get(cowSpecies));
                client.setScreen(new Preview());
            }
            ++ticks;
            for(var sample:EXAMPLES)sample.entity.tickCount=ticks;
            if(ticks>3600)throw new AssertionError("Draw timeout");
        }catch(Throwable failure){LOG.error("MODEL_SMOKE FAIL",failure);finished=true;client.stop();}
    }
    private static void add(String source,String label){
        var client=Minecraft.getInstance();var mob=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id(source)).create(client.level);
        var species=Objects.requireNonNull(EntityBattleProfiles.get(id(source))).species();
        if(mob instanceof net.minecraft.world.entity.animal.Sheep sheep){sheep.setColor(net.minecraft.world.item.DyeColor.RED);sheep.setAge(-24000);}
        var pokemon=PokemonSpecies.getByIdentifier(species).create(35);EntityPokemonOrigin.bind(pokemon,mob);
        EntityPokemonOrigin.refreshAppearance(pokemon,mob);pokemon.setClient$common(true);
        var entity=new PokemonEntity(client.level,pokemon,CobblemonEntities.POKEMON);entity.setPos(100,120,100);entity.xo=100;entity.yo=120;entity.zo=100;
        EntityPokemonNativeVisuals.setSource(entity.getUUID(),id(source),EntityPokemonOrigin.appearance(pokemon).orElseGet(CompoundTag::new));
        var state=new FloatingState();state.setCurrentAspects(pokemon.getAspects());EXAMPLES.add(new Example(entity,state,label));
    }
    private static void checkLargeSlime(){
        var client=Minecraft.getInstance();var slime=(net.minecraft.world.entity.monster.Slime)
                BuiltInRegistries.ENTITY_TYPE.get(id("minecraft:slime")).create(client.level);slime.setSize(8,true);
        var species=Objects.requireNonNull(EntityBattleProfiles.get(id("minecraft:slime"))).species();
        var pokemon=PokemonSpecies.getByIdentifier(species).create(35);EntityPokemonOrigin.bind(pokemon,slime);
        EntityPokemonOrigin.refreshAppearance(pokemon,slime);pokemon.setClient$common(true);
        var entity=new PokemonEntity(client.level,pokemon,CobblemonEntities.POKEMON);
        var state=new FloatingState();state.setCurrentAspects(pokemon.getAspects());
        require(((net.minecraft.world.entity.monster.Slime)EntityPokemonNativeVisuals.modelFor(species,state)).getSize()==8,"Large slime appearance retained");
        checkSummaryGeometry(new Example(entity,state,"size-8 slime"));
        checkPortraitGeometry(new Example(entity,state,"size-8 slime"));
    }
    private static void verifyFusion(com.cobblemon.mod.common.client.render.models.blockbench.PosableState state,ResourceLocation species)throws Exception {
        var repo=VaryingModelRepository.INSTANCE;var texture=repo.getTexture(species,state);
        require(texture.getNamespace().equals("teamrocket"),"Dynamic fusion texture "+species);
        var dynamic=(DynamicTexture)Minecraft.getInstance().getTextureManager().getTexture(texture);var output=dynamic.getPixels();
        var clean=new FloatingState();var base=repo.getTexture(species,clean);
        try(var resource=Minecraft.getInstance().getResourceManager().getResource(base).orElseThrow().open();var input=NativeImage.read(resource)){
            require(output.getWidth()==input.getWidth()&&output.getHeight()==input.getHeight(),"Fusion dimensions retained");
            int changed=0;for(int y=0;y<input.getHeight();y++)for(int x=0;x<input.getWidth();x++)if(input.getPixelRGBA(x,y)!=output.getPixelRGBA(x,y))changed++;
            require(changed>0,"Actual pixels recoloured "+species);LOG.info("MODEL_SMOKE FUSION {}: {} pixels changed",species,changed);
        }
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("Cobblemon repository models"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
            if(finished)return;
            try{
                if(frames==0){checkTextureFirst();checkLargeSlime();}
                graphics.fill(0,0,width,height,0xff26303a);
                for(int i=0;i<EXAMPLES.size();i++){
                    var example=EXAMPLES.get(i);var species=example.entity.getPokemon().getSpecies().getResourceIdentifier();
                    require(VaryingModelRepository.INSTANCE.getPoser(species,example.state).getRootPart().getClass().getSimpleName().equals("NativePokemonBone"),"Actual native registered bone "+species);
                    float scale=Math.min(width/9F,height/5F)*.52F;
                    graphics.pose().pushPose();graphics.pose().translate((i%4+.5)*width/4,i/4*height/3+20,0);
                    PokemonGuiUtilsKt.drawProfilePokemon(species,graphics.pose(),new Quaternionf().rotationXYZ(.15F,-.6F,0),PoseType.PROFILE,example.state,
                            partial,scale,ProfileTransformType.PROFILE,false,false,1,1,1,1,0,0,15);
                    graphics.pose().popPose();graphics.drawCenteredString(font,example.name,(i%4*2+1)*width/8,i/4*height/3+5,0xffffff);
                    // Common portrait path used by the battle/party widgets.
                    graphics.pose().pushPose();graphics.pose().translate(i%4*width/4+4,i/4*height/3+15,0);
                    GuiUtilsKt.drawPosablePortrait(species,graphics.pose(),8,1,false,example.state,partial,0,0,0,0,0,false,1,1,1,1);
                    graphics.pose().popPose();
                    var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(example.entity);
                    if(frames==0&&i>=3&&i<11)checkMove(example);
                    if(frames==0)checkSummaryGeometry(example);
                    if(frames==0)checkPortraitGeometry(example);
                    // Record world geometry without mixing a world camera into this GUI screenshot.
                    // Screen.render's delta is not the world's interpolation fraction.
                    float worldPartial=.5F;
                    var world=new Geometry();renderer.render(example.entity,0,worldPartial,new PoseStack(),type->world,0xf000f0);
                    require(world.vertices>0,"World geometry emitted "+species);
                    if(frames==0&&(example.name.equals("cow")||example.name.equals("alpha_yeti"))){
                        var source=EntityPokemonNativeVisuals.modelFor(example.entity);var baseline=new Geometry();
                        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(source).render(source,0,worldPartial,new PoseStack(),type->baseline,0xf000f0);
                        float nativeScale=example.entity.getPokemon().getForm().getBaseScale()*example.entity.getPokemon().getEffectiveScale();
                        double offset=TwilightBossPoses.offset(example.entity,worldPartial).y*nativeScale;
                        require(Math.abs(world.minY-baseline.minY*nativeScale-offset)<.01&&Math.abs(world.maxY-baseline.maxY*nativeScale-offset)<.01,"World ground/height match source "+species+" "+world.minY+".."+world.maxY+" vs "+baseline.minY+".."+baseline.maxY);
                        LOG.info("MODEL_SMOKE GEOMETRY {}: registered and source Y bounds agree",species);
                    }
                }
                for(int n=0;n<8&&drawn<batch.size();n++,drawn++){
                    var entry=batch.get(drawn);var state=new FloatingState();
                    var poser=VaryingModelRepository.INSTANCE.getPoser(entry.getKey(),state);
                    require(poser.getRootPart().getClass().getSimpleName().equals("NativePokemonBone"),"Batch model "+entry.getKey());
                    graphics.pose().pushPose();graphics.pose().translate(-2000,-2000,0);
                    PokemonGuiUtilsKt.drawProfilePokemon(entry.getKey(),graphics.pose(),new Quaternionf(),PoseType.PROFILE,state,partial,10,ProfileTransformType.PROFILE,false,false,1,1,1,1,0,0,15);
                    graphics.pose().popPose();
                    require(VaryingModelRepository.INSTANCE.getPoser(entry.getKey(),state).getRootPart().getClass().getSimpleName().equals("NativePokemonBone"),"Batch renderer did not fail "+entry.getKey());
                }
                graphics.flush();frames++;
                if(frames==45){
                    try(var picture=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){picture.writeToFile(Minecraft.getInstance().gameDirectory.toPath().resolve("model-preview.png"));}
                    // Rebuild native repository entries without touching the live user's pack.
                    EntityBattleModelRepository.reloaded();EntityBattleModelRepository.registerProfiles(EntityBattleProfiles.visualSpeciesMap());
                    var cowSpecies=EXAMPLES.getFirst().entity.getPokemon().getSpecies().getResourceIdentifier();
                    require(VaryingModelRepository.INSTANCE.getVariations().get(cowSpecies)!=snapshot.resolver,"Resolver rebuilt");
                    Class.forName("com.jhnwudi666.teamrocket.client.fusion.FusionVariationInjector").getMethod("onResourceReload").invoke(null);
                    Class.forName("com.jhnwudi666.teamrocket.client.fusion.FusionVariationInjector").getMethod("tick").invoke(null);
                    verifyFusion(EXAMPLES.getLast().state,cowSpecies);
                    require(drawn==batch.size(),"Every profile drawn");
                    LOG.info("MODEL_SMOKE PASS: {} actual common profile renders, common portraits and world renders, eight boss move/part states, both fusion directions and pixel changes, variant aspects, resource rebuild",drawn);
                    Minecraft.getInstance().setScreen(new SummaryPreview());
                }
            }catch(Throwable failure){LOG.error("MODEL_SMOKE FAIL",failure);finished=true;Minecraft.getInstance().stop();}
        }
    }
    private static final class Geometry implements com.mojang.blaze3d.vertex.VertexConsumer {
        int vertices;float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
        public Geometry addVertex(float x,float y,float z){vertices++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);minX=Math.min(minX,x);maxX=Math.max(maxX,x);return this;}
        public Geometry setColor(int r,int g,int b,int a){return this;}
        public Geometry setUv(float u,float v){return this;}
        public Geometry setUv1(int u,int v){return this;}
        public Geometry setUv2(int u,int v){return this;}
        public Geometry setNormal(float x,float y,float z){return this;}
    }
    private static void checkTextureFirst() {
        // Official GUI and fusion queries resolve textures before applying model animations.
        // Use fresh states: warm previews cannot expose an uninitialized multipart measurement.
        for(var sample:EXAMPLES) {
            if(!Set.of("naga","hydra","snow_queen","lich").contains(sample.name))continue;
            var state=new FloatingState();state.setCurrentAspects(sample.state.getCurrentAspects());
            var species=sample.entity.getPokemon().getSpecies().getResourceIdentifier();
            VaryingModelRepository.INSTANCE.getTexture(species,state);
            checkSummaryGeometry(new Example(sample.entity,state,sample.name+" texture first"));
        }
        LOG.info("MODEL_TEXTURE_FIRST PASS: cold texture query followed by full multipart summary and PC geometry");
    }
    private static void checkSummaryGeometry(Example example) {
        var species=example.entity.getPokemon().getSpecies().getResourceIdentifier();
        var repo=VaryingModelRepository.INSTANCE;var model=repo.getPoser(species,example.state);
        var context=new com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext();
        context.put(com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext.Companion.getPOSABLE_STATE(),example.state);
        context.put(com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext.Companion.getSPECIES(),species);
        model.setContext(context);example.state.setCurrentModel(model);example.state.setPoseToFirstSuitable(PoseType.PROFILE);
        // Exact official ModelWidget summary anchor, scale, rotation and 66x66 scissor.
        var stack=new PoseStack();stack.translate(33,-10,0);stack.scale(40,40,-40);
        float fitted=model.getProfileSummaryScale();var offset=model.getProfileSummaryTranslation();
        stack.translate(offset.x,offset.y+1.5*fitted,offset.z-4);stack.scale(fitted,fitted,fitted);
        stack.mulPose(new Quaternionf().rotationXYZ((float)Math.toRadians(13),(float)Math.toRadians(325),0));
        var geometry=new Geometry();
        for(float partial: new float[]{.05F,.45F,.95F}) {
            example.state.updatePartialTicks(partial);
            model.applyAnimations(null,example.state,0,0,0,0,20);
            model.withLayerContext(type->geometry,example.state,repo.getLayers(species,example.state),()->{
                model.render(context,stack,geometry,0xf000f0,0,-1);return kotlin.Unit.INSTANCE;
            });
            if(example.name.equals("lich")) {
                var source=EntityPokemonNativeVisuals.modelFor(species,example.state);
                var nativeModel=(net.minecraft.client.model.HumanoidModel<?>)((net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?>)
                        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(source)).getModel();
                require(Math.abs(nativeModel.head.xRot-(float)Math.toRadians(20))<.001,"GUI head pitch does not re-interpolate "+partial+" = "+nativeModel.head.xRot);
            }
        }
        require(geometry.vertices>0&&geometry.minX>=1&&geometry.maxX<=65&&geometry.minY>=1&&geometry.maxY<=65,
                "Full summary geometry fits "+example.name+" X="+geometry.minX+".."+geometry.maxX+" Y="+geometry.minY+".."+geometry.maxY);
        var slotStack=new PoseStack();slotStack.translate(12.5,1,0);slotStack.scale(11.25F,11.25F,-4.5F);
        float slotScale=model.getProfileScale();var slotOffset=model.getProfileTranslation();
        slotStack.translate(slotOffset.x,slotOffset.y+1.5*slotScale,slotOffset.z-4);slotStack.scale(slotScale,slotScale,slotScale);
        slotStack.mulPose(new Quaternionf().rotationXYZ((float)Math.toRadians(13),(float)Math.toRadians(35),0));
        model.applyAnimations(null,example.state,0,0,0,0,0);
        var slot=new Geometry();
        model.withLayerContext(type->slot,example.state,repo.getLayers(species,example.state),()->{
            model.render(context,slotStack,slot,0xf000f0,0,-1);return kotlin.Unit.INSTANCE;
        });
        require(slot.vertices>0&&slot.minX>=-2&&slot.maxX<=29&&slot.minY>=2&&slot.maxY<=29,
                "Full PC slot geometry fits "+example.name+" X="+slot.minX+".."+slot.maxX+" Y="+slot.minY+".."+slot.maxY);
        if(example.name.equals("lich")) {
            example.entity.setXRot(32);example.entity.xRotO=-12;
            var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(example.entity);
            for(float partial:new float[]{.05F,.45F,.95F}) {
                renderer.render(example.entity,0,partial,new PoseStack(),type->new Geometry(),0xf000f0);
                var source=EntityPokemonNativeVisuals.modelFor(example.entity);
                var nativeModel=(net.minecraft.client.model.HumanoidModel<?>)((net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?>)
                        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(source)).getModel();
                require(Math.abs(nativeModel.head.xRot)<.001,"World display head stays level "+partial+" = "+nativeModel.head.xRot);
            }
        }
        LOG.info("MODEL_LAYOUT geometry {} summary X={}..{} Y={}..{}; PC X={}..{} Y={}..{}",example.name,
                geometry.minX,geometry.maxX,geometry.minY,geometry.maxY,slot.minX,slot.maxX,slot.minY,slot.maxY);
    }
    private static final class SummaryPreview extends Screen {
        private int summaryFrames;
        private final List<com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget> widgets=new ArrayList<>();
        SummaryPreview(){super(Component.literal("Actual Cobblemon summary widgets"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override protected void init(){
            widgets.clear();
            for(int i=0;i<EXAMPLES.size();i++)widgets.add(new com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget(
                    (i%4*2+1)*width/8-33,i/4*height/3+23,66,66,EXAMPLES.get(i).entity.getPokemon().asRenderablePokemon(),
                    2F,325F,-10,false,true,13));
        }
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
            try{
                graphics.fill(0,0,width,height,0xff26303a);
                for(int i=0;i<widgets.size();i++){
                    var widget=widgets.get(i);graphics.fill(widget.getX(),widget.getY(),widget.getX()+66,widget.getY()+66,0xff424d59);
                    widget.render(graphics,-1000,-1000,partial);
                    graphics.drawCenteredString(font,EXAMPLES.get(i).name,widget.getX()+33,widget.getY()-13,0xffffff);
                }
                graphics.flush();
                if(++summaryFrames==15){
                    try(var picture=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
                        picture.writeToFile(Minecraft.getInstance().gameDirectory.toPath().resolve("summary-preview.png"));
                    }
                    LOG.info("MODEL_LAYOUT PASS: actual summary widgets, twelve full-body summary and PC slot geometry fits plus size-8 slime, GUI cursor pitch and world head across three interpolation fractions");
                    Minecraft.getInstance().setScreen(new PortraitPreview());
                }
            }catch(Throwable failure){LOG.error("MODEL_LAYOUT FAIL",failure);finished=true;Minecraft.getInstance().stop();}
        }
    }
    private record PortraitCase(String name,int diameter,float scale,double anchorY) {}
    private static final List<PortraitCase> PORTRAITS=List.of(
            new PortraitCase("party",21,13,-12),new PortraitCase("battle",28,18,-5),
            new PortraitCase("compact battle",19,18*.65F,-15));

    private static void checkPortraitGeometry(Example example) {
        var species=example.entity.getPokemon().getSpecies().getResourceIdentifier();var repo=VaryingModelRepository.INSTANCE;
        var state=new FloatingState();state.setCurrentAspects(example.state.getCurrentAspects());
        var model=repo.getPoser(species,state);
        var context=new com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext();
        context.put(com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext.Companion.getPOSABLE_STATE(),state);
        context.put(com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext.Companion.getSPECIES(),species);
        model.setContext(context);state.setCurrentModel(model);state.setPoseToFirstSuitable(PoseType.PORTRAIT);
        for(var frame:PORTRAITS)for(boolean reverse:new boolean[]{false,true}) {
            // Exact party/battle callers plus the shared GuiUtils portrait transform.
            var stack=new PoseStack();stack.translate(frame.diameter/2.0-(frame.name.equals("party")?1:0),frame.anchorY,0);
            stack.translate(0,30,0);stack.scale(frame.scale,frame.scale,-frame.scale);stack.translate(0,-28.0/18,0);
            float fitted=model.getPortraitScale();var offset=model.getPortraitTranslation();
            stack.translate(offset.x*(reverse?-1:1),offset.y+1.5*fitted,offset.z-4);stack.scale(fitted,fitted,fitted);
            stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(reverse?32:-32));
            stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(5));
            model.applyAnimations(null,state,0,0,0,0,0);var geometry=new Geometry();
            model.withLayerContext(type->geometry,state,repo.getLayers(species,state),()->{
                model.render(context,stack,geometry,0xf000f0,0,-1);return kotlin.Unit.INSTANCE;
            });
            require(geometry.vertices>0&&geometry.minX>=0&&geometry.maxX<=frame.diameter&&geometry.minY>=0&&geometry.maxY<=frame.diameter,
                    "Full portrait fits "+example.name+" "+frame.name+" reversed="+reverse+" X="+geometry.minX+".."+geometry.maxX+" Y="+geometry.minY+".."+geometry.maxY);
            if(example.name.equals("lich"))LOG.info("MODEL_PORTRAIT FIT lich {} reversed={} X={}..{} Y={}..{}",frame.name,reverse,
                    geometry.minX,geometry.maxX,geometry.minY,geometry.maxY);
        }
    }
    private static final class PortraitPreview extends Screen {
        private int portraitFrames;
        PortraitPreview(){super(Component.literal("Party and battle portraits with actual clipping"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
            try {
                graphics.fill(0,0,width,height,0xff26303a);
                for(int i=0;i<EXAMPLES.size();i++) {
                    var example=EXAMPLES.get(i);int left=i%4*width/4+4,top=i/4*height/3+24;
                    graphics.drawCenteredString(font,example.name,left+width/8-4,top-17,0xffffff);
                    for(int f=0;f<PORTRAITS.size();f++) {
                        var frame=PORTRAITS.get(f);int x=left+f*32;
                        graphics.fill(x,top,x+frame.diameter,top+frame.diameter,0xff424d59);
                        graphics.enableScissor(x,top,x+frame.diameter,top+frame.diameter);
                        graphics.pose().pushPose();
                        graphics.pose().translate(x+frame.diameter/2.0-(frame.name.equals("party")?1:0),top+frame.anchorY,0);
                        GuiUtilsKt.drawPosablePortrait(example.entity.getPokemon().getSpecies().getResourceIdentifier(),graphics.pose(),
                                frame.scale,example.entity.getPokemon().getForm().getBaseScale(),f==2,example.state,partial,0,0,0,0,0,false,1,1,1,1);
                        graphics.pose().popPose();graphics.disableScissor();
                    }
                }
                graphics.flush();
                if(++portraitFrames==15){
                    try(var picture=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
                        picture.writeToFile(Minecraft.getInstance().gameDirectory.toPath().resolve("portrait-preview.png"));
                    }
                    LOG.info("MODEL_PORTRAIT PASS: twelve samples plus size-8 slime, party and both battle frame sizes, both directions, actual common portrait draw with scissors");
                    com.cobblemon.mod.common.client.gui.summary.Summary.Companion.open(EXAMPLES.subList(3,9).stream().map(e->e.entity.getPokemon()).toList(),false,0);
                }
            }catch(Throwable failure){LOG.error("MODEL_PORTRAIT FAIL",failure);finished=true;Minecraft.getInstance().stop();}
        }
    }
    private static void checkMove(Example example)throws Exception {
        String kind=example.name;
        String move=switch(kind){case "naga"->"bodyslam";case "hydra"->"flamethrower";case "snow_queen"->"icebeam";case "minoshroom"->"rockslide";case "alpha_yeti"->"icehammer";case "ur_ghast"->"fireblast";case "knight_phantom"->"shadowclaw";default->"shadowball";};
        TwilightBossPoses.receive(example.entity.getUUID(),move,-1);
        var model=EntityPokemonNativeVisuals.modelFor(example.entity);example.entity.tickCount++;
        EntityBattleNativeModels.prepare(model,example.entity);
        String getter=switch(kind){case "naga","minoshroom","ur_ghast"->"isCharging";case "alpha_yeti"->"isRampaging";case "knight_phantom"->"isChargingAtPlayer";case "snow_queen"->"isBreathing";default->null;};
        if(getter!=null)require((Boolean)model.getClass().getMethod(getter).invoke(model),"Native attack pose "+kind);
        if(kind.equals("naga"))require(Arrays.stream(model.getParts()).filter(p->EntityBattleNativeModels.isPartVisible(model,p)).count()==12,"Naga twelve parts");
        if(kind.equals("hydra"))require(Arrays.stream(model.getParts()).filter(p->p.getClass().getSimpleName().equals("HydraHead")&&EntityBattleNativeModels.isPartVisible(model,p)).count()==3,"Hydra three heads");
        if(kind.equals("snow_queen"))require(Arrays.stream(model.getParts()).allMatch(p->p.position().distanceTo(model.position())<4),"Queen shields attached");
        LOG.info("MODEL_SMOKE MOVE {} {}",kind,move);
    }
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
