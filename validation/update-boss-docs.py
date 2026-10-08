"""Rebuild current Boss data pages from the release JSON, leaving source evidence in the main docs."""
import argparse
import json
import sys
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import profile_document
from profile_document import load_locales, load_species

DATA = ROOT / "src/main/resources/data/entitybattle"
PAGE = {
    "twilightforest:naga": "娜迦", "twilightforest:lich": "巫妖",
    "twilightforest:minoshroom": "米诺陶", "twilightforest:hydra": "九头蛇",
    "twilightforest:knight_phantom": "幻影骑士", "twilightforest:ur_ghast": "暮初恶魂",
    "twilightforest:alpha_yeti": "雪怪首领", "twilightforest:snow_queen": "冰雪女王",
    "aether:slider": "滑行魔石", "aether:valkyrie_queen": "武神女王",
    "aether:sun_spirit": "烈阳巨灵", "minecraft:warden": "监守者",
    "minecraft:wither": "凋灵", "kaleidoscope_twilight:umbral_sunflower": "向日葵",
    "deep_aether:eots_controller": "狂瞳龙卷",
}
NOTES = {
    "twilightforest:naga": "原作是绕圈、冲撞与受伤减少身体节段。树篱蜘蛛是宝可梦群战新增援军，不能当作原作庭院召唤技能。完整身体使用来源多部件显示；GUI 尺寸与大世界尺寸分别处理。",
    "twilightforest:lich": "巫妖种族值、四招、暮光支配与魂契分担沿用已确认方案。所有本体伤害均分给本体与存活仆从；只对巫妖自己的份额减伤。首次半血后永久汲取，只按攻击造成的对手实际 HP 损失回复 10%，不计过量或间接伤害。没有阶段换招。",
    "twilightforest:minoshroom": "原作持斧近战、冲锋与蓄力砸地。宝可梦阶段显示来源蓄力与落地表现，不再执行原生世界砸地伤害。",
    "twilightforest:hydra": "保留来源模型的头颈显示；头部不拆成独立宝可梦或独立 HP。喷火甲虫是本场新增援军。原生阶段、碰撞和吐息世界伤害不迁入回合结算。",
    "twilightforest:knight_phantom": "使用同房间原有六骑士小队，保留来源编号、武器和原生整队结算。不是新生成五个克隆随从；残缺小队不自动补齐，单个骑士倒下不提前结算整队胜利。",
    "twilightforest:ur_ghast": "声音与火系攻击配合专属哀鸣暴怒，累计敌方攻击的实际 HP 损失达到阈值后强化声音招式。本体先倒下再逃跑仍属于未完整胜利，不发来源战利品和 Boss 奖励。",
    "twilightforest:alpha_yeti": "取材于投掷、砸地与冰雪落物。世界真实方块破坏不在宝可梦招式中执行；随从只提供宝可梦战斗行动。",
    "twilightforest:snow_queen": "保留来源冰盾位置与束射姿态。极光幕、降雪和范围冰招由原生引擎处理；显示冰盾不具有第二份碰撞伤害。",
    "aether:slider": "先用原作认可的合法工具在合法位置唤醒。默认模式待原作正式开战后自动转换；错误工具、未激活与和平模式不能绕过门槛。",
    "aether:valkyrie_queen": "先完成原作对话，交付十枚胜利勋章，再作合法攻击确认。收取勋章仍由原模组负责；原作正式开战后自动转换。Ready 状态本身不豁免第一击。",
    "aether:sun_spirit": "先完成原作对话与冷却，最后挑战台词宣布开战后自动转换。宝可梦特性熔核冷却只管理本场天气和能力；完整胜利仍执行原作结束永昼、地牢解锁与死亡后续。",
    "minecraft:warden": "等待原作出现 / 潜地动作结束再转换。声音与近战显示使用原生动画时钟，实际伤害走宝可梦招式；没有复制独立世界攻击 AI。",
    "minecraft:wither": "等待原作出生蓄力结束再转换。亡骸甲胄由本场特性管理，完整胜利通过原作死亡路径产出下界之星；战斗剩饭与援军装备不追加掉落。",
    "kaleidoscope_twilight:umbral_sunflower": "正式 Boss，无额外对话 / 交付门槛，默认生成后自动转换。四招绑定原生剑技、剑气、地刺、护盾显示。没有复制原生二阶段复活 AI；胜利调用原作宝箱流程，保留奖杯和热泪之剑。",
    "deep_aether:eots_controller": "先用原作玩家攻击唤醒，再自动转换控制实体。身体分段只参与显示，不充当群战单位；清理只作用同一控制者。完整胜利复用原作黄铜钥匙、房间解锁和进度。详细实现与来源证据见 ../../DEEP_AETHER_BOSS.md。",
}

