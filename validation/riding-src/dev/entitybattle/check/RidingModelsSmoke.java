package dev.entitybattle.check;

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.LocatorAccess;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.riding.RidingProperties;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real client Bedrock baking and poser construction across the generated overlay. */
@EventBusSubscriber(modid = "entitybattle", value = Dist.CLIENT)
public final class RidingModelsSmoke {
    private static boolean finished;
    private static boolean seat(LocatorAccess access) {
        return access != null && (access.getLocators().containsKey("seat_1") || access.getChildren().stream().anyMatch(RidingModelsSmoke::seat));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (finished || !(client.screen instanceof TitleScreen)) return;
        finished = true;
        var log = com.mojang.logging.LogUtils.getLogger();
        try {
            var report = JsonParser.parseString(Files.readString(client.gameDirectory.toPath().resolve("riding-report.json"))).getAsJsonObject();
            var repo = VaryingModelRepository.INSTANCE;
            var targets = new HashSet<ResourceLocation>();
            for (var entry : report.getAsJsonArray("成功模型")) {
                String name = entry.getAsJsonObject().get("模型").getAsString();
                String namespace = name.split("/")[1];
                var resource = client.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(
                        namespace, name.substring(("assets/" + namespace + "/").length()))).orElseThrow();
                if (!resource.sourcePackId().equals("file/865995骑乘修复包.zip"))
                    throw new AssertionError("Overlay not applied: " + name + " from " + resource.sourcePackId());
                String file = name.substring(name.lastIndexOf('/') + 1, name.length() - ".json".length());
                var id = ResourceLocation.fromNamespaceAndPath(namespace, file);
                var model = repo.getTexturedModels().get(id);
                if (model == null || !seat(LocatorAccess.Companion.resolve(model))) throw new AssertionError("Missing baked seat: " + id);
                targets.add(id);
            }
            int pairs = 0, poses = 0;
            var used = new HashSet<ResourceLocation>();
            var checked = new HashSet<String>();
            var failures = new ArrayList<String>();
            for (var resolver : repo.getVariations().values()) {
                for (var variation : resolver.getVariations()) {
                    var modelId = variation.getModel();
                    if (modelId == null || !targets.contains(modelId)) continue;
                    var state = new FloatingState();
                    state.setCurrentAspects(variation.getAspects());
                    var poserId = variation.getPoser() == null ? resolver.getResolvedPoser(state) : variation.getPoser();
                    if (!checked.add(poserId + "|" + modelId)) continue;
                    var factory = repo.getPosers().get(poserId);
                    if (factory == null) {
                        failures.add("Missing actual poser: " + poserId + " for " + modelId);
                        continue;
                    }
                    var model = factory.invoke(repo.getTexturedModels().get(modelId));
                    model.initializeLocatorAccess();
                    model.registerPoses();
                    state.setCurrentModel(model);
                    for (var pose : model.getPoses().values()) {
                        model.setDefault();
                        model.applyPose(state, pose, 1F);
                        poses++;
                    }
                    used.add(modelId);
                    pairs++;
                }
            }
            if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
            for (String name : List.of("fletchling.geo", "fletchinder.geo", "sandshrew.geo", "maushold_three.geo", "maushold_four.geo"))
                if (!used.contains(ResourceLocation.fromNamespaceAndPath("cobblemon", name))) throw new AssertionError("Regression not exercised: " + name);
            int rideData = 0;
            try (var overlay = new ZipFile(client.gameDirectory.toPath().resolve("resourcepacks/865995骑乘修复包.zip").toFile())) {
                for (var entry : Collections.list(overlay.entries())) {
                    if (!entry.getName().startsWith("data/") || !entry.getName().endsWith(".json")) continue;
                    try (var reader = new InputStreamReader(overlay.getInputStream(entry), StandardCharsets.UTF_8)) {
                        var addition = JsonParser.parseReader(reader).getAsJsonObject();
                        var riding = PokemonSpecies.INSTANCE.getGson().fromJson(addition.get("riding"), RidingProperties.class);
                        if (riding == null || riding.getSeats().isEmpty() || riding.getBehaviours().isEmpty()
                                || riding.getBehaviours().values().stream().anyMatch(Objects::isNull))
                            throw new AssertionError("Invalid native riding settings: " + entry.getName());
                        rideData++;
                    }
                }
            }
            if (rideData != report.get("保留数据文件").getAsInt()) throw new AssertionError("Riding data coverage mismatch");
            log.info("RIDING_DATA PASS: {} species additions deserialize with Cobblemon 1.8.1's own riding adapters", rideData);
            log.info("RIDING_MODELS PASS: {} geometry files retain baked seats, {} actual poser/model pairs construct, {} pose transformations apply; prior Fletchling/Fletchinder/Sandshrew/Maushold regressions exercised", targets.size(), pairs, poses);
        } catch (Throwable failure) { log.error("RIDING_MODELS FAIL", failure); }
        client.stop();
    }
}
