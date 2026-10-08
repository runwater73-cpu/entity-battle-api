# EntityBattle API 接入指南

适用：Minecraft 1.21.1、NeoForge 21.1.x、Cobblemon 1.8.1。接入目标是现有 `Mob` 类型，不必重新制作它的模型和基础动画。战斗资料使用 Cobblemon species，世界实体模式由 profile 选择。

## 选择模式

- 选 `native_mob`：生物在大世界继续运行原生 AI、攻击、交互、装备、掉落或死亡事件。默认使用转换器永久转换成真实 `PokemonEntity`；开启 `enableRChallenge` 时也可按 R 临时转换对战，未捕捉则战后还原。推荐给有复杂世界行为和任务死亡回调的生物。
- 选 `pokemon_entity`：生物从出现起就是 `PokemonEntity`，希望野生对战和捕捉完全由 Cobblemon 管理。原生 Mob 只提供来源类型和渲染外观。原生 AI 不会自动迁移；需要的世界行为由适配器添加。预置正式 Boss 由 `bossWorldMode` 默认覆盖为此模式；直接模式示例在 `examples/direct_pokemon_entity/cow.json`，不会自动加载。

在 `src/main/resources/data/<你的命名空间>/battle_profiles/<文件名>.json` 创建 profile。文件可为一个 JSON 对象或多个 profile 的 JSON 数组。重名文件由 Minecraft 数据包优先级覆盖；同一个实体类型不要在同一批数据中重复定义。预置生物配置与属性说明见 [PACK_PROFILES.md](PACK_PROFILES.md)。

## Profile 字段

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `entity` | 资源 ID | 已注册的原生 Mob 类型，例如 `minecraft:zombie` |
| `species` | 资源 ID | 已存在的 Cobblemon species；也可以是你的模组提供的自定义 species |
| `level.min`, `level.max` | 1 至 100 整数 | 首次绑定时随机等级；保存后不再重新随机 |
| `worldMode` | `native_mob` / `pokemon_entity` | 世界实体模式；省略时为 `native_mob` |
| `defeat` | `vanilla_death` / `knockout` | `native_mob` 战败后原生死亡或保留 1 点以上生命；直接模式使用 Cobblemon 原生结算 |
| `catchable` | 布尔值 | 是否允许 Cobblemon 捕捉；默认 `false`，Boss 建议为 `false` |
| `worldBehavior` | `passive` / `hostile` | `PokemonEntity` 的基础行为；原生 Mob 阶段仍使用原生 AI，默认 `passive` |
| `nativeDrops` | 布尔值 | `PokemonEntity` 额外读取来源 Mob 战利品表；原生 Mob 阶段由自身掉落 |
| `nativeExperience` | 布尔值 | `PokemonEntity` 额外给 Minecraft 经验；原生 Mob 阶段由自身结算 |
| `boss` | 布尔值 | 标记正式 Boss；必须同时 `catchable: false`。胜利后奖励一次固定 1 级个体 |
| `bossBattle` | 对象，可选 | Boss 开场群战；支持 `mode: "horde"`、`minionEntity`、`minionCount`、`levelOffset`、`minionHeldItem`；`existing_squad` 需要已登记的来源小队适配 |
| `heldItem` | 物品 ID，可选 | 接入野生宝可梦战斗时携带物品；随从优先读取本场 `minionHeldItem`，省略时读自身 profile。已有物品不覆盖，效果由对应持有物规则提供；我方野生 Boss 战斗装备不自动追加到死亡掉落 |

`catchable` 与 species 的 `catchRate` 分工不同：前者决定是否允许投球，后者参与成功率。设成 `false` 会给 Pokemon 应用 Cobblemon 的不可捕捉属性。

Boss 奖励使用 Cobblemon 的 `BATTLE_VICTORY` 事件，不使用 Minecraft 怪物掉落事件。Boss 物种的来源外观会复制到奖励个体；奖励等级固定为 1，不继承 Boss 的等级、伤害或剩余生命。安装 Team Rocket 时，奖励球通过 `pokemon_nbt` 保存完整个体数据，使用招募球后进入队伍；未安装时 API 直接放入队伍或 PC。

