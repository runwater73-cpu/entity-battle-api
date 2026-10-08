/* Run against the unmodified Showdown extracted by the isolated Cobblemon server. */
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const engine = path.resolve(process.argv[2] || path.join(root, 'build/maid-smoke-run/showdown'));
const {Battle} = require(path.join(engine, 'sim/battle'));
const {Cobblemon} = require(path.join(engine, 'sim/cobblemon/cobblemon'));
const {Dex} = require(path.join(engine, 'sim/dex'));
const domination = 'entitybattletwilightdominion';
const covenant = 'entitybattlesoulcovenant';
for (const id of [domination, covenant]) {
    const file = path.join(root, `src/main/resources/data/entitybattle/abilities/${id}.js`);
    const definition = vm.runInThisContext(`(${fs.readFileSync(file, 'utf8').replace(/\r?\n/g, ' ')})`);
    Cobblemon.registries.ability.register(definition, id);
    assert(Cobblemon.registries.ability.get(id), `Registered ${id}`);
}
for (const name of ['lich', 'lich_minion']) {
    const json = JSON.parse(fs.readFileSync(path.join(root, `src/main/resources/data/entitybattle/species/twilightforest_${name}.json`)));
    Cobblemon.registries.species.register({
        name: 'entitybattle:' + json.name, num: json.nationalPokedexNumber,
        types: [json.primaryType, json.secondaryType].filter(Boolean),
        baseStats: {hp: json.baseStats.hp, atk: json.baseStats.attack, def: json.baseStats.defence,
            spa: json.baseStats.special_attack, spd: json.baseStats.special_defence, spe: json.baseStats.speed},
        abilities: {0: json.abilities[0]}, weightkg: json.weight / 10, heightm: json.height / 10,
    });
}
const lich = (extra = {}) => ({species: 'entitybattle:TwilightforestLich', ability: domination,
    moves: ['nightshade', 'shadowball', 'protect', 'supersonic'], level: 90, ...extra});
const guard = () => ({species: 'entitybattle:TwilightforestLichMinion', ability: covenant,
    moves: ['nightshade'], level: 90});