def main(check=False):
    locale, species = load_locales(), load_species()
    profiles = {}
    for path in sorted((DATA / "battle_profiles").glob("*.json")):
        value = json.loads(path.read_text(encoding="utf-8"))
        for p in value if isinstance(value, list) else [value]:
            profiles[p["entity"]] = p
    def name(source):
        spec=species[profiles[source]["species"]]
        key="entitybattle.species."+"".join(c for c in spec["name"].lower() if c.isalnum())+".name"
        return locale.get(key, source)
    def item(value):
        ns, key=value.split(":",1)
        return locale.get("item."+ns+"."+key) or locale["block."+ns+"."+key]
    def chinese(value):
        for old,new in (("HP","生命值"),("GUI","界面"),("Boss","首领"),("Minecraft","原版世界"),("MC","原版世界"),("AI","人工智能"),("Ready","准备就绪")):
            value=value.replace(old,new)
        return value.replace("详细实现与来源证据见 ../../DEEP_AETHER_BOSS.md。", "详细实现与来源证据见开发接入文档。")
    failures=[]
    for source,file in PAGE.items():
        p=profiles[source];s=species[p["species"]]
        types=" / ".join(locale.get("cobblemon.type."+t,t) for t in (s.get("primaryType"),s.get("secondaryType")) if t)
        stats=" / ".join(str(s["baseStats"][k]) for k in ("hp","attack","defence","special_attack","special_defence","speed"))
        abilities="、".join(locale["cobblemon.ability."+a.removeprefix("h:")] for a in s["abilities"])
        h=p.get("bossBattle"); squad=source=="twilightforest:knight_phantom"
        roster="原有同房间六骑士小队" if squad else "本体 + "+str(h["minionCount"])+" "+name(h["minionEntity"]) if h else "单体首领"
        lines=[f"# {name(source)} 当前战斗设定", "", "更新日期：2026-10-08。本页从当前源码配置生成，替换早期审阅基线。", "", "## 生效数据", "", "| 项目 | 当前配置 |", "| --- | --- |",
               f"| 等级 | {p['level']['min']}–{p['level']['max']} |", f"| 属性 | {types} |",f"| 种族值 生命 / 攻击 / 防御 / 特攻 / 特防 / 速度 | {stats}；合计 {sum(s['baseStats'].values())} |",
               f"| 特性 | {abilities} |",f"| 持有物 | {item(p['heldItem'])} |",f"| 战斗阵容 | {roster} |", "| 捕捉 | 正式首领不可捕捉；完整胜利发一次 1 级同物种奖励 |", "", "## 特性规则", ""]
        for ability in s["abilities"]:
            key="cobblemon.ability."+ability.removeprefix("h:")
            lines.append("**"+locale[key]+"**："+chinese(locale[key+".desc"]))
            lines.append("")
        lines += ["## 固定四招", "", "、".join(locale['cobblemon.move.'+move] for move in p.get('moves',[]))+"。"]
        moves=p.get("moves",[])
        lines += ["", "野外首领使用接入配置指定的四招；玩家的 1 级奖励按物种学习表学招，不提前继承高等级四招。实际选招复用原生人工智能，不规定固定招序。", "", "## 随从与道具", ""]
        if h:
            m=profiles[h["minionEntity"]];ms=species[m["species"]];off=h["levelOffset"]
            held=h.get("minionHeldItem",m.get("heldItem"))
            mm=h.get("minionMoves",m.get("moves",moves if squad else []))
            lines += [f"{h['minionCount']} 名 {name(h['minionEntity'])}，等级偏移 {off:+d}，实际 {max(1,p['level']['min']+off)}–{min(100,p['level']['max']+off)}。",
                      f"每名携带 {item(held)}。{'原有骑士按整队结算' if squad else '临时随从不可捕捉、不发首领奖励、不追加来源掉落或原版世界经验'}。",
                      "随从四招："+"、".join(locale["cobblemon.move."+move] for move in mm)+"。",
                      "随从物种的基础数值与学习表也适用于普通同种个体；本场装备与人数由首领接入配置指定。"]
        else:lines.append("无额外生成随从。" if not squad else "六名原有骑士各携带自己的设定装备，整队完整胜利结算一次。")
        lines += ["", "## 剧情 世界与结算", "", chinese(NOTES[source]), "",
                  "首领世界模式默认使用宝可梦实体，满足来源资格后自动转换。无前置条件时生成后即转换。用户也可设为原生生物模式；两种模式都不能跳过来源资格。",
                  "完整胜利保留来源战利品与原生死亡后续。临时快捷转换中断恢复来源；自动或永久转换中断保持宝可梦。不完整胜利不发通关奖励。首领与随从的战斗持有物不自动追加掉落，来源战利品表中本来存在的同名物品仍属于原作奖励。",
                  "", "## 如何修改", "", "物种设定文件负责数值、类型、特性与学习表；接入配置负责等级、固定四招、阵容与道具。具体文件位置和字段见 [开发接入文档](../../INTEGRATION.md)。",
                  "修改设定文档不会自动部署，应修改对应源码配置后构建并安装模组。",
                  "- [流程等级表](../../BOSS_PROGRESSION.md)、[战斗总表](../../BOSS_BATTLES.md)、[机制与边界](功能预设表.md)、[验证记录](../../BOSS_VERIFICATION.md)。", ""]
        target=ROOT/"docs/boss-design"/(file+".md")
        text="\n".join(lines)
        if re.search(r"[A-Za-z]", re.sub(r"\]\([^)]*\)", "]",text)):
            raise ValueError("English leaked into Chinese setting page: "+file)
        if check:
            if not target.exists() or target.read_text(encoding="utf-8")!=text:failures.append(str(target))
        else:target.write_text(text,encoding="utf-8")
    if failures:raise SystemExit("Outdated Boss pages: "+", ".join(failures))
    print(f"BOSS_DOCS {'CHECK' if check else 'WRITE'} PASS: {len(PAGE)} pages from current source JSON")

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--check",action="store_true")
    parser.add_argument("--mods",type=Path,default=profile_document.MODS)
    args=parser.parse_args();profile_document.MODS=args.mods;main(args.check)