`bossBattle` 在 Asymmetric Battles、Horde Encounters 都已安装时自动生效，不需要独立群战开关。巫妖示例开场创建两名不可捕捉仆从，等级跟随巫妖（偏移 0，巫妖范围 33-37）。`minionEntity` 必须有非 Boss profile，`minionCount` 为 1 至 5；敌方总数不超过 Horde 的六目标格式。Horde Encounters 识别队伍并交给 Asymmetric Battles；缺少依赖或创建失败时走普通单打。此配置只在开场组队，不在中途新增仆从。巫妖半血阶段改为正式特性脚本，不再使用 `phases` 或阶段选招；招式由原生 AI 选择。删除 `bossBattle` 恢复单打。完整规则和可修改资源见 [BOSS_BATTLES.md](BOSS_BATTLES.md)。

## 特性与辅助招式

`bossBattle.mode = "existing_squad"` 是来源小队接入，当前内置暮色六骑士适配；不能直接拿它拼装其他 Boss。骑士须是同房间六名存活来源，`minionEntity` 为自身类型、`minionCount=5`、`levelOffset=0`，其他五名装备读 `minionHeldItem`。缺少依赖、来源房间或完整队伍时拒绝开战，普通 `horde` 的回退单打规则不适用于完整骑士小队。骑士自定义成标准单打时应删除此对象。修改与奖励 / 掉落边界见 [实施记录](TWILIGHT_BOSS_IMPLEMENTATION.md)。

自定义特性可直接使用 Cobblemon 的 `data/<命名空间>/abilities/<特性ID>.js` 注册机制，species 的 `abilities` 填注册 ID，不需要额外 PokemonEntity 或 Java 血量同步层。预置例子为 `entitybattletwilightdominion`（暮光支配）与 `entitybattlesoulcovenant`（魂契分担）。脚本使用块注释，名称和描述在语言资源中提供。玩家个体同样使用正式特性。

原生 `RandomBattleAI` 优先选择敌方目标，因此 API 保留队友治疗目标适配，默认支持 `healpulse`。给接入生物的 species 配置此招后，野生 AI 选中时自动指向其他合法己方单位，优先血量比例较低者。无队友则尝试其他可用招式，治疗招式被强制锁定或没有其他可用招式时 pass；玩家的手动选目标不变。作者可调用 `EntityBattleSupportMoves.registerAllyHealingMove("自定义招式ID")` 登记同类招式，招式本身的治疗效果仍由原生脚本结算。当前巫妖使用超音波，不携带治愈波动。

## 按 R 挑战开关

在单人游戏中打开“模组列表 → Entity Battle API → 配置 → Common”，可直接切换“对原生生物按 R 发起对战”。也可编辑 `config/entitybattle-common.toml` 中的 `enableRChallenge`。默认值为 `false`：按 R 不会向原生 Mob 发起本模组的宝可梦对战，玩家仍可直接攻击原生 Mob，或用转换器把目标永久变为野生 `PokemonEntity`。设置为 `true` 后重启游戏，即可恢复对准原生 Mob 按 R 临时对战。多人服务器须由服主修改服务器的同名配置文件并重启服务器；开关由服务端决定并同步给客户端。转换器不受它影响。

永久转换会舍弃原生 Mob 实体。Boss 的原生 AI、阶段机制与真实死亡事件不会自动转移到 `PokemonEntity`；`nativeDrops` 与 `nativeExperience` 只提供战利品表和 Minecraft 经验的尝试性结算。依赖原生死亡回调的任务需逐个验证。想保留战后原生死亡流程时，应使用 Minecraft 战斗，或启用按 R 的临时转换对战。

## 示例一：原生僵尸，战斗时转换

项目内可运行文件：[zombie.json](src/main/resources/data/entitybattle/battle_profiles/zombie.json)，对应的 species 是 [zombie species](src/main/resources/data/entitybattle/species/zombie.json)。关键内容：

```json
{
  "entity": "minecraft:zombie",
  "species": "entitybattle:zombie",
  "level": { "min": 5, "max": 20 },
  "defeat": "vanilla_death",
  "catchable": false,
  "worldMode": "native_mob",
  "worldBehavior": "hostile",
  "nativeDrops": true,
  "nativeExperience": true
}
```

大世界中僵尸仍是原版 `Zombie`，保留追人、攻击、装备和普通受击流程。开启 `enableRChallenge` 后，玩家瞄准按 `R` 时，模组创建真实 `PokemonEntity` 并启动 Cobblemon 标准野生对战。战斗结束后恢复僵尸；战败时按原生死亡结算。生物攻击玩家不会由本模组自动触发对战。`worldBehavior` 和两个 `native*` 字段不覆盖原生阶段；若使用转换器永久转换，这些字段会用于新生成的 `PokemonEntity`。要做可捕捉版本，把 `catchable` 改为 `true`，并为 species 设置合理的 `catchRate`。

