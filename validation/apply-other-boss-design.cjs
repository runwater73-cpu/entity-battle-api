/* Approved 2026-10-07 designs. All writes stay in source resources. */
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),res=path.join(root,'src/main/resources');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const write=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n');
const rows=[
 ['aether:slider',53,57,'rock','steel',[180,120,150,40,120,30],'stamina',['1:rockslide','18:heavyslam','30:bodypress','45:bulldoze'],'hard_stone'],
 ['aether:valkyrie_queen',58,62,'fighting','flying',[160,135,105,65,105,110],'defiant',['1:sacredsword','18:aerialace','30:roost','45:wideguard'],'black_belt'],
 ['aether:sun_spirit',63,67,'fire','psychic',[170,65,90,130,105,80],'entitybattlecorecooling',['1:fireblast','18:heatwave','30:icywind','45:protect'],'wise_glasses','aether:fire_minion',5,-12,'oran_berry'],
 ['minecraft:wither',73,77,'ghost','poison',[185,85,105,135,125,65],'entitybattlewitherarmor',['1:shadowball','18:sludgebomb','30:darkpulse','45:protect'],'leftovers','minecraft:wither_skeleton',3,-10,'black_glasses'],
 ['minecraft:warden',68,72,'dark','ground',[230,135,105,90,115,45],'unaware',['1:boomburst','18:hammerarm','30:stompingtantrum','45:throatchop'],'assault_vest'],
 ['aether:fire_minion',null,null,'fire',null,[40,45,40,55,40,70],'flashfire',['1:ember','12:quickattack','24:flamecharge','36:protect']],
 ['minecraft:wither_skeleton',null,null,'ghost','dark',[60,85,65,35,55,60],'innerfocus',['1:shadowclaw','12:bite','24:poisonjab','36:protect']],
];
const files=['pack_mobs','vanilla_mobs'].map(name=>path.join(res,`data/entitybattle/battle_profiles/${name}.json`));
const profiles=files.map(read),twilightBefore=JSON.stringify(profiles.flat().filter(p=>p.entity.startsWith('twilightforest:')));
const stats=['hp','attack','defence','special_attack','special_defence','speed'];
for(const [id,min,max,type,second,values,ability,moves,item,minion,count,offset,minionItem] of rows){
 const profile=profiles.flat().find(p=>p.entity===id);assert(profile,`Profile ${id}`);
 const file=path.join(res,`data/entitybattle/species/${profile.species.split(':')[1]}.json`),species=read(file);
 species.primaryType=type;if(second)species.secondaryType=second;else delete species.secondaryType;
 species.baseStats=Object.fromEntries(stats.map((k,i)=>[k,values[i]]));species.abilities=[ability];species.moves=moves;write(file,species);
 if(min!==null){profile.boss=true;profile.catchable=false;profile.level={min,max};profile.worldMode='native_mob';profile.heldItem='cobblemon:'+item;
  if(minion)profile.bossBattle={mode:'horde',minionEntity:minion,minionCount:count,levelOffset:offset,minionHeldItem:'cobblemon:'+minionItem};else delete profile.bossBattle;
 }
}
assert.equal(JSON.stringify(profiles.flat().filter(p=>p.entity.startsWith('twilightforest:'))),twilightBefore);
files.forEach((f,i)=>write(f,profiles[i]));
const translations={
 'entitybattle.source.peaceful':['和平模式下无法转换此 Boss。','This Boss cannot be converted on Peaceful difficulty.'],
 'entitybattle.source.aether_challenge':['请先完成天境原生挑战条件并正式开战，再使用转换器。','Complete the native Aether challenge and begin combat before converting.'],
 'entitybattle.source.wither_charge':['凋灵仍在出生蓄力，暂时无法转换。','The Wither is still charging after spawning.'],
 'entitybattle.source.warden_transition':['监守者正在钻出或钻入地面，暂时无法转换。','The Warden is emerging or digging and cannot yet be converted.'],
 'entitybattle.source.unavailable':['来源生物的转换接口不可用，请查看服务端日志。','The source conversion interface is unavailable; check the server log.'],
 'cobblemon.ability.entitybattlecorecooling':['熔核冷却','Core Cooling'],
 'cobblemon.ability.entitybattlecorecooling.desc':['本场首次有效登场时开启普通晴天；首次受到敌方冰属性攻击招式的实际伤害并存活后永久冷却，清除普通晴天，特攻和速度各降低一级。','Once per battle, sets normal sunlight on its first effective entry. After surviving actual HP damage from an opposing Ice attack, cools permanently, clears normal sunlight, and lowers its Special Attack and Speed by one stage.'],
 'cobblemon.battle.activate.entitybattlecorecooling':['%1$s的熔核冷却了！普通晴天消散，特攻和速度下降！','%1$s\'s core cooled! Normal sunlight ended, and its Special Attack and Speed fell!'],
 'cobblemon.ability.entitybattlewitherarmor':['亡骸甲胄','Wither Armor'],
 'cobblemon.ability.entitybattlewitherarmor.desc':['本场首次生命值降至一半或以下并存活后，防御提升一级，此后有效特性可阻挡球弹类招式。跨越阈值的整次攻击先完成；治疗和换下不会解除甲胄，但换下会清除普通能力变化。','Once per battle, after surviving at half HP or less, raises Defense one stage and blocks bullet moves while this Ability is effective. The crossing attack finishes first. Healing and switching preserve armor; switching clears normal stat changes.'],
 'cobblemon.battle.activate.entitybattlewitherarmor':['%1$s展开了亡骸甲胄！防御提升，此后可抵挡球弹类招式！','%1$s raised its Wither Armor! Its Defense rose, and it now blocks bullet moves!'],
};
for(const [i,lang] of ['zh_cn','en_us'].entries()){const file=path.join(res,`assets/entitybattle/lang/${lang}.json`),data=read(file);for(const [k,v]of Object.entries(translations))data[k]=v[i];write(file,data);}
const mixFile=path.join(res,'entitybattle.mixins.json'),mix=read(mixFile);
if(!mix.mixins.includes('LivingDeathStateAccess'))mix.mixins.push('LivingDeathStateAccess');write(mixFile,mix);
require('./apply-boss-progression.cjs');
console.log('OTHER_BOSS_DESIGN PASS: five Bosses, two shared minion species; Twilight profiles preserved.');
