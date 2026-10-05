# 预置生物配置与修改方法

适用版本：Minecraft 1.21.1、NeoForge、Cobblemon 1.8.1。本项目预置 87 种其他模组生物、79 种原版生物和车万女仆模组的野外妖精，共 167 种。每种生物使用不同的 `entitybattle:` 物种 ID，并有自己的类型、六项种族值、特性和升级招式。具有主人、背包和工作状态的女仆本体暂未接入。中文名称位于 `src/main/resources/assets/entitybattle/lang/zh_cn.json`，全部数值以 `src/main/resources/data/entitybattle/species/` 中的 JSON 为准。

## 两种世界模式

| 模式 | 世界实体 | 对战与捕捉 | 原生 AI、交互与死亡事件 |
| --- | --- | --- | --- |
| `native_mob` | 来源模组的 `Mob` | 使用转换器永久变为野生 `PokemonEntity`；也可在配置开启时对准按 R 临时转换对战 | 世界阶段保留；按 R 临时对战的 `vanilla_death` 会尝试走来源 Mob 死亡结算 |
| `pokemon_entity` | 从生成起就是 `PokemonEntity` | 直接使用 Cobblemon 的野生对战、捕捉和队伍行为 | 不会自动运行来源 Mob 的 AI 或专属交互；需要额外的行为适配器 |

当前 167 种预置生物全部使用 `native_mob`。`config/entitybattle-common.toml` 中的 `enableRChallenge` 默认是 `false`：大世界中可直接用 Minecraft 方式战斗，对准生物按 R 不会触发本模组的临时转换。想用宝可梦方式挑战时，用创造标签页“生物宝可梦化”中的转换器右击已接入生物，将其永久转换为野生 `PokemonEntity`。转换器在生存模式也可使用，但当前没有合成配方。单人游戏可在“模组列表 → Entity Battle API → 配置 → Common”中切换 R 对战开关；多人服务器须由服主修改服务端配置。修改后重启游戏或服务器。本模组不会在 Mob 攻击玩家时自动开战。`examples/direct_pokemon_entity/cow.json` 保留了牛的直接模式示例，默认不加载。

转换器会移除原生 Mob，此后大世界的 AI 与战败结算遵循 `PokemonEntity` 路径。特殊 Boss 的多阶段 AI、真正的原生死亡事件和任务回调不会因此自动保留；`nativeDrops` 和 `nativeExperience` 仅尝试提供原生战利品表与 Minecraft 经验。依赖原生死亡事件推进任务的 Boss 请先实机验证转换后的行为。需要原生死亡结算时，可开启按 R 临时对战，或直接进行 Minecraft 战斗。

## 文件位置

| 内容 | 源文件或目录 |
| --- | --- |
| 87 种整合包生物的绑定与行为 | `src/main/resources/data/entitybattle/battle_profiles/pack_mobs.json` |
| 77 种新增原版生物的绑定与行为 | `src/main/resources/data/entitybattle/battle_profiles/vanilla_mobs.json` |
| 车万女仆野外妖精 | `src/main/resources/data/entitybattle/battle_profiles/touhou_fairy.json` |
| 牛、僵尸示例 | `src/main/resources/data/entitybattle/battle_profiles/cow.json` 与 `zombie.json` |
| 167 个独立物种 | `src/main/resources/data/entitybattle/species/*.json` |
| 中文与英文物种名 | `src/main/resources/assets/entitybattle/lang/zh_cn.json` 与 `en_us.json` |

每个 profile 的 `species` 指向一个同名物种文件。例如 `twilightforest:naga` 指向 `entitybattle:twilightforest_naga`，对应 `species/twilightforest_naga.json`；`minecraft:creeper` 指向 `entitybattle:minecraft_creeper`，对应 `species/minecraft_creeper.json`。这些文件是可直接修改的预置配置，接入作者不必先借用别的宝可梦物种。不要运行项目里的初始生成脚本覆盖手工设计数据。

女仆模组当前只接入自然生成的 `touhou_little_maid:fairy`。它在大世界仍会飞行和发射原生弹幕；使用转换器，或开启配置后按 R，才进入宝可梦对战。`touhou_fairy.json` 指向独立物种 `touhou_little_maid_fairy.json`，属性为妖精/飞行。兼容层记录 18 种颜色和幼体状态，供战斗与捕捉后的原生模型渲染使用。女仆本体、弹幕、扫帚等辅助实体不接入。妖精的点数掉落与死亡后续行为仍需实机验证。

## 修改一个生物

1. 在 profile 中找到实体 ID。`level.min/max` 控制初次绑定时的等级；`catchable` 控制是否允许捕捉；`worldMode` 选择两种接入模式；`defeat` 控制原生模式战败结算；`worldBehavior`、`nativeDrops`、`nativeExperience` 主要影响永久转换后的 `PokemonEntity`。
2. 根据该 profile 的 `species` ID 打开 `data/entitybattle/species/<ID 路径>.json`。`primaryType`/`secondaryType` 是属性，`baseStats` 六项依次为 HP、攻击、防御、特攻、特防、速度；`abilities` 为 Cobblemon 特性 ID，`moves` 里的 `18:shadowball` 表示 18 级学习暗影球。
3. 同一文件还可修改 `catchRate`、`baseExperienceYield`、`experienceGroup`、`evYield`、`maleRatio`、`height`、`weight` 和 `hitbox`。`catchable: false` 禁止投球，此时提高 `catchRate` 也不会开放捕捉。种族值不是个体实际战斗面板数值，等级、个体值与性格仍会影响结果。
4. 修改中文名时同步调整 `assets/entitybattle/lang/zh_cn.json` 中的 `entitybattle.species.<species.name 小写>.name`。复制文件用于新生物时，给 `name`、图鉴号、species ID 和翻译键分配唯一值，并在 profile 里指向它。
5. 重新构建并安装 JAR，或使用数据包覆盖相同 `data/entitybattle/...` 路径。资源包用于覆盖语言文件。修改后检查启动日志是否成功加载物种与 profile；已有个体可能仍保留旧状态，应在新世界或新生成的生物上确认。

新作者可以从相近生物的文件开始调整，但发布时应分配自己的命名空间与 species ID，避免与本包预置物种冲突。详细的两种模式接入代码见 `INTEGRATION.md`。

## 捕捉与验证边界

整合包清单的 12 个高数值 Boss 与任务羊不可捕捉。原版远古守卫者、铁傀儡、劫掠兽、监守者、凋灵、村民及流浪商人也不可捕捉。原版末影龙有多部件实体和特殊死亡流程，巨人、幻术师属于未自然生成的特殊实体，这三者未加入预置清单。投射物、展示实体与玩家不是可接入 `Mob`。

所有独立物种均已写入数据；种族值、特性和招式是针对各生物机制的初始平衡方案。实机仍须验证原生 Mob 的特殊外观、Boss 多阶段行为、任务死亡回调、掉落、捕捉与队伍重新放出，尤其是来源模组升级或整合包变更以后。
