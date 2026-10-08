package dev.entitybattle.compat;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import java.util.concurrent.ThreadLocalRandom;

/** Keeps native AI choices within occupied Horde slots before dispatch to Showdown. */
public final class HordeAITargets {
    private HordeAITargets() {}

    public static ShowdownActionResponse correct(ActiveBattlePokemon active, PokemonBattle battle,
            ShowdownMoveset moveset, boolean switching, ShowdownActionResponse selected) {
        if (switching || active.isGone() || moveset == null
                || !battle.getFormat().getBattleType().getName().equals("horde")
                || !(selected instanceof MoveActionResponse move) || move.getTargetPnx() == null) return selected;
        var chosen = moveset.getMoves().stream().filter(candidate -> candidate.getId().equals(move.getMoveName()))
                .findFirst().orElse(null);
        if (chosen == null || chosen.mustBeUsed()) return selected;
        var targetType = move.getGimmickID() != null && chosen.getGimmickMove() != null
                && !chosen.getGimmickMove().getDisabled() ? chosen.getGimmickMove().getTarget() : chosen.getTarget();
        var legal = targetType.getTargetList().invoke(active);
        if (legal == null || legal.isEmpty()) return selected;
        var occupied = legal.stream().filter(candidate -> candidate instanceof ActiveBattlePokemon target
                && !target.isGone() && target.isAlive()).toList();
        if (occupied.stream().anyMatch(target -> target.getPNX().equals(move.getTargetPnx()))) return selected;
        // Match RandomBattleAI's enemy preference; preserve the selected move and gimmick.
        var enemies = occupied.stream().filter(target -> !active.isAllied(target)).toList();
        var candidates = enemies.isEmpty() ? occupied : enemies;
        if (candidates.isEmpty()) return PassActionResponse.INSTANCE;
        move.setTargetPnx(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).getPNX());
        return move;
    }
}
