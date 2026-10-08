/* Loaded by Cobblemon's ability registry. Use block comments: its loader flattens newlines. */
{
    name: "Entity Battle Twilight Dominion",
    rating: 4,
    flags: {breakable: 1},
    entityBattleEnterSiphon(pokemon) {
        if (!pokemon.entityBattleTwilightSiphon && pokemon.hp > 0 && pokemon.hp * 2 <= pokemon.maxhp) {
            pokemon.entityBattleTwilightSiphon = true;
            this.add('-activate', pokemon, 'ability: entitybattletwilightdominion', '[msg] siphon');
        }
    },
    onStart(pokemon) {
        this.dex.abilities.get('entitybattlesoulcovenant').entityBattleInstallDirectDamage.call(this);
        this.dex.abilities.get('entitybattletwilightdominion').entityBattleEnterSiphon.call(this, pokemon);
        if (!pokemon.entityBattleTwilightSiphon) {
            this.add('-ability', pokemon, 'entitybattletwilightdominion');
        }
    },
    onUpdate(pokemon) {
        this.dex.abilities.get('entitybattletwilightdominion').entityBattleEnterSiphon.call(this, pokemon);
    },
    /* Runs after soul sharing. Only the Lich's remaining allocation is reduced. */
    onDamagePriority: -100,
    onDamage(damage, target, source, effect) {
        this.dex.abilities.get('entitybattletwilightdominion').entityBattleEnterSiphon.call(this, target);
        if (!target.entityBattleTwilightSiphon && damage > 0) {
            return Math.max(1, Math.floor(damage * 0.8));
        }
    },
    onDamagingHit(damage, target) {
        this.dex.abilities.get('entitybattletwilightdominion').entityBattleEnterSiphon.call(this, target);
    },
    /* A berry or another heal must not erase a half-health crossing before Update. */
    onTryHeal(damage, target) {
        this.dex.abilities.get('entitybattletwilightdominion').entityBattleEnterSiphon.call(this, target);
    },
    onModifyMove(move, pokemon) {
        this.dex.abilities.get('entitybattletwilightdominion').entityBattleEnterSiphon.call(this, pokemon);
        move.entityBattleSiphonAttack = !!pokemon.entityBattleTwilightSiphon;
    },
    onSourceDamagingHit(damage, target, source, move) {
        if (!move.entityBattleSiphonAttack || move.category === 'Status' || !source.hp
                || source.isAlly(target) || !(damage > 0)) return;
        const transferred = move.entityBattleSoulLoss && move.entityBattleSoulLoss.get(target) || 0;
        if (move.entityBattleSoulLoss) move.entityBattleSoulLoss.delete(target);
        const amount = Math.floor((damage + transferred) / 10);
        if (amount > 0) this.heal(amount, source, target, 'drain');
    }
}
