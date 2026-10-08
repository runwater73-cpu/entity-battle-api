const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const engine=path.resolve(process.argv[2]||path.join(root,'build/boss-smoke-run/showdown'));
const {Battle}=require(path.join(engine,'sim/battle'));
const {Cobblemon}=require(path.join(engine,'sim/cobblemon/cobblemon'));
const id='entitybattlelamentrage';
const definition=vm.runInThisContext('('+fs.readFileSync(path.join(root,`src/main/resources/data/entitybattle/abilities/${id}.js`),'utf8').replace(/\r?\n/g,' ')+')');
Cobblemon.registries.ability.register(definition,id);
function make() {
  const b=new Battle({formatid:'cobblemonsingles',seed:[1,2,3,4]});
  const set=(ability,moves)=>({species:'Mew',ability,moves,level:100,movesInfo:moves.map(id=>({pp:20,maxPp:20}))});
  b.setPlayer('p1',{name:'Attack',team:[set('synchronize',['nightshade','hypervoice','shadowball','protect'])]});
  b.setPlayer('p2',{name:'Boss',team:[set(id,['hypervoice','shadowball']),set(id,['hypervoice','shadowball'])]});
  return b;
}
let checks=0;
function check(name,fn){fn();console.log('UR_GHAST CHECK:',name);checks++;}
function hit(b,damage,move='nightshade',source=b.sides[0].active[0]) {
  const target=b.sides[1].active[0];
  const actual=b.damage(damage,target,source,b.dex.moves.get(move));
  b.runEvent('DamagingHit',target,source,b.dex.moves.get(move),actual);
  return actual;
}
check('Opening threshold is ceil(max HP/4), low HP does not trigger immediately',()=>{
  const b=make(),p=b.sides[1].active[0];
  assert.equal(p.entityBattleLamentThreshold,Math.ceil(p.maxhp/4));
  p.hp=1;assert(!p.entityBattleLamentRage);assert.equal(p.entityBattleLamentLoss,0);
});
check('Actual native move accumulates once per hit and latches at threshold',()=>{
  const b=make(),p=b.sides[1].active[0],source=b.sides[0].active[0];
  const loss=Math.floor((p.entityBattleLamentThreshold-1)/2);source.level=loss;
  b.actions.useMove('nightshade',source,p);
  assert.equal(p.entityBattleLamentLoss,loss);
  b.actions.useMove('nightshade',source,p);assert(!p.entityBattleLamentRage);
  b.actions.useMove('nightshade',source,p);assert(p.entityBattleLamentRage);
  assert.equal(p.entityBattleLamentLoss,loss*3);
});
check('Healing preserves cumulative damage and rage',()=>{
  const b=make(),p=b.sides[1].active[0],loss=Math.floor(p.entityBattleLamentThreshold/2);hit(b,loss);b.heal(p.maxhp,p,p,b.dex.moves.get('recover'));
  hit(b,p.entityBattleLamentThreshold-loss);assert(p.entityBattleLamentRage);
  b.heal(p.maxhp,p,p,b.dex.moves.get('recover'));assert(p.entityBattleLamentRage);
});
check('Poison, weather, recoil and direct HP changes do not count',()=>{
  const b=make(),p=b.sides[1].active[0];
  for(const effect of ['psn','hail']) b.damage(10,p,p,b.dex.conditions.get(effect));
  b.directDamage(10,p,p,{id:'strugglerecoil'});p.hp-=10;
  assert.equal(p.entityBattleLamentLoss,0);
});
check('Fatal overkill gives no extra HP loss or rage',()=>{
  const b=make(),p=b.sides[1].active[0];p.hp=12;assert.equal(hit(b,500),12);
  assert(!p.entityBattleLamentRage);assert.equal(p.entityBattleLamentLoss,0);
});
check('Ally/self damage does not count',()=>{
  const b=make(),p=b.sides[1].active[0];hit(b,50,'nightshade',p);assert.equal(p.entityBattleLamentLoss,0);
});
check('Substitute absorbs the native hit without counting',()=>{
  const b=make(),p=b.sides[1].active[0];p.addVolatile('substitute');p.volatiles.substitute.hp=150;
  b.actions.useMove('nightshade',b.sides[0].active[0],p);assert.equal(p.entityBattleLamentLoss,0);
});
check('Suppression prevents new count and preserves previous state',()=>{
  const b=make(),p=b.sides[1].active[0];hit(b,50);p.addVolatile('gastroacid');hit(b,100);
  assert.equal(p.entityBattleLamentLoss,50);p.removeVolatile('gastroacid');hit(b,100);assert(p.entityBattleLamentRage);
});
check('Only sound power is boosted; suppressed sound receives no boost',()=>{
  const b=make(),p=b.sides[1].active[0],foe=b.sides[0].active[0];p.entityBattleLamentRage=true;
  assert.equal(b.runEvent('BasePower',p,foe,b.dex.moves.get('hypervoice'),100),130);
  assert.equal(b.runEvent('BasePower',p,foe,b.dex.moves.get('shadowball'),100),100);
  p.addVolatile('gastroacid');assert.equal(b.runEvent('BasePower',p,foe,b.dex.moves.get('hypervoice'),100),100);
});
check('Switching preserves progress; new battle resets it',()=>{
  const b=make(),p=b.sides[1].active[0];hit(b,50);b.actions.switchIn(b.sides[1].pokemon[1],0);
  b.actions.switchIn(p,0);assert.equal(p.entityBattleLamentLoss,50);
  assert.equal(make().sides[1].active[0].entityBattleLamentLoss,0);
});
console.log(`UR_GHAST PASS ${checks} native-engine checks`);
