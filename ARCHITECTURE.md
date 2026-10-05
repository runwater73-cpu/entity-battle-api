# EntityBattle API 架构

目标版本：Minecraft 1.21.1、NeoForge 21.1.x、Cobblemon 1.8.1。

## 两种世界模式

| 模式 | 大世界实体 | 世界 AI 与攻击 | 对战、捕捉、队伍 | 战败后的死亡事件 |
| --- | --- | --- | --- | --- |
| `native_mob` | 原始 `Mob` | 来源生物的原生实现 | 开战后转换为真实 `PokemonEntity`，交给 Cobblemon | 还原原始 `Mob` 后触发原生伤害/死亡 |
| `pokemon_entity` | 真实 `PokemonEntity` | Cobblemon Brain，可增加行为适配器 | 从生成起全部交给 Cobblemon | `PokemonEntity` 死亡；可选来源战利品表和经验 |

每个实体类型只能注册一个 profile，并选择其中一种模式。未写 `worldMode` 时默认 `native_mob`。

### `native_mob` 生命周期

1. 原生 Mob 自然生成，保留原生 AI、攻击、交互、装备和普通死亡流程。其持久数据中保存一个 Cobblemon `Pokemon` 个体，生命值按比例同步。
2. `enableRChallenge` 开启时，玩家瞄准目标按 `R`；也可由接入代码使用 `EntityBattleSessions.start(...)`。服务端检查目标、队伍与距离。`EntityNativePokemonConversion` 快照原生 Mob，并通过 `Pokemon.sendOut` 生成真正的野生 `PokemonEntity`。预置配置默认关闭 R 挑战。
3. `BattleBuilder.pve` 让玩家整支队伍与这个 `PokemonEntity` 进入 Cobblemon 标准单打。成功后原始 Mob 从世界移除。对战、血条、换宠、招式、状态和捕捉走 Cobblemon 原生流程。
4. 捕捉成功后，Cobblemon 将 `Pokemon` 加入队伍或 PC；本模组清除恢复快照，不再生成原生 Mob。
5. 对战结束且未捕捉时，模组召回野生 `PokemonEntity`，从快照还原相同类型和 UUID 的 Mob，回写 Pokemon 状态与对应血量。若战败且 `defeat: vanilla_death`，对还原后的 Mob 造成玩家伤害，由原生死亡和掉落流程结算。`knockout` 使其以 1 点以上生命存活。
6. 玩家掉线、服务器正常关闭，以及残留转换实体重新加载时，模组尝试还原原生 Mob。崩溃发生在世界存盘的中间阶段、其他模组取消实体添加或强制卸载区块时，仍可能需要针对具体模组验证。

`native_mob` 的普通大世界阶段由原生 Mob 自己负责 AI 和死亡结算，因此 `worldBehavior`、`nativeDrops`、`nativeExperience` 不覆盖原生行为。若用转换器把该个体永久转换成 `PokemonEntity`，这三个字段会按直接模式生效。

`config/entitybattle-common.toml` 的 `enableRChallenge` 默认 `false`。客户端只在服务端同步允许后拦截 R 键；服务端收到挑战包时再次校验。转换器仍使用完整 profile 清单。永久转换没有原生 Mob 快照，因此特殊 Boss 的阶段 AI 与真实原生死亡事件不能由它恢复；需要实机验证任务回调。

### `pokemon_entity` 生命周期

原生 Mob 尝试加入世界时，`EntityPokemonWorldSpawns` 为其创建 Cobblemon `Pokemon` 和野生 `PokemonEntity`；成功后取消原始 Mob 的加入。之后只有一个世界实体。Cobblemon 直接处理野生对战、捕捉、再次放出和队伍生命周期。来源类型记录在 Pokemon 持久数据中，客户端在 PokemonEntity、战斗与队伍头像，以及 PC 预览和储存格中绘制来源 Mob 模型。当前预置清单不启用此模式；它作为接入作者的可选方案保留。

这条模式不运行原生 Mob 的 AI、普通攻击或其真实死亡事件。`worldBehavior` 提供被动/敌对基础行为；特殊行为可由接入模组注册 `EntityPokemonWorldBehaviorAdapter`。`nativeDrops` 和 `nativeExperience` 可读取来源实体的战利品表和经验，但不会把死亡事件中的实体类型改成来源 Mob。

## 职责边界

| 模块 | 职责 |
| --- | --- |
| `EntityBattleProfiles` | 解析数据包与代码注册的 profile |
| `EntityPokemonData` | 创建、加载、保存个体及捕捉策略 |
| `EntityPokemonOrigin` | 来源类型、UUID、可选外观快照 |
| `EntityNativePokemonConversion` | Mob 快照、生成 PokemonEntity、还原 Mob |
| `EntityBattleSessions` | 对战会话、捕捉结束与异常恢复 |
| `EntityBattleNetwork` | R 键挑战的服务端校验和客户端资料同步 |
| `EntityPokemonWorldSpawns` | `pokemon_entity` 模式的生成时替换 |
| `EntityPokemonWorldBehaviors` | 真实 PokemonEntity 的基础敌对行为 |
| `EntityPokemonNativeRewards` | `pokemon_entity` 模式的可选原生战利品表/经验 |
| `EntityPokemonNativeVisuals` | 使用来源 Mob 渲染器绘制 PokemonEntity 外观 |
| `EntityBattleItems` | 转换道具与独立创造物品栏标签页 |

## 当前验证边界

已通过 Gradle 构建和 167 个预置 profile 的静态一致性检查。客户端仍需实测：R 键开战、换宠、捕捉成功/失败、逃跑、濒死、创造标签页、道具右击、服务器重启后的恢复，以及来源模组的特殊 NBT 与死亡回调。对不同实体类型的行为不能从牛/僵尸的结果直接推断。

接入字段、示例和测试步骤见 [INTEGRATION.md](INTEGRATION.md)。
