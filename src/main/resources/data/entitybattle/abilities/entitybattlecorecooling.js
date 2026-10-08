/* Cobblemon flattens this file: use block comments only. Per-Pokemon flags last for this battle. */
{
    name: "Entity Battle Core Cooling",
    rating: 4,
    flags: {breakable: 1},
    onStart(pokemon) {
        if (pokemon.entityBattleCoreOpened) return;
        pokemon.entityBattleCoreOpened = true;
        if (!pokemon.entityBattleCoreCooled) this.field.setWeather('sunnyday', pokemon);
    },
    onDamagingHit(damage, target, source, move) {
        if (target.entityBattleCoreCooled || !(damage > 0) || !target.hp || !source
                || source.isAlly(target) || move.effectType !== 'Move' || move.category === 'Status'
                || move.type !== 'Ice') return;
        target.entityBattleCoreCooled = true;
        this.add('-activate', target, 'ability: entitybattlecorecooling');
        if (this.field.weather === 'sunnyday') this.field.clearWeather();
        this.boost({spa: -1, spe: -1}, target, target);
    }
}
