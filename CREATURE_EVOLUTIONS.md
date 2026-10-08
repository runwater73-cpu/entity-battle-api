# 生存获取与转化粉进化

作者：RunWater1。版本：0.2.0-beta.2。适用于我的世界 1.21.1 与方可梦 1.8.1。

本批为现有接入物种补充 16 条路线，使用暮色森林原有转化粉和方可梦原生道具进化。本表是本模组新增玩法，来源模组原本没有这些进化关系。

## 玩家操作

1. 合成转换器：第一行中间放紫水晶碎片，第二行依次放红石、铁锭、红石，第三行中间放紫水晶碎片。
2. 用转换器右击表中的来源生物，再用精灵球捕捉。普通僵尸本次开放捕捉，捕获率 180。
3. 培养到表中最低等级，放出自己的宝可梦，手持一份转化粉普通右击。
4. 在方可梦队伍详情中确认待进化选项。放出状态播放原生进化过程，收回状态确认则直接完成。

粉在登记待进化时消耗，暂缓或取消确认不会返还。重复右击同一条待进化不会重复消耗。进化单向，不能把野外原生生物直接撒粉变成拥有的宝可梦。

## 全部路线

| 来源宝可梦 | 转化结果 | 最低等级 | 补充原因 |
|---|---|---:|---|
| 僵尸 | 巫妖仆从 | 28 | 首领召唤的随从；临时群战个体不可捕捉 |
| 马 | 僵尸马 | 15 | 在我的世界 1.21.1 中没有自然生存生成入口 |
| 守卫者 | 远古守卫者 | 55 | 原生可生成，但当前普通生物配置禁止直接捕捉 |
| 雪傀儡 | 铁傀儡 | 35 | 原生可制造或生成，但当前普通生物配置禁止直接捕捉 |
| 掠夺者 | 劫掠兽 | 50 | 原生可在袭击生成，但当前普通生物配置禁止直接捕捉 |
| 僵尸村民 | 村民 | 20 | 原生可生成或治疗，但当前普通生物配置禁止直接捕捉 |
| 村民 | 流浪商人 | 25 | 原生可生成，但当前普通生物配置禁止直接捕捉 |
| 巨人矿工 | 武装巨人 | 55 | 原生可生成，但当前普通生物配置禁止直接捕捉 |
| 骷髅德鲁伊 | 信徒 | 35 | 当前来源版本未发现生存生成入口 |
| 岩浆怪 | 噩兆立方 | 30 | 当前来源版本未发现生存生成入口 |
| 迷宫史莱姆 | 漫游立方 | 35 | 当前来源版本未发现生存生成入口 |
| 稳定冰核 | 冰晶 | 35 | 冰雪女王召唤的随从；临时群战个体不可捕捉 |
| 烈焰人 | 烈焰奴仆 | 30 | 原作陷阱也可召唤；补充稳定获取路线 |
| 西风啸云 | 迷你云护卫 | 20 | 原作云杖也可召唤；补充脱离召唤物生命周期的获取路线 |
| 西风嗤云 | 和风 | 15 | 原作浮空围巾也可召唤；补充脱离召唤物生命周期的获取路线 |
| 唤魔者 | 手下 | 35 | 当前来源版本仅发现刷怪蛋入口，未发现生存生成或合成入口 |

每条使用一份暮色转化粉，并要求来源与结果的内容模组均存在。烈焰奴仆、迷你云护卫、和风原作已有陷阱或召唤道具入口，新增的是稳定培养途径。深入天境的西风嗤云可由地牢陷阱生成，保留原入口。

## 个体数据

原生进化保留个体编号、等级、主人、个体值、努力值、性格、昵称、精灵球、携带物和已学招式。更换目标物种后使用目标属性、种族值、成长组和特性，按原生规则结算生命值。新物种学习表可在招式管理中查看，不强制重置四招。巫妖仆从使用魂契分担。

进化不会附赠剩饭，不继承群战临时随从身份、首领队伍或死亡奖励。跨来源变化会清理旧原生生物编号和外观快照，绑定结果模型；同来源形态变化保留外观。世界、队伍、电脑等公共显示入口使用更新的模型身份。

拥有后的个体遵循方可梦行为；村民交易、召唤物主人与寿命、任务、原生人工智能不会随进化迁入。

## 保留的原有途径

十五种正式首领继续要求原作剧情及整场胜利，发放一次一级奖励个体；不增加转化粉绕过首领。谜题羊仍须十六色任务实际领奖后才能转换捕捉。骷髅马保留雷暴陷阱、雪傀儡保留制造、忠诚僵尸保留权杖召唤，其余已正常生成并可捕捉的物种保持原路线。女仆本体、末影龙、原版巨人和幻术师当前未接入。

