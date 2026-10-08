/* Soul transfer is an Ability damage event, never another move hit. */
{
    name: "Entity Battle Soul Covenant",
    rating: 3,
    flags: {breakable: 1},
    /* Native directDamage bypasses Damage events (confusion, Struggle, crash costs).
       Route only an affected Lich through native events, on this battle instance. */
    entityBattleInstallDirectDamage() {
        if (this.entityBattleDirectDamageRouted) return;
        const original = this.directDamage;
        this.directDamage = function (damage, target, source, effect) {
            target = target || this.event && this.event.target;
            source = source || this.event && this.event.source;
            effect = effect || this.event && this.effect;
            if (!(damage > 0) || !target || !target.hp
                    || target.baseSpecies.id !== 'entitybattletwilightforestlich'
                    || !target.hasAbility('entitybattletwilightdominion')
                        && !this.getAllActive().some(p => p !== target && p.hp > 0 && !p.fainted
                            && p.isActive && p.isAlly(target) && p.hasAbility('entitybattlesoulcovenant'))) {
                return original.call(this, damage, target, source, effect);
            }
            if (typeof effect === 'string' || !effect) effect = this.dex.conditions.getByID(effect || '');
            const allocation = this.runEvent('Damage', target, source, effect, this.clampIntRange(damage, 1));
            if (!allocation) return allocation;
            const actual = original.call(this, allocation, target, source, effect);
            if (target.hasAbility('entitybattletwilightdominion')) {
                this.dex.abilities.get('entitybattletwilightdominion').entityBattleEnterSiphon.call(this, target);
            }
            return actual;
        };
        this.entityBattleDirectDamageRouted = true;
    },
    onStart() {
        this.dex.abilities.get('entitybattlesoulcovenant').entityBattleInstallDirectDamage.call(this);
    },
    onAnyDamagePriority: 100,
    onAnyDamage(damage, target, source, effect) {
        if (!(damage > 0) || !target || target.baseSpecies.id !== 'entitybattletwilightforestlich'
                || effect && effect.id === 'entitybattlesoulcovenant'
                || this.event.entityBattleSoulTarget === target) return;
        const guards = this.getAllActive().filter(pokemon => pokemon !== target
                && pokemon.hp > 0 && !pokemon.fainted && pokemon.isActive
                && pokemon.isAlly(target) && pokemon.hasAbility('entitybattlesoulcovenant'));
        /* Each guard sees the same event; one stable leader performs the entire split. */
        if (!guards.length || guards[0] !== this.effectState.target) return;
        /* Keep the event marked even if its first guard faints during this allocation. */
        this.event.entityBattleSoulTarget = target;
        const share = Math.floor(damage / (guards.length + 1));
        let transferred = 0;
        for (const guard of guards) {
            if (share > 0) transferred += this.damage(share, guard, source, this.effect) || 0;
        }
        if (effect && effect.effectType === 'Move' && effect.entityBattleSiphonAttack) {
            if (!effect.entityBattleSoulLoss) effect.entityBattleSoulLoss = new Map();
            effect.entityBattleSoulLoss.set(target, transferred);
        }
        /* Keep the protocol effect ID ASCII so Cobblemon can parse it.  The
           translation key below supplies the Chinese battle text. */
        this.add('-activate', guards[0], 'ability: entitybattlesoulcovenant', '[of] ' + target);
        /* Integer remainder stays with the Lich; fainted guards' overkill is not reassigned. */
        return damage - share * guards.length;
    }
}
