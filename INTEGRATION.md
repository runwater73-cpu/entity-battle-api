# EntityBattle API 接入指南

适用：Minecraft 1.21.1、NeoForge 21.1.x、Cobblemon 1.8.0-1.8.1。接入目标是现有 `Mob` 类型，不必重新制作它的模型和基础动画。战斗资料使用 Cobblemon species，世界实体模式由 profile 选择。

## 选择模式

- 选 `native_mob`：生物在大世界继续运行原生 AI、攻击、交互、装备、掉落或死亡事件。默认使用转换器永久转换成真实 `PokemonEntity`；开启 `enableRChallenge` 时也可按 R 临时转换对战，未捕捉则战后还原。推荐给有复杂世界行为和任务死亡回调的生物。
- 选 `pokemon_entity`：生物从出现起就是 `PokemonEntity`，希望野生对战和捕捉完全由 Cobblemon 管理。原生 Mob 只提供来源类型和渲染外观。原生 AI 不会自动迁移；需要的世界行为由适配器添加。项目默认预置清单全部使用 `native_mob`；直接模式示例在 `examples/direct_pokemon_entity/cow.json`，不会自动加载。

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

`catchable` 与 species 的 `catchRate` 分工不同：前者决定是否允许投球，后者参与成功率。设成 `false` 会给 Pokemon 应用 Cobblemon 的不可捕捉属性。

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

若已有合适的 Cobblemon species，可直接在 profile 的 `species` 中引用它。若希望独立属性、招式、经验和捕捉率，在 `data/<命名空间>/species/<名字>.json` 定义 species。这里的 `species` ID 必须与 profile 一致；数据格式参考项目内的 `cow.json` 和 `zombie.json`。模型不需要在 species 中另做一份，因为 `EntityPokemonNativeVisuals` 使用来源 Mob 渲染器；PC 预览与储存格由客户端外观适配层绘制，其他依赖 species 资源的界面仍须在客户端测试。

`moves` 里使用 Cobblemon 已有招式 ID，例如 `1:tackle`。招式数据、属性克制、PP、状态和伤害公式都由 Cobblemon 管理。自定义招式本身需要遵循 Cobblemon 的数据格式；本 API 目前没有把来源 Mob 的攻击骨骼动画自动绑定到招式。

## Java 扩展点

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

`pokemon_entity` 模式要扩展世界行为，可以注册 `EntityPokemonWorldBehaviorAdapters.register(entityId, adapter)`。适配器收到的是 `PokemonEntity`，可向其 Brain/Goal 安装可复用行为；不能把只接受原生 Mob 子类的 Goal 直接强转使用。项目内 `CowWorldBehaviorAdapter` 是可编译的参考。注册表以来源 `entityId` 选择适配器，同类行为可由多个实体类型共用。

代码注册 profile 也可用 `EntityBattleProfiles.register(new EntityBattleProfile(...))`；数据包中同一 `entity` 的 profile 会覆盖代码注册值。接入模组只应在初始化阶段注册一次。

## 转换道具与创造标签页

本模组注册独立创造物品栏标签页“生物宝可梦化”，其中有 `entitybattle:converter`。用它右击 `native_mob` profile 对应的原生生物，会把该个体永久转换为野生 `PokemonEntity`，不会在战斗结束后还原。道具不消耗，当前没有合成配方；它不是只在创造模式才能使用。`pokemon_entity` profile 的自然生成本身就会自动转换，因此没有可供道具操作的常驻原生 Mob。

## 验证步骤

1. 使用随包清单时应在日志看到 `Loaded 167 entity battle profiles`，并检查 species 没有解析错误。
2. 默认配置下对僵尸按 `R`：不应触发本模组对战。启用 `enableRChallenge` 并重启后再按 `R`：应进入 Cobblemon 野生单打，队伍最多六只按正常规则依次上场；逃跑后恢复同一只僵尸和其 AI；战胜时触发原生死亡、掉落和经验。
3. 启用 `enableRChallenge`，临时把僵尸 `catchable` 设为 `true` 并调整 species `catchRate`，验证按 R 对战捕捉成功后不会重新生成僵尸，宝可梦能收入队伍、收回和再次放出。
4. 生成牛：默认应是保留挤奶、繁殖和原生 AI 的牛；可直接进行 Minecraft 战斗，或用转换器变成宝可梦后再对战。只有开启 `enableRChallenge` 才能对原生牛按 R 临时对战。要验证直接模式，可把 `examples/direct_pokemon_entity/cow.json` 的内容用于覆盖默认牛 profile；此时世界中应只有一个野生 `PokemonEntity`，并检查牛外观、捕捉、战斗血条、队伍头像及放出收回动画。
5. 在创造界面找到独立标签页及转换器。用转换器右击已接入的原生僵尸，确认世界里变成真实 `PokemonEntity`，并可走 Cobblemon 对战；未接入实体不应转换。
6. 对来源模组的复杂实体，额外检查装备、变种、骑乘、卸载/重启、死亡回调和第三方任务计数。

开发目录执行 `./gradlew build`；生成的 JAR 位于 `build/libs/entitybattle-0.1.0-dev.jar`。当前已验证编译与预置数据的一致性；客户端战斗、捕捉、创造界面及复杂 Boss 事件仍需实机复测。
