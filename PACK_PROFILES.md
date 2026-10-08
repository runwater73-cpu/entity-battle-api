# 预置生物配置与修改方法

适用版本：Minecraft 1.21.1、NeoForge、Cobblemon 1.8.1。本项目预置 89 种其他模组生物、79 种原版生物和车万女仆模组的野外妖精，共 169 种。每种生物使用不同的 `entitybattle:` 物种 ID，并有自己的类型、六项种族值、特性和升级招式。具有主人、背包和工作状态的女仆本体暂未接入。中文名称位于 `src/main/resources/assets/entitybattle/lang/zh_cn.json`，全部数值以 `src/main/resources/data/entitybattle/species/` 中的 JSON 为准。

## 两种世界模式

最新 Boss 等级以 [流程等级表](BOSS_PROGRESSION.md) 为准。共 15 个野外 Boss，均服从全局 `bossWorldMode`，默认在原作开战条件解锁后自动成为宝可梦；剧情未完成时保留来源交互。向日葵的独立配置在 `battle_profiles/sunflower_boss.json`，详细说明见 [向日葵 Boss](SUNFLOWER_BOSS.md)。深入天境狂瞳龙卷在 `battle_profiles/deep_aether_boss.json`，见 [狂瞳龙卷](DEEP_AETHER_BOSS.md)。用户已取消继续开发暮色领主。

| 模式 | 世界实体 | 对战与捕捉 | 原生 AI、交互与死亡事件 |
| --- | --- | --- | --- |
| `native_mob` | 来源模组的 `Mob` | 使用转换器永久变为野生 `PokemonEntity`；也可在配置开启时对准按 R 临时转换对战 | 世界阶段保留；按 R 临时对战的 `vanilla_death` 会尝试走来源 Mob 死亡结算 |
| `pokemon_entity` | 从生成起就是 `PokemonEntity` | 直接使用 Cobblemon 的野生对战、捕捉和队伍行为 | 不会自动运行来源 Mob 的 AI 或专属交互；需要额外的行为适配器 |

普通生物预置 `worldMode` 为 `native_mob`；15 个标记为 `boss` 的实体受 `bossWorldMode` 覆盖，仍保留来源资格门槛。`config/entitybattle-common.toml` 中的 `enableRChallenge` 默认是 `false`：大世界中的普通原生 Mob 可直接用 Minecraft 方式战斗，对准生物按 R 不会触发本模组的临时转换。想用宝可梦方式挑战普通生物时，用创造标签页“生物宝可梦化”中的转换器右击已接入生物，将其永久转换为野生 `PokemonEntity`。转换器可在生存模式用两份紫水晶碎片、两份红石和一份铁锭合成，整合包可覆盖默认配方。单人游戏可在“模组列表 → Entity Battle API → 配置 → Common”中切换 R 对战开关；多人服务器须由服主修改服务端配置。修改后重启游戏或服务器。本模组不会在 Mob 攻击玩家时自动开战。`examples/direct_pokemon_entity/cow.json` 保留了牛的直接模式示例，默认不加载。

正式 Boss 包括娜迦、巫妖、米诺菇、九头蛇、幻影骑士、暮初恶魂、雪怪首领、冰雪女王、滑行魔石、武神女王、烈阳巨灵、狂瞳龙卷、凋灵、监守者和向日葵，均不可捕捉。默认 `bossWorldMode = "POKEMON_ENTITY"` 时自动转换：天境与深入天境须先完成原作对话 / 交付 / 唤醒，其余满足各来源资格后转换。改为 `NATIVE_MOB` 可保留原生 Boss。Boss 在 Cobblemon `BATTLE_VICTORY` 胜利事件中被玩家击败后，只奖励一次对应物种的**固定 1 级**个体。安装 Team Rocket 时，奖励装在 `teamrocket:recruit_ball` 中；队伍已满时球会进入背包或掉落在玩家脚下。没有 Team Rocket 时则直接加入队伍，队伍已满再加入 PC。

