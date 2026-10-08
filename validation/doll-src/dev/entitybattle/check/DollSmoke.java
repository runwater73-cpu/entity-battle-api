package dev.entitybattle.check;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.entitybattle.api.*;
import dev.entitybattle.client.*;
import dev.entitybattle.compat.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual third-party conversion/loader/render APIs; isolated world only. */
@EventBusSubscriber(modid="entitybattle",value=Dist.CLIENT)
public final class DollSmoke {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private record Sample(String id,String label,ItemStack item){}
    private static final List<Sample> samples=new ArrayList<>();
    private static boolean opened,finished,prepared,viewerPending;private static int ticks,frames;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(finished)return;var client=Minecraft.getInstance();
        try {
            if(viewerPending){
                viewerPending=false;
                // Like Cobbledex's own SpriteCache, capture on the client tick outside
                // Screen.render's model-view/scissor state.
                verifyCobbledex();
                DollModels.clear();for(var sample:samples)require(DollModels.ensure(sample.id()),"Reload identity rebuild");
                LOG.info("DOLL_SMOKE PASS: actual item/block/thrown render paths and consumer fallback, seven source models, baby colour/size, both fusion directions, giant, old dolls and registry rebuild");
                finished=true;client.stop();return;
            }
            if(!opened&&client.screen instanceof TitleScreen){opened=true;client.createWorldOpenFlows().openWorld("multipart-smoke-world",()->{throw new AssertionError("Open failed");});return;}
            if(opened&&!prepared&&client.screen instanceof BackupConfirmScreen backup){var field=backup.getClass().getDeclaredField("onProceed");field.setAccessible(true);((BackupConfirmScreen.Listener)field.get(backup)).proceed(false,false);}
            if(client.level==null){if(++ticks>2400)throw new AssertionError("World timeout");return;}
            if(!prepared){prepared=true;EntityBattleClientProfiles.replace(EntityBattleProfiles.entityIds(),EntityBattleProfiles.visualSpeciesMap(),false);prepare();client.setScreen(new Preview());}
        }catch(Throwable failure){LOG.error("DOLL_SMOKE FAIL",failure);finished=true;client.stop();}
    }
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void prepare()throws Exception{
        var client=Minecraft.getInstance();
        var defaultItem=Class.forName("com.cobblemondoll.examplemod.item.DefaultModelItem");
        var parse=defaultItem.getDeclaredMethod("parseAspects",String.class);parse.setAccessible(true);
        var build=defaultItem.getDeclaredMethod("buildModelId",String.class,boolean.class,Set.class);build.setAccessible(true);
        var loader=Class.forName("com.github.ysbbbbbb.kaleidoscopedoll.client.custom.CustomDollLoader");
        var getModel=loader.getMethod("getModel",String.class);
        for(String source:List.of("minecraft:cow","minecraft:sheep","minecraft:slime","minecraft:magma_cube","twilightforest:lich","twilightforest:naga","twilightforest:hydra")){
            var mob=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id(source)).create(client.level);
            if(mob instanceof net.minecraft.world.entity.animal.Sheep sheep){sheep.setAge(-24000);sheep.setColor(net.minecraft.world.item.DyeColor.RED);}
            if(mob instanceof net.minecraft.world.entity.monster.Slime slime)slime.setSize(8,true);
            var species=EntityBattleProfiles.get(id(source)).species();var pokemon=PokemonSpecies.getByIdentifier(species).create(35);
            EntityPokemonOrigin.bind(pokemon,mob);EntityPokemonOrigin.refreshAppearance(pokemon,mob);pokemon.setClient$common(true);
            Set<String> aspects=pokemon.getAspects();
            @SuppressWarnings("unchecked") var parsed=(Set<String>)parse.invoke(null,String.join(",",aspects));
            require(parsed.equals(aspects),"Case-sensitive presentation preserved "+source);
            String modelId=(String)build.invoke(null,species.toString(),false,parsed);
            require(DollModelIds.decode(modelId).aspects().equals(aspects),"Identity round trip "+source);
            require(getModel.invoke(null,modelId)!=null,"Loader model "+source);
            require(DollModels.texture(modelId)!=null,"Loader texture "+source);
            require(!((String)loader.getMethod("getLanguage",String.class,String.class).invoke(null,"en_us",modelId)).contains("geometry."),"Friendly name "+source);
            if(mob instanceof net.minecraft.world.entity.animal.Sheep){var state=new FloatingState();state.setCurrentAspects(DollModelIds.decode(modelId).aspects());var visual=(net.minecraft.world.entity.animal.Sheep)EntityPokemonNativeVisuals.modelFor(species,state);require(visual.isBaby()&&visual.getColor()==net.minecraft.world.item.DyeColor.RED,"Red baby sheep retained");}
            add(modelId,source);
            String old="geometry."+species.getPath()+"."+String.join(".",aspects);
            require(getModel.invoke(null,old)!=null,"Existing doll identity supported "+source);
        }
        var payload=Class.forName("com.jhnwudi666.teamrocket.gene.FusionFormFactory").getMethod("payloadAspect",String.class,long.class,float.class);
        var slimeSpecies=EntityBattleProfiles.get(id("minecraft:slime")).species();
        Set<String> fused=Set.of("tr-fused",(String)payload.invoke(null,slimeSpecies.toString(),1759484021463299392L,.7F));
        String official=(String)build.invoke(null,"cobblemon:charizard",false,fused);add(official,"Charizard + slime");
        String nativeId=(String)build.invoke(null,slimeSpecies.toString(),true,Set.of("tr-fused",(String)payload.invoke(null,"cobblemon:charizard",2L,.7F)));add(nativeId,"Giant fused slime");
        Class.forName("com.jhnwudi666.teamrocket.client.fusion.FusionVariationInjector").getMethod("tick").invoke(null);
        require(DollModels.texture(official).getNamespace().equals("teamrocket"),"Fusion dynamic texture retained");
        var state=new FloatingState();state.setCurrentAspects(fused);
        var clean=new FloatingState();
        var donor=VaryingModelRepository.INSTANCE.getTexture(slimeSpecies,clean);
        var injector=Class.forName("com.jhnwudi666.teamrocket.client.fusion.FusionVariationInjector");
        var sub=injector.getDeclaredMethod("subTexture",String.class,com.cobblemon.mod.common.client.render.models.blockbench.PosableState.class);sub.setAccessible(true);
        LOG.info("FUSION_SLIME donor repository={} injector={} main={}",donor,sub.invoke(null,slimeSpecies.toString(),state),VaryingModelRepository.INSTANCE.getTexture(id("cobblemon:charizard"),clean));
        for(var texture:List.of(donor,VaryingModelRepository.INSTANCE.getTexture(id("cobblemon:charizard"),clean),DollModels.texture(official))){
            var colors=new HashMap<Integer,Integer>();
            var resource=client.getResourceManager().getResource(texture);
            if(resource.isPresent())try(var input=resource.get().open();var image=com.mojang.blaze3d.platform.NativeImage.read(input)){
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)colors.merge(image.getPixelRGBA(x,y),1,Integer::sum);
            }else{
                var image=((net.minecraft.client.renderer.texture.DynamicTexture)client.getTextureManager().getTexture(texture)).getPixels();
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)colors.merge(image.getPixelRGBA(x,y),1,Integer::sum);
            }
            LOG.info("FUSION_SLIME pixels {} {}",texture,colors.entrySet().stream().sorted(Map.Entry.<Integer,Integer>comparingByValue().reversed()).limit(7).map(e->String.format("%08x:%d",e.getKey(),e.getValue())).toList());
        }
        require(getModel.invoke(null,"geometry.unregistered_nonsense")==null,"Unrelated unknown models unchanged");
        LOG.info("DOLL_SMOKE conversion/identity/variants/fusion passed");
    }

    private static void audit() throws Exception {
        var client=Minecraft.getInstance();
        var report=new ArrayList<Map<String,Object>>(); int mismatches=0, meshCount=0;
        for(var mapping:EntityBattleClientProfiles.visualSpecies().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            var state=new FloatingState();
            var mob=EntityPokemonNativeVisuals.modelFor(mapping.getKey(),state);
            require(mob!=null,"Audit source created "+mapping.getValue());
            EntityBattleNativeModels.prepare(mob,null);
            var renderer=client.getEntityRenderDispatcher().getRenderer(mob);
            var reported=renderer.getTextureLocation(mob);
            var actual=EntityBattleNativeModels.texture(mob);
            var materials=new TreeMap<String,Integer>(); var geometry=new Geometry();
            EntityBattleNativeModels.render(mob,0,.5F,new PoseStack(),type->{
                String texture=EntityBattleModelTextures.texture(type).map(Object::toString).orElse(type.toString());
                materials.merge(texture,1,Integer::sum);return geometry;
            },0xf000f0);
            if(geometry.vertices>0){
                meshCount++;
                require(materials.containsKey(actual.toString()),"Selected texture really rendered "+mapping.getValue()+" "+actual+" "+materials);
            }
            boolean mismatch=!reported.equals(actual);if(mismatch)mismatches++;
            var row=new LinkedHashMap<String,Object>();
            row.put("source",mapping.getValue().toString());row.put("species",mapping.getKey().toString());
            row.put("renderer",renderer.getClass().getName());row.put("reportedTexture",reported.toString());
            row.put("actualTexture",actual.toString());row.put("reportedMismatch",mismatch);row.put("materials",materials);
            row.put("vertices",geometry.vertices);row.put("meshVerified",geometry.vertices>0);
            row.put("resourcePack",client.getResourceManager().getResource(actual).map(net.minecraft.server.packs.resources.Resource::sourcePackId).orElse("not a PNG resource"));
            report.add(row);
            if(mismatch)LOG.info("MODEL_AUDIT mismatch {} reported={} actual={}",mapping.getValue(),reported,actual);
            if(geometry.vertices==0)LOG.info("MODEL_AUDIT no submitted mesh {} renderer={}",mapping.getValue(),renderer.getClass().getName());
        }
        java.nio.file.Files.writeString(client.gameDirectory.toPath().resolve("native-texture-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        LOG.info("MODEL_AUDIT PASS: {} active sources inspected; {} meshes drawn; {} reported/actual texture mismatches",report.size(),meshCount,mismatches);
    }

    private static void verifyDollPaths(Sample sample)throws Exception{
        var client=Minecraft.getInstance();
        var geometry=new Geometry();
        net.minecraft.client.renderer.MultiBufferSource buffers=type->geometry;
        var customRenderer=net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(sample.item()).getCustomRenderer();
        require(customRenderer!=null,"Item renderer exists");
        customRenderer.renderByItem(sample.item(),ItemDisplayContext.GUI,new PoseStack(),buffers,0xf000f0,0);
        require(geometry.vertices>0,"Actual item path emits geometry "+sample.label());
        int itemVertices=geometry.vertices;geometry.vertices=0;
        var block=BuiltInRegistries.BLOCK.get(id("kaleidoscope_doll:custom_doll"));
        require(block!=net.minecraft.world.level.block.Blocks.AIR,"Doll block registered");
        var blockClass=Class.forName("com.github.ysbbbbbb.kaleidoscopedoll.block.entity.CustomDollBlockEntity");
        var blockEntity=(net.minecraft.world.level.block.entity.BlockEntity)blockClass.getConstructor(net.minecraft.core.BlockPos.class,net.minecraft.world.level.block.state.BlockState.class)
                .newInstance(net.minecraft.core.BlockPos.ZERO,block.defaultBlockState());
        blockEntity.setLevel(client.level);blockClass.getMethod("setModelId",String.class).invoke(blockEntity,sample.id());
        var blockRenderer=client.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        require(blockRenderer!=null,"Block renderer exists");
        blockRenderer.getClass().getMethod("render",blockClass,float.class,PoseStack.class,net.minecraft.client.renderer.MultiBufferSource.class,int.class,int.class)
                .invoke(blockRenderer,blockEntity,.5F,new PoseStack(),buffers,0xf000f0,0);
        require(geometry.vertices>0,"Actual block path emits geometry "+sample.label());
        int blockVertices=geometry.vertices;geometry.vertices=0;
        var dollClass=Class.forName("com.github.ysbbbbbb.kaleidoscopedoll.entity.DollEntity");
        var doll=(net.minecraft.world.entity.Entity)dollClass.getConstructor(net.minecraft.world.level.Level.class,double.class,double.class,double.class,float.class)
                .newInstance(client.level,0D,0D,0D,0F);
        dollClass.getMethod("setCustomDollId",String.class).invoke(doll,sample.id());
        client.getEntityRenderDispatcher().getRenderer(doll).render(doll,0,.5F,new PoseStack(),buffers,0xf000f0);
        require(geometry.vertices>0,"Actual thrown entity path emits geometry "+sample.label());
        LOG.info("DOLL_PATHS {} item={} block={} thrown={}",sample.label(),itemVertices,blockVertices,geometry.vertices);
        geometry.vertices=0;
        var stack=new PoseStack();stack.translate(.5,1.5,.5);stack.mulPose(Axis.ZN.rotationDegrees(180));
        DollModels.model(sample.id()).renderToBuffer(stack,geometry,0xf000f0,0,-1);
        require(geometry.vertices>0,"Consumer-only model fallback emits geometry "+sample.label());
    }

    private static void verifyReplacementSwitch()throws Exception{
        var config=(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)Class.forName("com.github.tartaricacid.touhoulittlemaid.config.subconfig.VanillaConfig")
                .getField("REPLACE_SLIME_MODEL").get(null);
        boolean original=config.get();
        try{
            for(boolean enabled:List.of(false,true)){
                config.set(enabled);EntityPokemonNativeVisuals.clearResourceModels();
                for(String source:List.of("minecraft:slime","minecraft:magma_cube")){
                    var species=EntityBattleProfiles.get(id(source)).species();var state=new FloatingState();
                    var actual=VaryingModelRepository.INSTANCE.getTexture(species,state);
                    var expected=id(enabled?source.endsWith("slime")?"touhou_little_maid:textures/bedrock/entity/reimu_yukkuri.png":"touhou_little_maid:textures/bedrock/entity/marisa_yukkuri.png":source.endsWith("slime")?"minecraft:textures/entity/slime/slime.png":"minecraft:textures/entity/slime/magmacube.png");
                    require(actual.equals(expected),"Active replacement texture "+source+" "+enabled+" "+actual);
                }
            }
            LOG.info("MODEL_REPLACEMENT PASS: slime and magma cube use actual vanilla/replaced textures on both public repository paths");
        }finally{config.set(original);EntityPokemonNativeVisuals.clearResourceModels();}
    }
    private static void verifyFusionLifetime() throws Exception {
        var sample=samples.stream().filter(s->s.label().equals("Charizard + slime")).findFirst().orElseThrow();
        var before=DollModels.texture(sample.id());
        var client=Minecraft.getInstance();
        var pixels=((net.minecraft.client.renderer.texture.DynamicTexture)client.getTextureManager().getTexture(before)).getPixels();
        int[] saved=new int[pixels.getWidth()*pixels.getHeight()];
        for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)saved[y*pixels.getWidth()+x]=pixels.getPixelRGBA(x,y);
        // The form is deliberately transient; the persisted doll stores only species/aspects.
        var species=PokemonSpecies.getByIdentifier(id("cobblemon:charizard"));
        var base=species.getStandardForm();
        var factory=Class.forName("com.jhnwudi666.teamrocket.gene.FusionFormFactory");
        var formType=com.cobblemon.mod.common.pokemon.FormData.class;
        String token=(String)factory.getMethod("tokenFor",formType,int[].class,int.class,int.class)
                .invoke(null,base,new int[]{80,85,80,110,85,100},0,0);
        var types=new ArrayList<com.cobblemon.mod.common.api.types.ElementalType>();base.getTypes().forEach(types::add);
        var form=factory.getMethod("ensure",com.cobblemon.mod.common.pokemon.Species.class,formType,String.class,Map.class,List.class)
                .invoke(null,species,base,token,base.getBaseStats(),types);
        require(form!=null&&species.getForms().contains(form),"Transient fusion form created");
        require((Boolean)Class.forName("com.jhnwudi666.teamrocket.gene.GeneFormFactory")
                .getMethod("removeForm",com.cobblemon.mod.common.pokemon.Species.class,String.class).invoke(null,species,token),"Actual TeamRocket form cleanup");
        Class.forName("com.jhnwudi666.teamrocket.client.fusion.FusionTextureCache").getMethod("releaseAll").invoke(null);
        verifyDollPaths(sample);
        var after=DollModels.texture(sample.id());
        var rebuilt=((net.minecraft.client.renderer.texture.DynamicTexture)client.getTextureManager().getTexture(after)).getPixels();
        require(saved.length==rebuilt.getWidth()*rebuilt.getHeight(),"Rebuilt fusion size stable");
        for(int y=0;y<rebuilt.getHeight();y++)for(int x=0;x<rebuilt.getWidth();x++)require(saved[y*rebuilt.getWidth()+x]==rebuilt.getPixelRGBA(x,y),"Same doll rebuilds identical fusion pixels");
        LOG.info("DOLL_FUSION_LIFETIME PASS: actual removeForm/releaseAll, same persisted doll, item/block/thrown geometry and identical regenerated pixels");
    }
    private static void verifyCobbledex() throws Exception {
        var indexType=Class.forName("com.cobbledex.SpawnDataIndex");var index=indexType.getField("INSTANCE").get(null);
        indexType.getMethod("loadAll").invoke(index);
        var cacheType=Class.forName("com.cobbledex.PokemonItemCache");var cache=cacheType.getField("INSTANCE").get(null);
        var normalType=Class.forName("com.cobbledex.SpeciesNameNormalizer");var normal=normalType.getField("INSTANCE").get(null);
        var names=(List<?>)indexType.getMethod("getAllSpeciesNames").invoke(index);
        var plugin=Class.forName("com.cobbledex.jei.CobbleDexJEIPlugin").getConstructor().newInstance();
        var registrationType=Class.forName("mezz.jei.api.registration.IModIngredientRegistration");
        var ingredients=new ArrayList<Object>();
        Object registration=java.lang.reflect.Proxy.newProxyInstance(registrationType.getClassLoader(),new Class<?>[]{registrationType},(proxy,method,args)->{
            if(method.getName().equals("register")&&args[0].getClass().getName().endsWith("PokemonIngredientType"))ingredients.addAll((Collection<?>)args[1]);
            return null;
        });
        plugin.getClass().getMethod("registerIngredients",registrationType).invoke(plugin,registration);
        var ingredientType=Class.forName("com.cobbledex.jei.PokemonIngredient");var ingredientNames=new HashSet<String>();
        for(var ingredient:ingredients)ingredientNames.add((String)ingredientType.getMethod("getSpecies").invoke(ingredient));
        int active=0;
        for(var mapping:EntityBattleClientProfiles.visualSpecies().entrySet()){
            var species=PokemonSpecies.getByIdentifier(mapping.getKey());
            require(species.getImplemented(),"Active source implemented "+mapping.getKey());
            String name=(String)normalType.getMethod("normalize",String.class).invoke(normal,species.getName());
            require(names.contains(name),"Cobbledex runtime species index includes "+name);
            require(cacheType.getMethod("resolveSpecies",String.class).invoke(cache,name)==species,"Namespaced species resolved "+name);
            require(ingredientNames.contains(name),"Actual JEI registerIngredients includes "+name);
            active++;
        }
        var official=PokemonSpecies.getByIdentifier(id("cobblemon:charizard"));
        require(cacheType.getMethod("resolveSpecies",String.class).invoke(cache,"charizard")==official,"Official lookup retained");
        LOG.info("COBBLEDEX_INDEX PASS: {} active sources in actual index and JEI registration",active);
        var iconType=Class.forName("com.cobbledex.IconCapture");var icon=iconType.getField("INSTANCE").get(null);
        iconType.getMethod("init").invoke(icon);
        for(String source:List.of("minecraft:cow","twilightforest:naga","twilightforest:hydra","twilightforest:lich")){
            var species=PokemonSpecies.getByIdentifier(EntityBattleProfiles.get(id(source)).species());
            byte[] png=(byte[])iconType.getMethod("captureSpeciesToPng",String.class,Set.class,int.class)
                    .invoke(icon,species.getResourceIdentifier().toString(),Set.of(),32);
            require(png!=null&&png.length>0,"Actual Cobbledex icon capture "+source);
            try(var image=com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(png))){
                int visible=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getPixelRGBA(x,y)>>>24)>10)visible++;
                require(visible>10,"Cobbledex icon has visible pixels "+source);
            }
            java.nio.file.Files.write(Minecraft.getInstance().gameDirectory.toPath().resolve("cobbledex-"+species.getResourceIdentifier().getPath()+".png"),png);
            String label=(String)Class.forName("com.cobbledex.SpawnInfoKt").getMethod("formatSpeciesName",String.class).invoke(null,species.getName());
            require(label.equals(species.getTranslatedName().getString()),"Species translation used in viewer "+source);
        }
        iconType.getMethod("cleanup").invoke(icon);
        LOG.info("COBBLEDEX PASS: {} active sources indexed and registered through real JEI plugin, official lookup retained, four nonblank static icon captures and shared labels",active);
    }
    private static void add(String modelId,String label)throws Exception{
        var item=new ItemStack(BuiltInRegistries.ITEM.get(id("kaleidoscope_doll:custom_doll")));
        require(!item.isEmpty(),"Actual custom doll item registered");
        Class.forName("com.github.ysbbbbbb.kaleidoscopedoll.item.CustomDollItem").getMethod("setModelId",ItemStack.class,String.class).invoke(null,item,modelId);
        require(DollModels.ensure(modelId),"Doll key registered");samples.add(new Sample(modelId,label,item));
    }
    private static final class Geometry implements VertexConsumer {
        int vertices;double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        public VertexConsumer addVertex(float x,float y,float z){vertices++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("Doll integration"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int x,int y,float delta){
            if(finished)return;try{
                g.fill(0,0,width,height,0xff34404c);
                var nativeRenderer=Class.forName("com.cobblemondoll.examplemod.client.PokemonNativeRenderer");
                var active=nativeRenderer.getMethod("markActive",String.class,net.minecraft.client.renderer.MultiBufferSource.class);
                var render=nativeRenderer.getMethod("renderIfActive",PoseStack.class,net.minecraft.client.model.Model.class,VertexConsumer.class,int.class,int.class);
                for(int i=0;i<samples.size();i++){
                    var sample=samples.get(i);var model=DollModels.model(sample.id());
                    if(frames==0){
                        verifyDollPaths(sample);
                        var geometry=new Geometry();var stack=new PoseStack();stack.translate(.5,1.5,.5);stack.mulPose(Axis.ZN.rotationDegrees(180));
                        active.invoke(null,sample.id(),(net.minecraft.client.renderer.MultiBufferSource)type->geometry);
                        require((Boolean)render.invoke(null,stack,model,geometry,0xf000f0,0),"Native render active "+sample.label());
                        require(geometry.vertices>0,"Actual geometry emitted "+sample.label());
                        if(!sample.label().startsWith("Charizard"))require(Math.abs(geometry.minY)<.015,"Doll feet at base "+sample.label()+" "+geometry.minY);
                        LOG.info("DOLL_SMOKE geometry {}: {} vertices, Y {}..{}",sample.label(),geometry.vertices,geometry.minY,geometry.maxY);
                    }
                    int gx=(i%4)*width/4+width/8,gy=(i/4)*height/((samples.size()+3)/4)+15;
                    g.drawCenteredString(font,sample.label().replace("minecraft:","").replace("twilightforest:",""),gx,gy,0xffffff);
                    float zoom=sample.label().startsWith("Giant")||sample.label().startsWith("Charizard")?1.4F:2F;
                    g.pose().pushPose();g.pose().translate(gx-8*zoom,gy+40,200);g.pose().scale(zoom,zoom,zoom);g.renderItem(sample.item(),0,0);g.pose().popPose();
                }
                g.flush();if(++frames==40){
                    try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Minecraft.getInstance().gameDirectory.toPath().resolve("doll-preview.png"));}
                    audit();
                    verifyReplacementSwitch();
                    verifyFusionLifetime();
                    viewerPending=true;
                }
            }catch(Throwable failure){LOG.error("DOLL_SMOKE FAIL",failure);finished=true;Minecraft.getInstance().stop();}
        }
    }
}
