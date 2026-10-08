/* Read the user's installed trophy script from an ignored review file; never redistribute it. */
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),engine=path.join(root,'build/boss-smoke-run/showdown');
const {Battle}=require(path.join(engine,'sim/battle'));
const {Cobblemon}=require(path.join(engine,'sim/cobblemon/cobblemon'));
const original=process.argv[2];if(!original)throw Error('Provide path to the installed 0.2.53 trophy script for read-only review');
const item=vm.runInThisContext('('+fs.readFileSync(original,'utf8').replace(/\r?\n/g,' ')+')');
Cobblemon.registries.heldItem.register(item,'nagatrophy');
Cobblemon.registries.species.register({name:'entitybattle:TwilightforestNaga',num:10036,types:['Dragon','Ground'],baseStats:{hp:160,atk:130,def:100,spa:60,spd:100,spe:100},abilities:{0:'shedskin'},weightkg:300,heightm:3.5});
function make(species='entitybattle:TwilightforestNaga'){
  const b=new Battle({formatid:'cobblemonsingles',seed:[1,2,3,4]});
  const set=(species,item)=>({species,item,moves:['protect','tackle'],movesInfo:[{pp:20,maxPp:20},{pp:20,maxPp:20}]});
  b.setPlayer('p1',{name:'Naga',team:[set(species,'Naga Trophy')]});b.setPlayer('p2',{name:'Test',team:[set('Mew','')]});return b;
}
const old=make(),p=old.sides[0].active[0],nativeItem=p.getItem();
nativeItem.onResidual.call(old,p);assert.equal(nativeItem.onBeforeMove.call(old,p),false);
nativeItem.onResidual.call(old,p);assert.equal(nativeItem.onBeforeMove.call(old,p),false);
console.log('NAGA CHECK: installed script reproduces repeated stun');
const b=make(),holder=b.sides[0].active[0];
const script=fs.readFileSync(path.join(root,'src/main/resources/data/entitybattle/battle_compat/naga_trophy.js'),'utf8');
vm.runInNewContext(script,{battle:b});const compatible=holder.getItem();
compatible.onResidual.call(b,holder);b.turn++;
assert.equal(compatible.onBeforeMove.call(b,holder),false);compatible.onResidual.call(b,holder);b.turn++;
assert.equal(compatible.onBeforeMove.call(b,holder),undefined);
assert.equal(compatible.onModifyPriority.call(b,0,holder,null,b.dex.moves.get('tackle')),1);
console.log('NAGA CHECK: our species skips one turn and resumes; native priority preserved');
const normal=make('Mew'),other=normal.sides[0].active[0],otherItem=other.getItem();
otherItem.onResidual.call(normal,other);normal.turn++;
assert.equal(otherItem.onBeforeMove.call(normal,other),false);otherItem.onResidual.call(normal,other);normal.turn++;
assert.equal(otherItem.onBeforeMove.call(normal,other),false);
console.log('NAGA PASS: wrapper does not change other species');
