# Boss 战斗设定表

适用：Minecraft 1.21.1、Cobblemon 1.8.1、Entity Battle API。具体数值以 `data/entitybattle/species/*.json` 和 `battle_profiles/*.json` 为准。2026-10-06 已实施八个暮色 Boss，2026-10-07 已批准并写入另外五个 Boss；分别见 [暮色实施记录](TWILIGHT_BOSS_IMPLEMENTATION.md) 与 [原版 / 天境实施记录](OTHER_BOSS_IMPLEMENTATION.md)。

## 共通规则

- `bossWorldMode = POKEMON_ENTITY` 时，正式 Boss 满足来源开战条件后自动转换为野生 `PokemonEntity`；无前置条件则生成后即转换。天境与深入天境保留原作对话、勋章 / 工具和唤醒条件，解锁后无需转换器。切回 `NATIVE_MOB` 时保留原生 Boss 和转换器途径，也不能绕过剧情。只作用于标记 `boss: true` 的 profile。
- 正式 Boss 本体不可捕捉。玩家沿用自己的最多六只宝可梦，每回合只派出一只，可以按 Cobblemon 正常换宠。群战时是敌方多个单位，不是玩家六只同时上场。
- 战胜正式 Boss 后的固定 1 级奖励、来源战利品表和 Minecraft 经验分别由现有模块结算。群战仆从不得额外发放 Boss 奖励，也不应重复发放来源掉落。
- profile 定义了 `bossBattle` 的 Boss 默认使用群战；需要同时安装 Asymmetric Battles 和 Horde Encounters。Horde Encounters 接管 Cobblemon 的野生 `pve`，再调用 Asymmetric Battles 的 Horde 战斗构建器；缺少任一模组或没有群战定义时回到 Cobblemon 标准单打。`heldItem` 在 Boss profile 顶层配置，和群战无关。ForgeConfigAPIPort 是 Asymmetric Battles 自身的前置，本模组不直接调用它。
- 原生阶段 AI、投射物、护盾、阶段切换、真实来源实体死亡事件和任务回调不会因转换而自动迁入宝可梦战斗。特别是暮色森林进度，必须实机验证。

## 当前 15 个正式 Boss

六项种族值顺序：HP / 攻击 / 防御 / 特攻 / 特防 / 速度。此表记录当前生效数据，修改方法见 [PACK_PROFILES.md](PACK_PROFILES.md)。

| Boss（来源实体） | 等级 | 属性 | 六项种族值 | 特性 | 本轮战斗形态 |
| --- | --- | --- | --- | --- | --- |
| 雪怪首领 `twilightforest:alpha_yeti` | 33–37 | 冰 / 格斗 | 155 / 135 / 110 / 50 / 85 / 50 | 威吓 | 本体 + 2 雪怪 |
| 九头蛇 `twilightforest:hydra` | 38–42 | 龙 / 火 | 180 / 125 / 110 / 140 / 110 / 65 | 多重鳞片 | 本体 + 2 喷火甲虫；不拆独立头部 |
| 幻影骑士 `twilightforest:knight_phantom` | 33–37 | 幽灵 / 钢 | 75 / 85 / 90 / 35 / 70 / 45 | 飘浮 | 原有同房间六骑士小队，每名种族值 400 |
| 巫妖 `twilightforest:lich` | 28–32 | 幽灵 / 超能 | 172 / 78 / 108 / 166 / 142 / 94 | 暮光支配 | **野生群战试点：巫妖 + 2 名巫妖仆从** |
| 米诺菇 `twilightforest:minoshroom` | 33–37 | 草 / 格斗 | 155 / 135 / 110 / 50 / 95 / 55 | 威吓 | 本体 + 2 米诺陶 |
| 娜迦 `twilightforest:naga` | 23–27 | 龙 / 地面 | 160 / 130 / 100 / 60 / 100 / 100 | 蜕皮 | 本体 + 2 树篱蜘蛛 |
| 冰雪女王 `twilightforest:snow_queen` | 38–42 | 冰 / 妖精 | 140 / 55 / 95 / 135 / 110 / 90 | 降雪 | 本体 + 2 冰晶 |
| 暮初恶魂 `twilightforest:ur_ghast` | 38–42 | 幽灵 / 火 | 200 / 60 / 105 / 145 / 120 / 70 | 哀鸣暴怒 | 本体 + 2 砷铅铁恶灵 |
| 滑行魔石 `aether:slider` | 53–57 | 岩石 / 钢 | 180 / 120 / 150 / 40 / 120 / 30 | 持久力 | 单 Boss |
| 武神女王 `aether:valkyrie_queen` | 58–62 | 格斗 / 飞行 | 160 / 135 / 105 / 65 / 105 / 110 | 不服输 | 单 Boss |
| 烈阳巨灵 `aether:sun_spirit` | 63–67 | 火 / 超能 | 170 / 65 / 90 / 130 / 105 / 80 | 熔核冷却 | 本体 + 5 烈焰奴仆 |
| 监守者 `minecraft:warden` | 68–72 | 恶 / 地面 | 230 / 135 / 105 / 90 / 115 / 45 | 纯朴 | 单 Boss |
| 凋灵 `minecraft:wither` | 73–77 | 幽灵 / 毒 | 185 / 85 / 105 / 135 / 125 / 65 | 亡骸甲胄 | 本体 + 3 凋灵骷髅 |
| 向日葵 `kaleidoscope_twilight:umbral_sunflower` | 43–47 | 草 / 钢 | 155 / 130 / 120 / 55 / 100 / 70 | 锋锐 | 单 Boss |
| 狂瞳龙卷 `deep_aether:eots_controller` | 63–67 | 飞行 / 冰 | 180 / 75 / 100 / 130 / 105 / 90 | 怒火冲天 | 单 Boss，原生身体显示不拆群战单位 |

