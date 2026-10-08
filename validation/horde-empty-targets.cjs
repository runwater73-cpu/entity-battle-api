/* Read-only reproduction against the engine extracted by the isolated server. */
const path = require('node:path');
const assert = require('node:assert/strict');
const engine = path.resolve(process.argv[2] || path.join(__dirname, '../build/maid-smoke-run/showdown'));
const {Battle} = require(path.join(engine, 'sim/battle'));
function make() {
    const b = new Battle({format: {name: 'Cobblemon Horde', mod: 'cobblemon', gameType: 'horde', ruleset: []}, seed: [1, 2, 3, 4]});
    const set = moves => ({species: 'Charizard', ability: 'blaze', level: 35,
        moves, movesInfo: moves.map(id => ({pp: 20, maxPp: 20}))});
    b.setPlayer('p1', {name: 'Player', team: [set(['protect']), set(['protect'])]});
    b.setPlayer('p2', {name: 'Horde', team: Array.from({length: 3}, () => set(['protect']))});
    b.setPlayer('p3', {name: 'Maid', team: [set(['airslash', 'dragonpulse'])]});
    const owner = b.sides[0], ally = b.sides[2];
    ally.foe = b.sides[1]; owner.allySide = ally; ally.allySide = owner;
    ally.sideConditions = owner.sideConditions;
    const activeTeam = () => owner.active.concat(ally.active);
    owner.activeTeam = activeTeam; ally.activeTeam = activeTeam;
    ally.active = [null];
    for (const pokemon of ally.pokemon) b.initPokemon(pokemon);
    b.actions.switchIn(ally.pokemon[0], 0);
    ally.emitRequest(b.getRequests('move')[2]);
    return b;
}
for (const move of ['airslash', 'dragonpulse']) {
    const bad = make();
    bad.choose('p1', 'switch 2');
    bad.choose('p3', `move ${move} +4`);
    assert.throws(() => bad.choose('p2', 'move protect, move protect, move protect'), TypeError);
    assert.equal(bad.turn, 1);
    console.log(`HORDE_TARGET REPRODUCED: ${move} +4 throws on missing side, turn stays 1`);
    for (const location of [1, 2, 3]) {
        const good = make();
        good.choose('p1', 'switch 2');
        good.choose('p3', `move ${move} +${location}`);
        good.choose('p2', 'move protect, move protect, move protect');
        assert.equal(good.turn, 2);
    }
}
console.log('HORDE_TARGET PASS: both empty-target failures reproduced; all 6 occupied-target controls reach turn 2');
