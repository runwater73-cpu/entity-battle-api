/* Cobblemon flattens newlines. Keep comments block-style. State belongs to the battle Pokemon. */
{
    name: "Entity Battle Lament Rage",
    rating: 3,
    flags: {breakable: 1},
    onStart(pokemon) {
        if (pokemon.entityBattleLamentLoss === undefined) {
            pokemon.entityBattleLamentLoss = 0;
            pokemon.entityBattleLamentThreshold = Math.ceil(pokemon.maxhp / 4);
        }
        this.add('-ability', pokemon, 'entitybattlelamentrage');
    },
    onDamagingHit(damage, target, source, move) {
        if (!target.hp || target.entityBattleLamentRage || !(damage > 0)
                || !source || source.isAlly(target) || move.category === 'Status') return;
        target.entityBattleLamentLoss += damage;
        if (target.entityBattleLamentLoss >= target.entityBattleLamentThreshold) {
            target.entityBattleLamentRage = true;
            this.add('-activate', target, 'ability: entitybattlelamentrage');
        }
    },
    onBasePowerPriority: 8,
    onBasePower(basePower, pokemon, target, move) {
        if (pokemon.entityBattleLamentRage && move.flags.sound) return this.chainModify([5325, 4096]);
    }
}