## 巫妖群战试点

| 项目 | 设定 |
| --- | --- |
| 触发 | 玩家按 Cobblemon 的正常方式挑战野生巫妖 `PokemonEntity`；不新增专用按键或道具 |
| 敌方 | 巫妖 1 只，加 `twilightforest:lich_minion` 仆从 2 只，最多 3 个敌方目标 |
| 玩家 | 原队伍最多 6 只；同时上场 1 只，允许正常换宠 |
| 仆从等级 | 群战仆从跟随巫妖等级（偏移 0）；巫妖和普通野生仆从均在 28–32 级范围 |
| 仆从属性与招式 | `entitybattle:twilightforest_lich_minion`：幽灵 / 恶、魂契分担；咬住、浊雾、挑衅、黑夜魔影 |
| 仆从持有物 | 全部携带 `cobblemon:leftovers`（剩饭），由原生持有物规则在回合结束回复最大 HP 的 1/16 |
| 持有物 | `twilightforest:lich_trophy`；由 Journeysouvenirs 的 Cobblemon 持有物脚本结算，前三次受到的伤害各减少 25%；已装备其他持有物时不覆盖 |
| 捕捉 | 巫妖和本场召唤的仆从均不可捕捉；普通大世界巫妖仆从仍按自身 profile 决定 |
| 胜利 | 敌方全部失去战斗能力；只对巫妖发一次 1 级奖励 |
| 清理 | 战斗失败、逃跑、断线或服务器重启后，不留下本场临时召唤的仆从 |
| 回退 | 未安装群战模组、没有有效仆从 profile 或仆从创建失败时，巫妖走标准 Cobblemon 单打；临时仆从会被清理 |

### 招式与表现

暮色森林 4.8.3345 的巫妖使用可反弹的彩色魔法弹、带火焰粒子的爆炸弹、护盾、影子分身，以及后续的召唤和近战。试点使用 Cobblemon 已有招式结算战斗。实际执行招式时发送原版挥手动画，并在巫妖周围发送对应粒子；客户端原生模型复用挥手进度。目标侧招式动画仍由 Cobblemon 的资源决定，需要实机确认。下表不表示原生攻击机制已经迁入战斗。

| 已有招式 ID | 对应的巫妖表现 | 机制边界 |
| --- | --- | --- |
| `shadowball` | 挥手、灵魂粒子，表达原生魔法弹的暗色魔法主题 | 不实现原生弹丸反弹；由 Cobblemon 计算伤害与特防下降概率 |
| `flameburst` | 挥手、火焰粒子，表达原生爆炸弹的火焰主题 | 使用 Cobblemon 的火属性与溅射规则，不生成会破坏世界的原生爆炸 |
| `protect` | 挥手、附魔粒子，表达防护魔法 | 只按 Cobblemon 的单回合守住结算，不复制原生多层护盾 |
| `supersonic` | 挥手、音符粒子，表示声音干扰 | 原生超音波，55% 命中率，使目标混乱；没有治疗效果 |

巫妖使用 `entitybattletwilightdominion`（暮光支配），仆从使用 `entitybattlesoulcovenant`（魂契分担）。两者是 Cobblemon 正式注册的特性脚本；玩家奖励个体使用同一 species，因此同样拥有特性。招式不随阶段更换或过滤。

### 特性内的两个阶段