## 示例二：牛从生成起就是宝可梦

项目内默认文件：[cow.json](src/main/resources/data/entitybattle/battle_profiles/cow.json) 使用 `native_mob`，对应 [cow species](src/main/resources/data/entitybattle/species/cow.json)。直接模式的可选示例是 [direct cow profile](examples/direct_pokemon_entity/cow.json)：

```json
{
  "entity": "minecraft:cow",
  "species": "entitybattle:cow",
  "level": { "min": 3, "max": 8 },
  "defeat": "vanilla_death",
  "catchable": true,
  "worldMode": "pokemon_entity",
  "worldBehavior": "passive",
  "nativeDrops": true,
  "nativeExperience": true
}
```

启用这个示例后，原版牛生成事件被替换为一个野生 `PokemonEntity`，牛模型由来源渲染器绘制。玩家按 Cobblemon 的正常方式进入野生对战、捕捉、收入队伍并再次放出。此模式不会自动保留挤奶、繁殖等牛的专属交互；项目内 `CowWorldBehaviorAdapter` 只安装一组可在 `PokemonEntity` 上运行的基础移动与看向目标 Goal。

## 自定义 species

若已有合适的 Cobblemon species，可直接在 profile 的 `species` 中引用它。若希望独立属性、招式、经验和捕捉率，在 `data/<命名空间>/species/<名字>.json` 定义 species。这里的 `species` ID 必须与 profile 一致；数据格式参考项目内的 `cow.json` 和 `zombie.json`。独立 species 的来源模型会自动注册到 Cobblemon 公共模型仓库，统一用于世界、PC、战斗及其他公共显示入口；不需要重画基础模型。作者提供的标准 Cobblemon 模型资源优先。注册原理、纹理扩展及骨骼 / 静态文件边界见 [MODEL_REPOSITORY.md](MODEL_REPOSITORY.md)。

`moves` 里使用 Cobblemon 已有招式 ID，例如 `1:tackle`。招式数据、属性克制、PP、状态、伤害公式和已有招式粒子由 Cobblemon 管理。来源绑定的 `PokemonEntity` 使用招式时，API 默认触发攻击挥手并由来源 Mob 渲染器绘制；可以通过 `EntityBattleAnimations.registerMove(sourceType, moveId, action)` 覆盖指定来源类型和招式的服务端动作。此回调收到的仍是 `PokemonEntity`，不会运行来源 Mob 的原生攻击 AI；依赖专用骨骼状态的动画需要客户端适配。巫妖的招式选择与视觉边界见 [BOSS_BATTLES.md](BOSS_BATTLES.md)。

## Java 扩展点

对于多部件或有专用骨骼状态的模型，在**客户端初始化**注册视觉适配器。基础模型和部件继续使用来源模组的渲染器，无需复制建模资源：

```java
EntityBattleNativeModels.register(ResourceLocation.parse("mymod:my_mob"),
        new EntityBattleNativeModels.Adapter() {
    @Override public void update(Mob visual, PokemonEntity pokemon) {
        // pokemon == null 表示 GUI。不要在此运行原生 AI 或向世界添加部件。
        String move = pokemon == null ? "" : EntityBattleNativeModels.moveFor(pokemon);
        ((MyMob) visual).setCharging(move.equals("bodyslam"));
        // 如模型依赖动画进度/部件位置，还需更新这些纯视觉状态。
    }
});
```

`EntityBattleAnimations.registerMove` 继续负责可选的服务端动作回调；API 在原生招式实际执行时统一同步招式 ID。上述客户端适配读取短期招式状态，只影响外观。注册类位于 `dev.entitybattle.client`，只能从客户端代码引用。具体暮色绑定与地图入口见 [MODEL_DISPLAY.md](MODEL_DISPLAY.md)。

若部件有独立的激活状态或只用于碰撞，可以覆写 `Adapter.isPartVisible(Mob, Entity)` 筛选需要绘制的部件；同一筛选也用于 GUI 整体尺寸计算。请在 `initialize` 中先设置初始位置与插值旧位置，避免第一帧部件从世界原点拉伸。

