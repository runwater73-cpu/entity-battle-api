package dev.entitybattle.api;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

/** Ally-only target policy after native AI selects a support move, without phase rules. */
public final class EntityBattleSupportMoves {
    private static final Set<String> ALLY_HEALING = ConcurrentHashMap.newKeySet();
    static {ALLY_HEALING.add("healpulse");}
    private EntityBattleSupportMoves() {}

    /** Register an existing move whose NPC target should be a living ally other than itself. */
    public static void registerAllyHealingMove(String moveId) {
        String id = Objects.requireNonNull(moveId).toLowerCase(Locale.ROOT);
        if (id.isBlank()) throw new IllegalArgumentException("Move ID cannot be blank");
        ALLY_HEALING.add(id);
    }

    public static ShowdownActionResponse correct(BattleAI ai, ActiveBattlePokemon active,
            PokemonBattle battle, BattleSide side, ShowdownMoveset moveset, boolean switching,
            ShowdownActionResponse selected) {
        if (switching || active.isGone() || active.getBattlePokemon() == null || moveset == null
                || !(selected instanceof MoveActionResponse move) || !ALLY_HEALING.contains(move.getMoveName())) return selected;
        var pokemon = active.getBattlePokemon().getOriginalPokemon();
        if (!pokemon.isWild() || EntityPokemonOrigin.entityId(pokemon).map(EntityBattleProfiles::get).orElse(null) == null) return selected;
        var legal = moveset.getMoves().stream().filter(candidate -> candidate.getId().equals(move.getMoveName()))
                .findFirst().orElse(null);
        if (legal == null) return selected;
        var targets = legal.getTarget().getTargetList().invoke(active);
        var ally = StreamSupport.stream(active.getAllActivePokemon().spliterator(), false)
                .filter(candidate -> candidate != active && !candidate.isGone()
                        && candidate.getBattlePokemon() != null && candidate.getBattlePokemon().getHealth() > 0
                        && active.isAllied(candidate) && targets != null && targets.contains(candidate))
                .min(Comparator.comparingDouble(candidate -> (double) candidate.getBattlePokemon().getHealth()
                        / candidate.getBattlePokemon().getMaxHealth())).orElse(null);
        if (ally != null) {
            move.setTargetPnx(ally.getPNX());
            return move;
        }
        var alternatives = moveset.getMoves().stream()
                .filter(candidate -> !ALLY_HEALING.contains(candidate.getId()) && candidate.canBeUsed()).toList();
        if (alternatives.isEmpty() || legal.mustBeUsed()) return PassActionResponse.INSTANCE;
        var fallback = new ShowdownMoveset();
        fallback.setMoves(alternatives);
        return ai.choose(active, battle, side, fallback, false);
    }
}
