package dev.entitybattle.check;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonData;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Optional sources absent: only base Cobblemon and its mandatory dependencies. */
@EventBusSubscriber(modid="entitybattle")
public final class SourceAvailabilitySmoke {
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        var log=com.mojang.logging.LogUtils.getLogger();
        try {
            for (String source:List.of("deep_aether:eots_controller","kaleidoscope_twilight:umbral_sunflower","twilightforest:quest_ram")) {
                if(EntityBattleProfiles.get(ResourceLocation.parse(source))!=null)throw new AssertionError("Missing source retained profile: "+source);
                var species=PokemonSpecies.getByIdentifier(ResourceLocation.parse("entitybattle:"+source.replace(':','_')));
                if(species==null||species.getImplemented())throw new AssertionError("Missing source should be unavailable: "+source);
            }
            var cow=EntityType.COW.create(event.getServer().overworld());
            var pokemon=EntityPokemonData.getOrCreate(cow,EntityBattleProfiles.get(EntityType.COW));
            if(pokemon==null||!pokemon.getSpecies().getImplemented())throw new AssertionError("Vanilla profile unavailable");
            log.info("SOURCE_AVAILABILITY PASS: no optional source classes required, absent species excluded, vanilla profile usable");
        }catch(Throwable failure){log.error("SOURCE_AVAILABILITY FAIL",failure);}
        event.getServer().halt(false);
    }
}
