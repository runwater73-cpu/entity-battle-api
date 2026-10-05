package dev.entitybattle.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;

/** Adapts Cobblemon lifecycle notifications into this mod's world bridge. */
public final class EntityBattleEvents {
    private EntityBattleEvents() {}

    public static void register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(event ->
                EntityBattleSessions.onCaptured(event.getPokemon()));
    }
}