巫妖的 `bossBattle` 是首个群战预置：只要同时安装 Asymmetric Battles 与 Horde Encounters，profile 中的群战定义就会自动生效，不需要额外开关。战斗开始时巫妖和两名巫妖仆从成为野生 Horde 敌方，玩家仍一次只上一只。巫妖等级范围为 28–32，群战仆从跟随巫妖等级；普通野生仆从也在 28–32 级范围。仆从使用自己的 species，群战临时仆从不可捕捉，也不发放额外来源掉落或 Minecraft 经验。巫妖四招为守住、超音波、暗影球和烈焰溅射；仆从为咬住、浊雾、挑衅、黑夜魔影，并全部携带剩饭。阶段使用正式特性暮光支配与魂契分担，不更换招式。修改物种学习表请编辑 species 文件，修改 Boss 固定四招、援军数、等级与持有物请编辑 `battle_profiles/pack_mobs.json`。详细机制边界见 [BOSS_BATTLES.md](BOSS_BATTLES.md)。

转换器会移除原生 Mob。未登记来源适配器的生物此后大世界 AI 与战败结算遵循 `PokemonEntity` 路径；`nativeDrops` 和 `nativeExperience` 尝试提供原生战利品表与 Minecraft 经验。天境、凋灵、监守者、向日葵及骑士小队已登记来源死亡 / 中断恢复适配，完整胜利可执行真正来源死亡回调。原作多阶段 AI 不会自动迁移到宝可梦引擎，第三方任务插件仍需实机验证。

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

生存获取补充见 [转化粉进化](CREATURE_EVOLUTIONS.md)：普通僵尸已开放捕捉，新增十六条原生进化，转换器有默认合成配方。其他普通禁捕来源保留野生禁捕，可通过表中的培养路线取得；首领和谜题羊仍要求原有胜利或任务。

1. 在 profile 中找到实体 ID。`level.min/max` 控制初次绑定时的等级；`catchable` 控制是否允许捕捉；`worldMode` 选择两种接入模式；`defeat` 控制原生模式战败结算；`worldBehavior`、`nativeDrops`、`nativeExperience` 主要影响永久转换后的 `PokemonEntity`。
2. 根据该 profile 的 `species` ID 打开 `data/entitybattle/species/<ID 路径>.json`。`primaryType`/`secondaryType` 是属性，`baseStats` 六项依次为 HP、攻击、防御、特攻、特防、速度；`abilities` 为 Cobblemon 特性 ID，`moves` 里的 `18:shadowball` 表示 18 级学习暗影球。
3. 同一文件还可修改 `catchRate`、`baseExperienceYield`、`experienceGroup`、`evYield`、`maleRatio`、`height`、`weight` 和 `hitbox`。`catchable: false` 禁止投球，此时提高 `catchRate` 也不会开放捕捉。种族值不是个体实际战斗面板数值，等级、个体值与性格仍会影响结果。
4. 修改中文名时同步调整 `assets/entitybattle/lang/zh_cn.json` 中的 `entitybattle.species.<species.name 小写>.name`。复制文件用于新生物时，给 `name`、图鉴号、species ID 和翻译键分配唯一值，并在 profile 里指向它。
5. 重新构建并安装 JAR，或使用数据包覆盖相同 `data/entitybattle/...` 路径。资源包用于覆盖语言文件。修改后检查启动日志是否成功加载物种与 profile；已有个体可能仍保留旧状态，应在新世界或新生成的生物上确认。

新作者可以从相近生物的文件开始调整，但发布时应分配自己的命名空间与 species ID，避免与本包预置物种冲突。详细的两种模式接入代码见 `INTEGRATION.md`。

## 捕捉与验证边界

正式 Boss 与铁傀儡、劫掠兽、村民、流浪商人不可捕捉。谜题羊是普通可捕捉物种，35–50 级，一般 / 妖精、毛茸茸，捕获率 45。大世界保留原生 Mob 与 AI，完成原作十六色羊毛任务并实际发放奖励后才允许转换；完成任务不会自动转换，玩家自行选择转换器或已开启的 R，再走 Cobblemon 捕捉。捕捉后由 Cobblemon 管理，不携带新的任务奖励。远古守卫者保留为普通 `native_mob` 配置，不会触发 Boss 奖励。原版末影龙有多部件实体和特殊死亡流程，巨人、幻术师属于未自然生成的特殊实体，这三者未加入预置清单。投射物、展示实体与玩家不是可接入 `Mob`。

所有独立物种均已写入数据；种族值、特性和招式是针对各生物机制的初始平衡方案。实机仍须验证原生 Mob 的特殊外观、Boss 多阶段行为、任务死亡回调、掉落、捕捉与队伍重新放出，尤其是来源模组升级或整合包变更以后。