- **暮光壁垒：**本场尚未进入汲取，且 HP 高于 50% 时，巫妖自己最终承担的伤害降低 20%。减伤在魂契分摊之后计算，不降低仆从的份额。
- **暮光汲取：**HP 首次降至 50% 或以下后，本场永久进入汲取；失去减伤，攻击招式对对手造成实际 HP 损失时，回复其 10%。只计实际损失，不计替身、过量伤害、毒、天气、反伤或其他间接伤害。回复基数向下取整，不足 1 HP 时不回复；使用原生吸取回复流程，禁疗、污泥和大根茎等仍按原规则结算。
- 跨越阈值的那一击仍使用命中前阶段；随后命中、下一次选招及紧接着的回血都会看到新的阶段。换下再上场、回血到半血以上均不重置。状态属于本场 Showdown Pokemon，不写入存档；下一场重新判断，开场已在半血以下时直接进入汲取。
- 特性服从原生特性抑制和破格规则；特性失效期间不执行其效果，已记录的汲取状态不会因暂时失效被清除。
- 删除了 `EntityBossPhases`、阶段 AI Mixin 和 `bossBattle.phases`。巫妖正常使用守住、超音波、暗影球、烈焰溅射；PP、挑衅、锁招和选招由原战斗系统处理。
- 保留可复用的 `EntityBattleSupportMoves` / `NativeSupportTargetMixin`：仅在原生 AI 选中已登记的治疗招式后修正目标，不按阶段选招、不计算招式效果。默认登记治愈波动，当前巫妖未携带此招，不会触发。适用于已接入 API 的野生个体，优先合法且血量比例较低的其他己方单位；无队友时选择其他原生可用招式，只有治疗招式可用或被锁定时使用原生 pass。玩家控制的个体保留原生自由选目标。作者可调用 `EntityBattleSupportMoves.registerAllyHealingMove("技能ID")` 增加同类策略。

### 魂契分担

按用户最终规则，**巫妖受到的所有伤害均分摊**，包括直接攻击、中毒、灼伤、天气、反伤、混乱自伤和挣扎反伤。HP 支付若走原生伤害结算也会参与；直接设置 HP 或替身 HP 不属于本体伤害事件。原生 `directDamage` 会绕过伤害事件，因此特性在本场战斗实例上为受到本特性影响的巫妖补上 `Damage` 事件后，继续调用原生扣血流程；其他生物保留原生路径，不修改引擎文件或第三方 JAR。

同阵营、存活且在场、魂契分担有效的单位参与。每次伤害只分摊一次，不会因为多个仆从而重复分摊。分摊使用巫妖收到的伤害数值，不重新计算仆从的防御和属性克制；承担伤害以特性事件结算，不视作再次被攻击，不再次触发接触与攻击追加效果。

整数份额向下取整，余数由巫妖承担。仆从低 HP 时只扣其剩余 HP，溢出不二次转移；下一击重新统计存活单位。群体攻击可以同时对仆从造成原招式伤害和其承担的份额。巫妖汲取对另一组魂契单位攻击时，计入对手实际承担的 HP 损失，但转移事件本身不独立触发吸血。

例如原伤害 90、两个仆从，先分为 30 / 30 / 30；壁垒只把巫妖一份降低为 24。原生携带物的计算顺序保持不变：现有巫妖奖杯在 `ModifyDamage` 阶段处理招式伤害，早于本特性的 `Damage` 分摊；不修改第三方奖杯脚本。

开战仍是巫妖与两名援军同时上场。Asymmetric Battles 和 Horde Encounters 的分工保持不变；援军不是战斗中途召唤。预设队伍不因塔内墙体遮挡减少成员。

## 自定义

用数据包覆盖 `data/entitybattle/battle_profiles/pack_mobs.json` 中对应 profile。巫妖的 `bossBattle` 内容如下；其他字段保留原有 profile。

```json
"heldItem": "twilightforest:lich_trophy",
"bossBattle": {
  "mode": "horde",
  "minionEntity": "twilightforest:lich_minion",
  "minionCount": 2,
  "levelOffset": 0
}
```

- 援军数量为 1-5；种类必须有非 Boss profile。等级偏移为 -99 至 99，实际等级限制在 1-100。
- 特性脚本在 `data/entitybattle/abilities/entitybattletwilightdominion.js` 和 `entitybattlesoulcovenant.js`，可以用数据包的同路径文件覆盖。脚本必须使用块注释，Cobblemon 加载时会将换行合并。
- species 的 `abilities` 填正式特性 ID，招式在 `moves` 中修改。中文名称与描述在 `assets/entitybattle/lang/zh_cn.json`。默认特性阈值 50%、伤害乘数 0.8、吸取比例 1/10；改变脚本中的数值后同步修改描述，并重启或在停止战斗后重载资源。
- 修改 species 后使用新召唤的 Boss 测试。已有个体保存了特性和招式，不会因为 species 文件更新自动迁移。
- 删除 `bossBattle` 后使用普通野生单打。没有全局群战开关。
- 目前为八个暮色森林 Boss 配置了对应奖杯。奖杯战斗效果需要 Journeysouvenirs 提供的持有物映射和脚本；只有暮色森林时，持有奖杯不代表有宝可梦减伤效果。

## 实机验收

检查巫妖刷怪笼、模型与招式动作、三个敌方血条与目标选择、玩家换宠、逃跑、掉线恢复、战胜奖励、原生掉落以及暮色森林进度。服务端验证结果和边界记录于 `BOSS_VERIFICATION.md`；客户端画面和整合包任务进度仍需游戏验证。
