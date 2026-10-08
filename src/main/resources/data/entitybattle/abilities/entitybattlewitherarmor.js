/* Actual direct damage latches a crossing, but armour starts only after the entire move. */
{
    name: "Entity Battle Wither Armor",
    rating: 4,
    flags: {breakable: 1},
    entityBattleEnterArmor(pokemon) {
        if (!pokemon.hp || pokemon.entityBattleWitherArmor) return;
        if (!pokemon.entityBattleWitherCrossed && pokemon.hp * 2 > pokemon.maxhp) return;
        pokemon.entityBattleWitherArmor = true;
        pokemon.entityBattleWitherCrossed = false;
        this.add('-activate', pokemon, 'ability: entitybattlewitherarmor');
        this.boost({def: 1}, pokemon, pokemon);
    },
    onStart(pokemon) {
        this.dex.abilities.get('entitybattlewitherarmor').entityBattleEnterArmor.call(this, pokemon);
    },
    onDamage(damage, target, source, effect) {
        target.entityBattleWitherMove = effect && effect.effectType === 'Move' && effect.category !== 'Status'
                ? this.activeMove : null;
    },
    onDamagingHit(damage, target) {
        if (damage > 0 && target.hp > 0 && target.hp * 2 <= target.maxhp) target.entityBattleWitherCrossed = true;
    },
    onTryHeal(damage, target) {
        if (target.hp > 0 && target.hp * 2 <= target.maxhp) target.entityBattleWitherCrossed = true;
        if (!target.entityBattleWitherMove || target.entityBattleWitherMove !== this.activeMove)
            this.dex.abilities.get('entitybattlewitherarmor').entityBattleEnterArmor.call(this, target);
    },
    onUpdate(pokemon) {
        if (pokemon.entityBattleWitherMove && pokemon.entityBattleWitherMove === this.activeMove) return;
        this.dex.abilities.get('entitybattlewitherarmor').entityBattleEnterArmor.call(this, pokemon);
    },
    onAnyAfterMove() {
        const pokemon = this.effectState.target;
        pokemon.entityBattleWitherMove = null;
        this.dex.abilities.get('entitybattlewitherarmor').entityBattleEnterArmor.call(this, pokemon);
    },
    onTryHit(target, source, move) {
        if (target.entityBattleWitherArmor && move.flags.bullet)
            return this.dex.abilities.get('bulletproof').onTryHit.call(this, target, source, move);
    }
}
