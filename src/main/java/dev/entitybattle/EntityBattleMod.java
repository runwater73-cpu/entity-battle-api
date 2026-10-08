package dev.entitybattle;

import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonPresentation;
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider;
import dev.entitybattle.battle.EntityBattleSessions;
import dev.entitybattle.battle.EntityBattleNetwork;
import dev.entitybattle.battle.EntityBattleEvents;
import dev.entitybattle.battle.EntityPokemonWorldSpawns;
import dev.entitybattle.battle.EntityPokemonNativeRewards;
import dev.entitybattle.battle.EntityPokemonWorldBehaviors;
import dev.entitybattle.battle.EntityBossHordes;
import dev.entitybattle.battle.EntityBossEncounters;
import dev.entitybattle.battle.EntityKnightSquads;
import dev.entitybattle.battle.EntityPokemonWorldBehaviorAdapters;
import dev.entitybattle.battle.CowWorldBehaviorAdapter;
import dev.entitybattle.compat.TouhouFairyAppearance;
import dev.entitybattle.compat.VanillaMobAppearance;
import dev.entitybattle.compat.MaidHordeCompatibility;
import dev.entitybattle.compat.NagaTrophyCompatibility;
import dev.entitybattle.compat.TwilightBossAppearance;
import dev.entitybattle.battle.TwilightBossMoveEffects;
import dev.entitybattle.client.EntityBattleConfigScreen;
import dev.entitybattle.item.EntityBattleItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;

@Mod(EntityBattleMod.ID)
public final class EntityBattleMod {
    public static final String ID = "entitybattle";

    public EntityBattleMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, EntityBattleConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EntityBattleConfigScreen.register(container);
        }
        EntityBattleItems.register(modBus);
        AspectProvider.Companion.register(new EntityPokemonPresentation());
        EntityBattleProfiles.registerSpeciesAvailability();
        dev.entitybattle.battle.EntityBossSources.register();
        modBus.addListener(EntityBattleNetwork::register);
        NeoForge.EVENT_BUS.register(EntityBattleProfiles.class);
        NeoForge.EVENT_BUS.register(EntityBattleNetwork.class);
        NeoForge.EVENT_BUS.register(EntityBossEncounters.class);
        NeoForge.EVENT_BUS.register(dev.entitybattle.battle.EntityBossSources.class);
        NeoForge.EVENT_BUS.register(EntityBattleSessions.class);
        NeoForge.EVENT_BUS.register(EntityPokemonWorldSpawns.class);
        NeoForge.EVENT_BUS.register(EntityPokemonNativeRewards.class);
        NeoForge.EVENT_BUS.register(EntityPokemonWorldBehaviors.class);
        NeoForge.EVENT_BUS.register(EntityBossHordes.class);
        NeoForge.EVENT_BUS.register(EntityKnightSquads.class);
        NeoForge.EVENT_BUS.register(MaidHordeCompatibility.class);
        EntityBattleEvents.register();
        dev.entitybattle.battle.EntityPokemonEvolutions.register();
        MaidHordeCompatibility.register();
        NagaTrophyCompatibility.register();
        EntityBossEncounters.register();
        EntityPokemonNativeRewards.register();
        EntityKnightSquads.register();
        TwilightBossMoveEffects.register();
        dev.entitybattle.battle.OtherBossMoveEffects.register();
        EntityPokemonWorldBehaviorAdapters.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "cow"),
                new CowWorldBehaviorAdapter());
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            VanillaMobAppearance.register();
            TwilightBossAppearance.register();
            TouhouFairyAppearance.register();
        }));
    }
}
