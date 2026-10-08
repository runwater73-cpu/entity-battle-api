/* Approved 2026-10-06 design. Changes source resources, never a modpack's files. */
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const rows = {
  naga: [28,32,[160,130,100,60,100,100],'shedskin',['1:bodyslam','12:crunch','20:rockslide','28:breakingswipe'],'hedge_spider',-5,'silver_powder'],
  minoshroom: [38,42,[155,135,110,50,95,55],'intimidate',['1:highhorsepower','15:hammerarm','25:woodhammer','35:rockslide'],'minotaur',-5,'black_belt'],
  hydra: [43,47,[180,125,110,140,110,65],'multiscale',['1:crunch','18:flamethrower','30:heatwave','40:fireblast'],'fire_beetle',-7,'charcoal_stick'],
  knight_phantom: [43,47,[75,85,90,35,70,45],'levitate',['1:shadowclaw','12:ironhead','24:sacredsword','36:protect']],
  ur_ghast: [48,52,[200,60,105,145,120,70],'entitybattlelamentrage',['1:shadowball','18:hypervoice','30:fireblast','45:heatwave'],'carminite_ghastling',-5,'wise_glasses'],
  alpha_yeti: [48,52,[155,135,110,50,85,50],'intimidate',['1:stompingtantrum','18:stormthrow','30:avalanche','42:icehammer'],'yeti',-5,'never_melt_ice'],
  snow_queen: [53,57,[140,55,95,135,110,90],'snowwarning',['1:icebeam','18:dazzlinggleam','35:auroraveil','50:blizzard'],'ice_crystal',-5,'never_melt_ice'],
  hedge_spider: [23,27,[55,60,50,35,45,60],'poisonpoint',['1:bugbite','8:stringshot','14:poisonfang','20:lunge']],
  minotaur: [33,37,[75,85,65,30,55,50],'battlearmor',['1:stomp','12:brickbreak','22:highhorsepower','30:protect']],
  fire_beetle: [36,40,[55,45,65,60,50,40],'flamebody',['1:ember','10:strugglebug','22:incinerate','32:willowisp']],
  carminite_ghastling: [43,47,[45,25,40,70,50,85],'levitate',['1:ember','12:confuseray','24:hex','36:flameburst']],
  yeti: [43,47,[70,75,60,30,50,45],'thickfat',['1:stomp','12:icepunch','24:bulkup','36:iceshard']],
  ice_crystal: [48,52,[60,25,45,75,65,70],'levitate',['1:aurorabeam','12:confuseray','24:protect','42:icywind']],
};
const keys = ['hp','attack','defence','special_attack','special_defence','speed'];
const profileFile = path.join(root,'src/main/resources/data/entitybattle/battle_profiles/pack_mobs.json');
const profiles = JSON.parse(fs.readFileSync(profileFile,'utf8'));
for (const [id, [min,max,stats,ability,moves,minion,offset,item]] of Object.entries(rows)) {
  const file = path.join(root,`src/main/resources/data/entitybattle/species/twilightforest_${id}.json`);
  const species = JSON.parse(fs.readFileSync(file,'utf8'));
  species.baseStats = Object.fromEntries(keys.map((key,i)=>[key,stats[i]]));
  species.abilities = [ability];
  species.moves = moves;
  fs.writeFileSync(file,JSON.stringify(species,null,2)+'\n');
  const profile = profiles.find(p=>p.entity===`twilightforest:${id}`);
  if (!profile) throw Error(`Missing profile ${id}`);
  profile.level = {min,max};
  if (minion) profile.bossBattle = {mode:'horde',minionEntity:`twilightforest:${minion}`,minionCount:2,levelOffset:offset,minionHeldItem:item.includes(':')?item:`cobblemon:${item}`};
  if (id==='knight_phantom') profile.bossBattle = {mode:'existing_squad',minionEntity:'twilightforest:knight_phantom',minionCount:5,levelOffset:0,minionHeldItem:'cobblemon:metal_coat'};
}
/* Lich combat data is frozen. Only the encounter equipment field is explicit. */
const lich = profiles.find(p=>p.entity==='twilightforest:lich');
lich.bossBattle.minionHeldItem = 'cobblemon:leftovers';
fs.writeFileSync(profileFile,JSON.stringify(profiles,null,2)+'\n');
console.log(`Applied ${Object.keys(rows).length} species and all eight encounter rosters.`);