## 修改进化

进化数据位于 `src/main/resources/data/entitybattle/species_additions/acquisition/`，仅向既有物种添加原生进化。下列技术标识用于定位；中文设定见上表。

| 来源名称 | 源文件名 | 结果物种标识 |
|---|---|---|
| 僵尸 | `zombie.json` | `entitybattle:twilightforest_lich_minion` |
| 马 | `minecraft_horse.json` | `entitybattle:minecraft_zombie_horse` |
| 守卫者 | `minecraft_guardian.json` | `entitybattle:minecraft_elder_guardian` |
| 雪傀儡 | `minecraft_snow_golem.json` | `entitybattle:minecraft_iron_golem` |
| 掠夺者 | `minecraft_pillager.json` | `entitybattle:minecraft_ravager` |
| 僵尸村民 | `minecraft_zombie_villager.json` | `entitybattle:minecraft_villager` |
| 村民 | `minecraft_villager.json` | `entitybattle:minecraft_wandering_trader` |
| 巨人矿工 | `twilightforest_giant_miner.json` | `entitybattle:twilightforest_armored_giant` |
| 骷髅德鲁伊 | `twilightforest_skeleton_druid.json` | `entitybattle:twilightforest_adherent` |
| 岩浆怪 | `minecraft_magma_cube.json` | `entitybattle:twilightforest_harbinger_cube` |
| 迷宫史莱姆 | `twilightforest_maze_slime.json` | `entitybattle:twilightforest_roving_cube` |
| 稳定冰核 | `twilightforest_stable_ice_core.json` | `entitybattle:twilightforest_ice_crystal` |
| 烈焰人 | `minecraft_blaze.json` | `entitybattle:aether_fire_minion` |
| 西风啸云 | `aether_zephyr.json` | `entitybattle:aether_cloud_minion` |
| 西风嗤云 | `deep_aether_baby_zephyr.json` | `entitybattle:deep_aether_gentle_wind` |
| 唤魔者 | `minecraft_evoker.json` | `entitybattle:legendarymonuments_grunt` |

修改 `requirements.minLevel` 调整等级，`result` 调整目标，`requiredContext` 调整道具标签。`consumeHeldItem=false` 保留携带物；`optional=true` 使用原生确认。`learnableMoves` 可指定进化时提供的招式。用数据包同路径覆盖并重载；已登记待进化的旧个体请清理后再测试。

结果物种的 `species` 文件同时设置 `preEvolution`，指向来源物种的完整标识。新增或调整路线时应同步修改，供原生进化成就和图鉴关系读取。

道具标签为 `data/entitybattle/tags/item/transformation_powders.json`。暮色粉使用 `required=false`；缺少暮色森林时标签为空，不能把其他道具误当转化粉。作者可向标签追加替代道具。缺失或未启用的结果物种在使用及确认时都会被拒绝。

## 转换器配方与整合包覆盖

默认配方 ID 为 `entitybattle:converter`，文件为 `data/entitybattle/recipe/converter.json`。整合包数据包覆盖相同 ID 时优先采用整合包版本；不同 ID 的旧配方会与默认配方并存。现有 KubeJS、CraftTweaker 自定义配方不会因新增本配方而被删掉；如需只保留旧配方，按 ID 删除默认配方，或覆盖同一 ID。无须修改模组 JAR。

## 核查依据

范围是当前预置的 169 种绑定，按生成、制造、召唤、捕捉策略、首领奖励与任务入口核对；不是新增整个实体注册表。来源版本为暮色森林 4.8.3345、天境 1.5.10、深入天境 1.1.5.1、传说遗迹 8.1。

- 巫妖和冰雪女王分别创建仆从和冰晶；本模组临时群战随从不可捕捉。
- 信徒、噩兆立方、漫游立方有实体注册和渲染；扫描此暮色版本的实体引用与生成资源未发现生存入口。
- 天境陷阱创建烈焰奴仆，云杖创建迷你云护卫；深入天境陷阱创建西风嗤云，浮空围巾创建和风。
- 传说遗迹的手下仅查到刷怪蛋创建；未发现自然或结构生成、刷怪蛋配方或掉落入口。
- 铁傀儡等普通生物原生可获得，是本模组的野生禁捕配置造成培养缺口；进化补齐培养路线，野生禁捕保留。

验证方式见 [验证说明](validation/README.md)。未逐一游玩所有自然世界；来源模组升级后应重新核查生成途径。游戏内进化画面、图鉴页与强度仍需验收。
