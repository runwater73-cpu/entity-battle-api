/* Approved 2026-10-07 progression; keeps species stats, abilities and learnsets intact. */
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),res=path.join(root,'src/main/resources/data/entitybattle');
const tiers={
 'twilightforest:naga':25,'twilightforest:lich':30,
 'twilightforest:minoshroom':35,'twilightforest:knight_phantom':35,'twilightforest:alpha_yeti':35,
 'twilightforest:hydra':40,'twilightforest:ur_ghast':40,'twilightforest:snow_queen':40,
 'kaleidoscope_twilight:umbral_sunflower':45,
 'aether:slider':55,'aether:valkyrie_queen':60,'aether:sun_spirit':65,
 'minecraft:warden':70,'minecraft:wither':75,
};
let seen=0;
for(const file of ['pack_mobs','vanilla_mobs']){
 const p=path.join(res,'battle_profiles',file+'.json'),rows=JSON.parse(fs.readFileSync(p,'utf8'));
 for(const row of rows){
  const level=tiers[row.entity];
  if(level){
   assert(row.boss);row.level={min:level-2,max:level+2};
   const species=JSON.parse(fs.readFileSync(path.join(res,'species',row.species.split(':')[1]+'.json'),'utf8'));
   row.moves=species.moves.filter(m=>/^\d+:/.test(m)).map(m=>m.split(':')[1]).slice(-4);
   assert.equal(row.moves.length,4,row.entity);seen++;
   if(row.bossBattle && row.bossBattle.mode==='horde'){
    const minion=rows.find(r=>r.entity===row.bossBattle.minionEntity);
    assert(minion,'Minion profile '+row.entity);
    const ms=JSON.parse(fs.readFileSync(path.join(res,'species',minion.species.split(':')[1]+'.json'),'utf8'));
    row.bossBattle.minionMoves=ms.moves.filter(m=>/^\d+:/.test(m)).map(m=>m.split(':')[1]).slice(-4);
   }
  }else if(row.entity==='twilightforest:lich_minion'){row.level={min:28,max:32};}
 }
 fs.writeFileSync(p,JSON.stringify(rows,null,2)+'\n');
}
assert.equal(seen,13);
console.log('BOSS_PROGRESSION PASS: 13 Boss ranges, preserved four-move kits and Lich minion level.');
const sunflower=JSON.parse(fs.readFileSync(path.join(res,'battle_profiles/sunflower_boss.json'),'utf8'));
assert(sunflower.boss && sunflower.level.min===tiers[sunflower.entity]-2 && sunflower.level.max===tiers[sunflower.entity]+2);
assert.equal(sunflower.moves.length,4);
console.log('SUNFLOWER_PROGRESSION PASS: level 45 +/- 2, standalone optional Boss profile.');
