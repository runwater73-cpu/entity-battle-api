package dev.entitybattle.compat;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import dev.entitybattle.api.EntityPokemonOrigin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** Version-bounded runtime wrapper; does not rewrite the third-party JAR or datapacks. */
public final class NagaTrophyCompatibility {
    private NagaTrophyCompatibility() {}
    public static void register() {
        if (ModList.get().getModContainerById("journeysouvenirs")
                .filter(mod -> mod.getModInfo().getVersion().toString().equals("0.2.53")).isEmpty()) return;
        final String script;
        try (var input = NagaTrophyCompatibility.class.getResourceAsStream(
                "/data/entitybattle/battle_compat/naga_trophy.js")) {
            if (input == null) throw new IllegalStateException("Missing Naga trophy compatibility script");
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace('\n', ' ').replace('\r', ' ');
        } catch (IOException error) { throw new IllegalStateException(error); }
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {
            if (java.util.stream.StreamSupport.stream(event.getBattle().getActors().spliterator(), false)
                    .flatMap(actor -> actor.getPokemonList().stream())
                    .anyMatch(p -> EntityPokemonOrigin.entityId(p.getOriginalPokemon())
                            .filter(id -> id.equals(ResourceLocation.parse("twilightforest:naga"))).isPresent())) {
                ShowdownService.Companion.getService().send(event.getBattle().getBattleId(), new String[]{">eval " + script});
            }
        });
    }
}