只用 JSON 就能完成最小接入。来源生物如果有纹理变种、特殊装备或其他客户端外观，需要按实体类型注册外观适配器：

```java
EntityPokemonOrigin.registerAppearance(MyEntities.MY_MOB.get(), new EntityPokemonOrigin.AppearanceAdapter() {
    @Override public CompoundTag save(Mob mob) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("variant", ((MyMob) mob).getVariant());
        return tag;
    }

    @Override public void load(Mob mob, CompoundTag tag) {
        ((MyMob) mob).setVariant(tag.getInt("variant"));
    }
});
```

在模组初始化时注册，确保两端都能访问该适配器。只保存外观所需字段，不要把整个原生实体 NBT 塞进 `Pokemon`。`native_mob` 战后还原使用独立的完整实体快照；捕捉进队后随 Pokemon 保存的是来源类型与可选外观快照。

来源类型与小型外观快照通过 Cobblemon 原生 aspects 进入界面数据，统一使用来源 Mob 渲染器。伤药选择、牧场列表、交易、队伍 / PC、资料、招式机、图鉴和战斗头像均使用公共绘制入口，不需要作者为每个界面另写适配。原版生物已提供颜色、幼年、变种和可见装备等外观适配。单个快照的 UTF-8 SNBT 建议不超过 4096 字节；超限时仅省略精简界面数据中的变种，仍保留正确的来源模型和 Pokemon 持久外观数据，转换不会因此失败。第三方自行绘制且绕过 Cobblemon 公共入口的界面需另行对接。实现、验证范围与限制见 [MODEL_DISPLAY.md](MODEL_DISPLAY.md)。

`pokemon_entity` 模式要扩展世界行为，可以注册 `EntityPokemonWorldBehaviorAdapters.register(entityId, adapter)`。适配器收到的是 `PokemonEntity`，可向其 Brain/Goal 安装可复用行为；不能把只接受原生 Mob 子类的 Goal 直接强转使用。项目内 `CowWorldBehaviorAdapter` 是可编译的参考。注册表以来源 `entityId` 选择适配器，同类行为可由多个实体类型共用。

代码注册 profile 也可用 `EntityBattleProfiles.register(new EntityBattleProfile(...))`；数据包中同一 `entity` 的 profile 会覆盖代码注册值。接入模组只应在初始化阶段注册一次。

## 转换道具与创造标签页

本模组注册独立创造物品栏标签页“生物宝可梦化”，其中有 `entitybattle:converter`。用它右击已接入的原生生物，满足来源资格时会永久转换为野生 `PokemonEntity`。道具不消耗，当前没有合成配方；它不是只在创造模式才能使用。`pokemon_entity` 的 Boss 遇到未完成的原作开战条件时先保留剧情交互，解锁后自动转换，无需道具。默认自动模式的战斗中断保留宝可梦；原生模式的临时 R 战斗中断恢复来源。

## 原生剧情与死亡回调接口

复杂 Boss 可以在初始化阶段通过 `EntityBattleSources.register(entityId, adapter)` 注册 `EntityBattleSourceAdapter`，同一种实现可共享给多个来源 ID。一般生物不必注册此接口。

- `denial(Mob)`：允许时返回 null，否则返回拒绝原因组件。R、转换器及自动转换共用，不能绕过来源剧情资格。
- `manualWorld()`：可选强制原生世界模式，不与剧情绑定。剧情门槛应放在 `denial`，默认模式将等待条件满足再自动转换；只有希望始终手动选择转换的来源才返回 true。
- `reset(Mob)`：中断后在原生来源上执行原作重试流程，不自行复制 AI。
- `complete(Mob, DamageSource)`：完整胜利或允许的局外死亡在真正原生实体上调用死亡回调。默认实现调用原生 `die`，保留自定义死亡掉落、进度、地牢解锁等事件；返回实际死亡是否被接受。

框架记录来源 UUID、独立实体快照及明确属于来源的助手。完整胜利才提交来源结算；中断是否恢复原生来源由临时 / 永久转换决定。所有者不明确的附近生物不会被任意清理。自定义召唤来源应在助手生成时记录 `EntityBossSources.OWNER` 的来源 UUID。分离生成的原生身体可通过 `EntityBattleSources.registerPart(partId, resolver)` 归属一个控制实体，并在客户端 `EntityBattleNativeModels.Adapter.parts` 提供仅显示的身体副本，不能让副本加入世界或运行 AI。狂瞳龙卷是此类示例。快照不会复制到玩家的 1 级 Boss 奖励。外部死亡取消事件及来源回调的不可逆副作用边界见 [原版与天境实施记录](OTHER_BOSS_IMPLEMENTATION.md)。

