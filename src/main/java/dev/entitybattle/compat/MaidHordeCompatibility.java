package dev.entitybattle.compat;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Repairs the dynamic ally handshake, then returns decisions to TeamRocket/Cobblemon. */
public final class MaidHordeCompatibility {
    private static final Gson JSON = new Gson();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private MaidHordeCompatibility() {}

    public static void register() {
        // Run after the mod has chosen its maid and added the Java-side actor.
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.LOWEST,
                (java.util.function.Consumer<com.cobblemon.mod.common.api.events.battles.BattleStartedEvent.Post>)
                        event -> prepare(event.getBattle()));
    }

    public static void prepare(PokemonBattle battle) {
        if (battle.getEnded() || !battle.getFormat().getBattleType().getName().equals("horde")
                || !ModList.get().getModContainerById("teamrocket")
                .map(mod -> mod.getModInfo().getVersion().toString().equals("1.1.6")).orElse(false)) return;
        UUID battleId = battle.getBattleId();
        if (PENDING.containsKey(battleId)) return;
        var actor = battle.getActor("p3");
        if (!(actor instanceof AIBattleActor maid)
                || !actor.getClass().getName().equals("com.jhnwudi666.teamrocket.maid.MaidBattleSupport$MaidAsPlayerActor")) {
            return;
        }
        // ShowdownSide.name is a UUID in Cobblemon. Display names stay on the actor.
        String team = BattleRegistry.INSTANCE.packTeam(maid.getPokemonList());
        String player = ">player p3 " + JSON.toJson(Map.of("name", maid.getUuid().toString(), "team", team));
        // The original turn can precede p3's late request. Keep input pending until
        // that request is installed; otherwise checkForInputDispatch clears it.
        maid.setMustChoose(true);
        PENDING.put(battleId, new Pending(battle, maid));
        battle.getOnEndHandlers().add(ended -> {PENDING.remove(battleId);return kotlin.Unit.INSTANCE;});
        // Horde's native helpers know one allied actor. Link the additional side
        // so spread targets, ally moves and victory counting include the maid.
        String initialize = """
                >eval (() => {
                    const owner = battle.sides[0], ally = battle.sides[2];
                    if (ally.entityBattleReady) return;
                    ally.entityBattleReady = true;
                    ally.foe = battle.sides[1];
                    owner.allySide = ally;
                    ally.allySide = owner;
                    ally.sideConditions = owner.sideConditions;
                    const activeTeam = () => owner.active.concat(ally.active);
                    owner.activeTeam = activeTeam;
                    ally.activeTeam = activeTeam;
                    ally.active = [null];
                    for (const pokemon of ally.pokemon) battle.initPokemon(pokemon);
                    const checkWin = battle.checkWin;
                    battle.checkWin = function(faintData) {
                        if (!owner.pokemonLeft && ally.pokemonLeft && !this.sides[1].pokemonLeft) {
                            return this.win(ally);
                        }
                        return checkWin.call(this, faintData);
                    };
                    battle.actions.switchIn(ally.pokemon[0], 0);
                    ally.emitRequest(battle.getRequests("move")[2]);
                })();
                """.replace('\n', ' ');
        ShowdownService.Companion.getService().send(battleId, new String[]{player, initialize});
        LogUtils.getLogger().info("Registered TeamRocket maid horde ally through Showdown API in battle {}", battleId);
    }

    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) {
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var pending = entry.getValue();
            var request = pending.maid.getRequest();
            if (pending.battle.getEnded() || pending.battle.getTurn() > 1
                    || !pending.maid.getResponses().isEmpty()
                    || (pending.battle.getTurn() > 0 && request == null && !pending.maid.getMustChoose())) {
                iterator.remove();
            } else if (pending.battle.getTurn() > 0 && request != null && !request.getWait()
                    && request.getActive() != null && !request.getActive().isEmpty()) {
                // Native turn() sends the choice notification; the maid's existing
                // AI chooses normally. This runs once, never once per round.
                pending.maid.turn();
                iterator.remove();
                LogUtils.getLogger().info("Completed maid horde action handshake in battle {}", entry.getKey());
            } else if (++pending.ticks > 400) {
                iterator.remove();
                LogUtils.getLogger().warn("Maid horde handshake timed out in battle {}; ending incomplete battle", entry.getKey());
                pending.battle.stop();
            }
        }
    }

    @SubscribeEvent public static void onStopping(ServerStoppingEvent event) {PENDING.clear();}
    private static final class Pending {
        final PokemonBattle battle;
        final AIBattleActor maid;
        int ticks;
        Pending(PokemonBattle battle, AIBattleActor maid) {this.battle=battle;this.maid=maid;}
    }
}
