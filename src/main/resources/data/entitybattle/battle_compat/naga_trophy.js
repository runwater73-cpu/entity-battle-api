/* Narrow compatibility wrapper for Journeysouvenirs 0.2.53; no third-party implementation copied. */
(() => {
    const matches = pokemon => pokemon.species.id === 'entitybattletwilightforestnaga';
    for (const pokemon of battle.getAllActive()) {
        if (!matches(pokemon)) continue;
        const item = battle.dex.items.get(pokemon.item);
        if (item.name !== 'Naga Trophy' || item.entityBattleRecoveryWrapped
                || !item.onBeforeMove || !item.onResidual) continue;
        const beforeMove = item.onBeforeMove;
        const residual = item.onResidual;
        item.onBeforeMove = function (holder, ...args) {
            const result = beforeMove.call(this, holder, ...args);
            if (matches(holder) && result === false) holder.entityBattleNagaRestTurn = this.turn;
            return result;
        };
        item.onResidual = function (holder, ...args) {
            if (matches(holder) && holder.entityBattleNagaRestTurn === this.turn) {
                delete holder.entityBattleNagaRestTurn;
                return;
            }
            return residual.call(this, holder, ...args);
        };
        item.entityBattleRecoveryWrapped = true;
    }
})();