## 验证步骤

谜题羊是普通 `native_mob`，`catchable=true`、捕获率 45；`QuestRamSource` 仅以原生 `getRewarded()` 判断羊毛任务是否真正领奖，不设置 `NoAI`。`TwilightBossAppearance` 保存 `ColorFlags` 与 `Rewarded` 用于转换后的颜色显示。完成任务不会自动转换；捕捉后使用 Cobblemon AI，不再运行原生羊毛任务。中文说明见 [谜题羊设定](QUEST_RAM.md)。

十五份中文 Boss 设定页与两份 Word 属性表都由源码 JSON 生成；字段与来源 ID 保留在此开发文档，中文设定页只显示中文名称与规则。公开脚本不依赖本机整合包路径，使用 `--mods` 指定包含来源模组与中文本地化资源的目录，也可设置 `ENTITYBATTLE_DOC_MODS` 环境变量：

```powershell
python validation/update-boss-docs.py --check --mods "你的模组目录"
python validation/profile_document.py --mods "你的模组目录"
```

前者检查十五份设定与源码的一致性，去掉 `--check` 可重建页面；后者更新两份 Word，仍需渲染检查。需要 Python 与 `python-docx`。脚本只在开发时运行，不进入模组运行流程。

1. 全部预置来源均安装时应在日志看到 `Loaded 169 entity battle profiles`；缺少可选来源会跳过相应条目。检查 species 没有解析错误。
2. 默认配置下对僵尸按 `R`：不应触发本模组对战。启用 `enableRChallenge` 并重启后再按 `R`：应进入 Cobblemon 野生单打，队伍最多六只按正常规则依次上场；逃跑后恢复同一只僵尸和其 AI；战胜时触发原生死亡、掉落和经验。
3. 启用 `enableRChallenge`，临时把僵尸 `catchable` 设为 `true` 并调整 species `catchRate`，验证按 R 对战捕捉成功后不会重新生成僵尸，宝可梦能收入队伍、收回和再次放出。
4. 生成牛：默认应是保留挤奶、繁殖和原生 AI 的牛；可直接进行 Minecraft 战斗，或用转换器变成宝可梦后再对战。只有开启 `enableRChallenge` 才能对原生牛按 R 临时对战。要验证直接模式，可把 `examples/direct_pokemon_entity/cow.json` 的内容用于覆盖默认牛 profile；此时世界中应只有一个野生 `PokemonEntity`，并检查牛外观、捕捉、战斗血条、队伍头像及放出收回动画。
5. 在创造界面找到独立标签页及转换器。用转换器右击已接入的原生僵尸，确认世界里变成真实 `PokemonEntity`，并可走 Cobblemon 对战；未接入实体不应转换。
6. 对来源模组的复杂实体，额外检查装备、变种、骑乘、卸载/重启、死亡回调和第三方任务计数。

开发目录执行 `./gradlew build`；生成的 JAR 位于 `build/libs/entitybattle-0.2.0-beta.1.jar`。专项验证范围见 [验证说明](validation/README.md)，自然地牢流程及整合包显示仍需实机复测。

## 固定初始四招与 NPC 外观

profile 可选 `moves: ["shadowball", "flameburst", "supersonic", "protect"]`，最多四个不重复的 Showdown ID。它只配置新个体及主动重新初始化的临时阵容，不修改 species 学习表或保存中的玩家队伍。省略字段按等级学招。临时随从更改等级后也使用同一初始化入口。

不由世界 Mob 转换而来的 NPC / 奖励宝可梦，可以调用 `EntityPokemonOrigin.setPresentation(pokemon, sourceEntityId, appearanceOrNull)` 添加来源模型与招式表现。该调用不添加源实体 UUID，不授权世界掉落和 Boss 奖励结算；已有来源绑定不会被覆盖。NPC 用 Cobblemon 原生 `NPCPartyStore` 与 `BattleBuilder.pvn`，例子见 [暮色领主](TWILIGHT_LORD.md)。

群战可选 `bossBattle.minionMoves` 定义临时援军的四招，优先于来源随从 profile 的 `moves`，不改变普通野生同种生物的学习规则。已有 Java 构造函数保留，新增字段省略时按既有行为执行。