const ordinary = () => ({species: 'Charizard', ability: 'blaze', moves: ['nightshade'], level: 90});
function make(p1 = [ordinary(), ordinary(), ordinary()], p2 = [lich(), guard(), guard()], type = 'triples') {
    const battle = new Battle({formatid: `cobblemon${type}`, seed: [1, 2, 3, 4]});
    const withInfo = team => team.map(set => ({...set, movesInfo: set.moves.map(id => ({pp: 20, maxPp: 20}))}));
    battle.setPlayer('p1', {name: 'Attacker', team: withInfo(p1)});
    battle.setPlayer('p2', {name: 'Defender', team: withInfo(p2)});
    for (const pokemon of battle.getAllActive()) {pokemon.maxhp = 600; pokemon.hp = 600;}
    return battle;
}
let checks = 0;
function check(name, fn) {fn(); checks++; console.log('LICH_ABILITY CHECK:', name);}
const incoming = b => b.dex.moves.get('nightshade');
check('90 splits once: Lich 24, guard 30, guard 30', () => {
    const b = make(), [l, a, c] = b.sides[1].active;
    assert.equal(b.damage(90, l, b.sides[0].active[0], incoming(b)), 24);
    assert.deepEqual([l.hp, a.hp, c.hp], [576, 570, 570]);
});
check('An actual Night Shade hit damages all three and emits the Lich HP update', () => {
    const b = make(), [l, a, c] = b.sides[1].active;
    const start = b.log.length;
    b.actions.useMove('nightshade', b.sides[0].active[0], l);
    assert.deepEqual([l.hp, a.hp, c.hp], [576, 570, 570]);
    const damage = b.log.slice(start).filter(line => line.startsWith('|-damage|'));
    for (const p of [l, a, c]) assert(damage.some(line => line.includes('|'+p+'|')), `HP update for ${p}`);
});
check('One guard 36/45; no guards 72', () => {
    const b = make(), [l, a, c] = b.sides[1].active;
    c.hp = 0;
    assert.equal(b.damage(90, l, b.sides[0].active[0], incoming(b)), 36);
    assert.equal(a.hp, 555);
    a.hp = 0;
    assert.equal(b.damage(90, l, b.sides[0].active[0], incoming(b)), 72);
});
check('Direct guard attacks never split or gain Lich reduction', () => {
    const b = make(), [l, a, c] = b.sides[1].active;
    assert.equal(b.damage(90, a, b.sides[0].active[0], incoming(b)), 90);
    assert.deepEqual([l.hp, a.hp, c.hp], [600, 510, 600]);
});
check('Poison shares across all allies, reducing only the Lich allocation', () => {
    const b = make(), [l, a, c] = b.sides[1].active;
    assert.equal(b.damage(90, l, b.sides[0].active[0], b.dex.conditions.get('psn')), 24);
    assert.deepEqual([l.hp, a.hp, c.hp], [576, 570, 570]);
});
check('HP crossing is latched before immediate healing and never regains barrier', () => {
    const b = make([ordinary()], [lich()], 'singles'), l = b.sides[1].active[0];
    l.maxhp = 100; l.hp = 100;
    assert.equal(b.damage(70, l, b.sides[0].active[0], incoming(b)), 56);
    assert.equal(l.hp, 44);
    b.heal(56, l, l, b.dex.moves.get('recover'));
    assert(l.entityBattleTwilightSiphon);
    assert.equal(l.hp, 100);
    assert.equal(b.damage(20, l, b.sides[0].active[0], incoming(b)), 20);
});
check('Low-health poison shares without barrier', () => {
    const b = make(), [l, a, c] = b.sides[1].active;
    l.hp = 250;
    b.eachEvent('Update');
    assert(l.entityBattleTwilightSiphon);
    assert.equal(b.damage(20, l, b.sides[0].active[0], b.dex.conditions.get('psn')), 8);
    assert.equal(a.hp, 594); assert.equal(c.hp, 594);
});
check('Actual 35 HP overkill heals 3, not the theoretical 90 damage', () => {
    const b = make([lich()], [ordinary()], 'singles'), l = b.sides[0].active[0], t = b.sides[1].active[0];
    l.maxhp = 100; l.hp = 40; t.hp = 35;
    b.actions.useMove('nightshade', l, t);
    assert.equal(t.hp, 0); assert.equal(l.hp, 43);
});
check('Substitute absorbs an attack without healing the attacker', () => {
    const b = make([lich()], [ordinary()], 'singles'), l = b.sides[0].active[0], t = b.sides[1].active[0];
    l.maxhp = 100; l.hp = 40;
    t.addVolatile('substitute'); const hp = t.hp;
    b.actions.useMove('nightshade', l, t);
    assert.equal(t.hp, hp); assert.equal(l.hp, 40);
});
check('Indirect damage sourced from the Lich never drains', () => {
    const b = make([lich()], [ordinary()], 'singles'), l = b.sides[0].active[0], t = b.sides[1].active[0];
    l.maxhp = 100; l.hp = 40; b.eachEvent('Update');
    b.damage(90, t, l, b.dex.conditions.get('psn'));
    assert.equal(l.hp, 40);
});
check('Weather without an attacker, burn and recoil all share once', () => {
    for (const id of ['sandstorm', 'brn', 'recoil']) {
        const b = make(), [l, a, c] = b.sides[1].active;
        assert.equal(b.damage(90, l, id === 'recoil' ? l : null, b.dex.conditions.get(id)), 24);
        assert.deepEqual([l.hp, a.hp, c.hp], [576, 570, 570]);
    }
});
check('Confusion and Struggle direct damage share once without siphon healing', () => {
    for (const id of ['confusion', 'strugglerecoil']) {
        const b = make(), [l, a, c] = b.sides[1].active;
        assert.equal(b.directDamage(90, l, l, {id}), 24);
        assert.deepEqual([l.hp, a.hp, c.hp], [576, 570, 570]);
        l.hp = 250; b.eachEvent('Update');
        assert.equal(b.directDamage(90, l, l, {id}), 30);
        assert.equal(l.hp, 220);
    }
});
check('Direct cost below half latches immediately; healed Lich keeps siphon', () => {
    const b = make([ordinary()], [lich()], 'singles'), l = b.sides[1].active[0];
    l.maxhp = 100; l.hp = 100;
    assert.equal(b.directDamage(70, l, l, {id: 'strugglerecoil'}), 56);
    assert(l.entityBattleTwilightSiphon);
    b.heal(100, l, l, b.dex.moves.get('recover'));
    assert.equal(b.directDamage(20, l, l, {id: 'strugglerecoil'}), 20);
});
check('Direct damage on ordinary Pokemon and minions retains native behavior', () => {
    const b = make(), [l, a, c] = b.sides[1].active, source = b.sides[0].active[0];
    assert.equal(b.directDamage(90, source, source, {id: 'strugglerecoil'}), 90);
    assert.equal(b.directDamage(90, a, a, {id: 'confusion'}), 90);
    assert.deepEqual([l.hp, a.hp, c.hp], [600, 510, 600]);
});
check('Minion Leftovers uses native end-of-turn recovery without healing the Lich', () => {
    const b = make(), [l, a, c] = b.sides[1].active;
    l.hp = 250; a.hp = 100; a.setItem('leftovers'); c.setItem('leftovers');
    const amount = Math.floor(a.baseMaxhp / 16);
    b.residualEvent('Residual');
    assert.equal(l.hp, 250); assert.equal(a.hp, 100 + amount); assert.equal(c.hp, 600);
});
check('Heal Block and Liquid Ooze retain native drain interactions', () => {
    const b = make([lich()], [ordinary()], 'singles'), l = b.sides[0].active[0], t = b.sides[1].active[0];
    l.maxhp = 100; l.hp = 40; l.addVolatile('healblock');
    b.actions.useMove('nightshade', l, t); assert.equal(l.hp, 40);
    l.removeVolatile('healblock'); t.setAbility('liquidooze');
    b.actions.useMove('nightshade', l, t); assert.equal(l.hp, 31);
});
check('Soul transfer overkill does not move to another ally; actual loss counts for siphon', () => {
    const b = make([lich(), ordinary(), ordinary()]), source = b.sides[0].active[0], [l, a, c] = b.sides[1].active;
    source.maxhp = 100; source.hp = 40; a.hp = 1;
    b.actions.useMove('nightshade', source, l);
    assert.deepEqual([l.hp, a.hp, c.hp], [576, 0, 570]);
    assert.equal(source.hp, 45);
});
check('One-point hits do not create damage on zero-share guards', () => {
    const b = make(), [l, a, c] = b.sides[1].active;
    assert.equal(b.damage(1, l, b.sides[0].active[0], incoming(b)), 1);
    assert.deepEqual([l.hp, a.hp, c.hp], [599, 600, 600]);
});
check('Suppressed guards do not participate', () => {
    const b = make(), [l, a, c] = b.sides[1].active;
    a.addVolatile('gastroacid'); c.addVolatile('gastroacid');
    assert.equal(b.damage(90, l, b.sides[0].active[0], incoming(b)), 72);
    assert.equal(a.hp, 600); assert.equal(c.hp, 600);
});
check('Switching retains siphon; a new battle has fresh state', () => {
    const b = make([lich(), ordinary()], [ordinary()], 'singles'), side = b.sides[0], l = side.active[0];
    l.hp = 250; b.eachEvent('Update'); assert(l.entityBattleTwilightSiphon);
    b.heal(600, l, l, b.dex.moves.get('recover'));
    b.actions.switchIn(side.pokemon[1], 0); b.actions.switchIn(l, 0);
    assert(l.entityBattleTwilightSiphon);
    assert.equal(b.damage(20, l, b.sides[1].active[0], incoming(b)), 20);
    const fresh = make([lich()], [ordinary()], 'singles').sides[0].active[0];
    assert(!fresh.entityBattleTwilightSiphon);
});
Cobblemon.registries.move.register({name: 'Entity Battle Test Twin Strike', num: 19001, accuracy: true,
    pp: 10, priority: 0, type: 'Dark', category: 'Special', target: 'normal', damage: 40, multihit: 2, flags: {}}, 'entitybattletesttwinstrike');
check('Multi-hit switches phase between hits, without changing moves', () => {
    const b = make([ordinary()], [lich()], 'singles'), l = b.sides[1].active[0];
    l.maxhp = 200; l.hp = 125;
    b.actions.useMove('entitybattletesttwinstrike', b.sides[0].active[0], l);
    assert.equal(l.hp, 53); assert(l.entityBattleTwilightSiphon);
});
Cobblemon.registries.move.register({name: 'Entity Battle Test Spread', num: 19002, accuracy: true,
    pp: 10, priority: 0, type: 'Dark', category: 'Special', target: 'allAdjacentFoes', damage: 90, flags: {}}, 'entitybattletestspread');
check('Spread hits retain both native direct guard damage and their transferred allocation', () => {
    const b = make(), [l, a, c] = b.sides[1].active;
    b.actions.useMove('entitybattletestspread', b.sides[0].active[1], l);
    assert.deepEqual([l.hp, a.hp, c.hp], [576, 480, 480]);
});
console.log(`LICH_ABILITY PASS ${checks} native-engine checks`);
