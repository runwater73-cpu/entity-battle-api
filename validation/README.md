# Boss 隔离服务端验证

此目录保留开发诊断，正常构建不包含诊断源码。诊断会建立模拟玩家、触发真实野生群战、校验阶段与清理，再自动停止服务端。仅在隔离目录使用，不要加入实际游戏模组。

## 玩偶渲染与来源材质

`doll-smoke.init.gradle` 使用 `build/model-smoke-run` 的隔离客户端 / 世界副本。准备模型专项依赖，再加入实际 CobblemonDoll 0.4.7 与 Kaleidoscope Doll 1.4.1。完整来源审计还需安装 167 个来源所属的可选内容模组及依赖；本次另装实际 EMF / ETF。第三方资源不进入发布包，不要引用用户存档。

```powershell
.\gradlew.bat -I validation/doll-smoke.init.gradle runClient '-Pneo_version=21.1.249' *> build/doll-client-validation.log
```

检查 `DOLL_SMOKE PASS`、`MODEL_AUDIT PASS`、`MODEL_REPLACEMENT PASS` 且之后没有 `DOLL_SMOKE FAIL`。验证真实物品 / 方块 / 投掷实体渲染器、consumer 后备绘制、名字、外观往返、底部位置、双向融合和缓存重建。审计结果在隔离目录 `native-texture-audit.json`，截图在 `doll-preview.png`。测试中的模型替换开关仅在隔离客户端改动并恢复；不写用户配置。旋风、出土僵尸和冰雪守卫的默认预览没有普通网格，日志及报告明确标识，不能据此宣称它们视觉验证通过。

`doll-server.init.gradle` 使用独立 `build/doll-server-run`。准备已接受 EULA 的测试服务器、独立端口以及实际两个玩偶 JAR：

```powershell
.\gradlew.bat -I validation/doll-server.init.gradle runServer '-Pneo_version=21.1.249' *> build/doll-server-validation.log
```

检查 `DOLL_SERVER PASS` 且无 FAIL，覆盖专用服务器真实转换、完整物种 ID、精确外观、原模组宝可梦身份 / UUID 标记与 ItemStack 保存 / 加载。测试服务器仅装基础依赖与玩偶也能运行，其他内容模组为可选。

正常打包不带 `-I` 参数，检查 JAR 不包含任何 `dev/entitybattle/check/`。

## 完整遭遇结算专项

`encounter-smoke.init.gradle` 使用相同隔离服务器，检查 `BOSS_ENCOUNTER PASS` 且无 FAIL。它通过真实野生群战让暮初恶魂主 Boss 单独倒下，等待普通死亡移除期限后再执行原生距离逃跑：期间不得有来源掉落或 1 级奖励，逃跑后主 Boss 恢复开战前 HP，临时随从清理。重新挑战并完整击败敌方，检查一次来源掉落及一个 1 级奖励，重复胜利事件和后续死亡均不得重复发放。最后检查按 R 临时转换的原生 Boss 倒下后中止，以及真实玩家退出事件：原始 Mob UUID 恢复且存活，退出事件须在下一服务器 tick 前完成恢复。掉落实体会合堆，检查使用物品总数量而非实体个数；主 Boss 按物种查找，不假定 Horde 名单首项就是主 Boss。所有强制 KO 都只在此隔离诊断中发送，不进入游戏功能。

```powershell
.\gradlew.bat -I validation/encounter-smoke.init.gradle runServer *> build/boss-encounter-server.log
```

## 准备

暮色全部预设与不掉装备规则的专用检查使用 `twilight-smoke.init.gradle`：七场 1+2 群战、真实死亡掉落方法和普通野生对照、原有六骑士的回合 / 换宠 / 恢复 / 一次奖励 / 原生宝箱。它将隔离服务器的 `bossWorldMode` 提前设为 `NATIVE_MOB`（该项需要重启），不会改整合包配置。

```powershell
.\gradlew.bat -I validation/twilight-smoke.init.gradle runServer *> build/twilight-boss-server.log
if (-not (Select-String build/twilight-boss-server.log -Pattern 'TWILIGHT_SMOKE PASS' -Quiet)) {
    throw 'Read the log: a successful Gradle exit alone does not mean the diagnostic passed.'
}
node validation/ur-ghast-ability.cjs
node validation/lich-abilities.cjs
```

娜迦奖杯限定兼容用 `node validation/naga-trophy.cjs <已安装0.2.53脚本的只读审查路径>` 检查。第三方脚本只在忽略的审查目录中读取，不进入本项目源码或发布包。`apply-twilight-design.cjs` 是已确认的数据变更记录；它会写资源 JSON，日常构建不会运行它。

1. 使用 Java 21，按主项目要求准备 NeoForge 开发依赖。
2. 在 `build/boss-smoke-run/mods` 放入 Asymmetric Battles 1.1.0、Horde Encounters 1.0.0、ForgeConfigAPIPort 21.1.6 和暮色森林 4.8.3345。第三方 JAR 不随本项目分发。
3. 在 `build/boss-smoke-run/eula.txt` 写入 `eula=true`，表示已阅读并同意 Minecraft 服务端 EULA。
4. 为此隔离目录设置服务端端口，例如 `server.properties` 中的 `server-port=25579`；不要引用用户存档。建议 `online-mode=false`、`view-distance=2`、`simulation-distance=2`。

在项目根目录运行，Cobblemon 路径替换为本地 1.8.1 JAR：

```powershell
.\gradlew.bat -I validation/boss-smoke.init.gradle runServer "-Pcobblemon_local_jar=C:\path\Cobblemon-neoforge-1.8.1+1.21.1.jar" *> build/boss-smoke-gradle.log
if (-not (Select-String -LiteralPath build/boss-smoke-gradle.log -Pattern 'BOSS_SMOKE PASS' -Quiet)) {
    throw 'Boss integration validation failed; read the log.'
}
```

必须检查 `BOSS_SMOKE PASS`，不能只检查 Gradle 的退出码：服务端正常停止也可能是诊断发现错误后主动停止。

验证包括完整六只队伍、墙体遮挡、三名敌方、禁捕、奖杯和剩饭装备、正式特性注册、所有伤害分摊、半血后回血不回退、原生 AI 行动与保留的队友治疗适配、招式排队动画、正常结束清理。客户端 UI、奖杯脚本和完整整合包范围见 [BOSS_VERIFICATION.md](../BOSS_VERIFICATION.md)。

## 特性结算专项

隔离服务器运行后，其未修改的 Showdown 引擎位于 `build/maid-smoke-run/showdown` 或 `build/boss-smoke-run/showdown`。使用 Node 执行：

```powershell
node validation/lich-abilities.cjs build/maid-smoke-run/showdown
```

脚本通过原生注册表加载本项目的特性，执行实际伤害、招式与回合末持有物流程。检查 `LICH_ABILITY PASS`。22 组检查覆盖真实招式攻击及扣血协议、均分、间接伤害、混乱自伤和挣扎反伤、死亡去重、半血与立即回血、换宠和新战斗、实际 HP 吸取、替身、群攻、多段攻击、禁疗、污泥和剩饭。引擎和第三方代码不随验证脚本分发。

诊断通过临时 Gradle 初始化脚本增加源码目录。验证完成后，正常运行 `gradlew.bat build`，不要带 `-I` 参数；正常发布 JAR 中不应有 `dev/entitybattle/check/BossSmoke.class`。

## 女仆 2v3 专项

追加 `-Pmaid_knights=true` 可改为实际 TeamRocket 女仆 2v6 原有骑士专项，覆盖原生回合 / 不服从 / 换宠、领队被收回但仍有队友、六名敌方全部倒下后的胜利分发窗口以及正常结算。检查 `MAID_KNIGHTS PASS`，同时确认后面没有 `MAID_SMOKE FAIL`。诊断收回实体发生在等待玩家输入的窗口，不清空原生动画队列。

使用 `maid-smoke.init.gradle` 和 `maid-src`，隔离目录为 `build/maid-smoke-run`。除上述 Boss 依赖外，还需实际的 TeamRocket 1.1.6、车万女仆、RCTAPI、Journeysouvenirs 及其依赖（包括 TeamRocket 实际使用的机械动力）。用 NeoForge 21.1.248 或以上版本，第三方 JAR 不随项目分发。

```powershell
.\gradlew.bat -I validation/maid-smoke.init.gradle runServer '-Pneo_version=21.1.249' "-Pcobblemon_local_jar=C:\path\Cobblemon-neoforge-1.8.1+1.21.1.jar" *> build/maid-smoke-gradle.log
if (-not (Select-String -LiteralPath build/maid-smoke-gradle.log -Pattern 'MAID_SMOKE PASS' -Quiet)) {
    throw 'Maid horde compatibility validation failed; read the log.'
}
```

专项测试在隔离世界中调整纪念品模组公开配置，让首回合必定出现不服从，之后验证换宠和正常攻击。没有改变整合包配置。该测试源码也不进入正常发布 JAR。结果与限制见 [MAID_HORDE_COMPATIBILITY.md](../MAID_HORDE_COMPATIBILITY.md)。

女仆测试现在只携带空气斩、龙之波动，覆盖 `any` 招式指向 Horde 预留空位的风险。它显式检查六个空位修复案例、256 次 Random AI 与 64 次 Strong AI 选择、有效目标保留，再运行实际女仆和完整回合结算。

可在服务端提取的原引擎上单独复现该空位异常：

```powershell
node validation/horde-empty-targets.cjs build/maid-smoke-run/showdown
```

检查 `HORDE_TARGET PASS`：两招指向第 4 空位均抛异常并停在第 1 回合，分别指向三个真实敌人的六组对照均推进到第 2 回合。该脚本用于验证故障原因；Java 兼容是否生效由上述真实女仆服务端诊断验证。

## 界面数据与客户端加载

`gui-smoke.init.gradle` 的消息专项需在隔离目录 `build/gui-smoke-run/mods` 放入实际暮色森林 JAR，以核对奖杯的原作翻译。检查 `BATTLE_MESSAGES PASS` 与 `GUI_SMOKE PASS` 且无 FAIL：八种 Boss / 随从特性的中文名称及说明、九种暮色奖杯的持有物提示、幻影骑士反伤翻译键、文本通知去协议外壳、敌我名称翻译组件及其他未知指令保留。诊断仅为检查名称转换而注册测试特性，不启动战斗或修改用户游戏数据。

`multipart-smoke.init.gradle` / `multipart-src` 在 `build/multipart-smoke-run` 的独立客户端中打开隔离服务器存档的副本 `saves/multipart-smoke-world`。准备相同的暮色及群战依赖，再放入实际 Xaero 小地图 26.4.2 JAR。不要引用用户存档。

```powershell
.\gradlew.bat -I validation/multipart-smoke.init.gradle runClient *> build/multipart-client-fix.log
```

检查 `MULTIPART_SMOKE PASS`。诊断执行八个来源的实际原生 GUI 绘制、娜迦节段及 HP 比例、九头蛇头颈、女王冰盾、原生姿态和 Xaero 图标生成 / 缓存；截图保存在隔离目录的 `multipart-preview.png`。另检查真实娜迦世界生成转换、持久标记 NBT 保存、正向消失判定下 Boss 保留，以及普通接入 Mob 正常消失的对照。绘制调用通过不等于用户整合包中的资源包、光影和全部界面均完成视觉验收。

Boss 诊断中的 `verifyPresentation` 校验红色幼年羊外观往返及伤药、交易、牧场原生 DTO 的编解码。它只检查界面数据传输。

`gui-smoke.init.gradle` 只在隔离客户端加载 `gui-src` 诊断，确认公共绘制入口及相关界面实际能够加载，随后退出：

```powershell
.\gradlew.bat -I validation/gui-smoke.init.gradle runClient "-Pcobblemon_local_jar=C:\path\Cobblemon-neoforge-1.8.1+1.21.1.jar" *> build/gui-smoke-client.log
if (-not (Select-String -LiteralPath build/gui-smoke-client.log -Pattern 'GUI_SMOKE PASS' -Quiet)) {
    throw 'GUI mixin loading validation failed; read the log.'
}
```

加载通过不等于视觉验收。模型位置、比例、界面裁剪和战斗血条需要在游戏中确认；额外普通回合诊断的等待超时记录见 [BOSS_VERIFICATION.md](../BOSS_VERIFICATION.md)。

## 公共模型仓库与融合贴图

`model-smoke.init.gradle` / `model-src` 在 `build/model-smoke-run` 打开同名的独立世界副本。依赖与女仆客户端相同，另需实际 TeamRocket 1.1.6、Xaero 26.4.2。如果第三方 resolver 引用了 Cobblemon 本体未提供的模型，必须在此隔离目录安装其需要的资源提供者，不能以停在加载遮罩的状态当成成功。本次使用整合包中的资源包及 Mega Showdown / GeckoLib 补齐第三方引用；这些资源不进入源码或发布 JAR。

```powershell
.\gradlew.bat -I validation/model-smoke.init.gradle runClient '-Pneo_version=21.1.249' *> build/model-repository-client.log
if (-not (Select-String -LiteralPath build/model-repository-client.log -Pattern 'MODEL_SMOKE PASS' -Quiet)) {
    throw 'Registered model validation failed; read the log.'
}
```

检查 PASS 后面也没有 FAIL。诊断通过公共仓库获取并实际绘制全部有效独立 species；核心样本走 Cobblemon 原来的资料、头像与世界绘制入口。检查红色幼年羊的精简外观、八个暮色 Boss 的招式 / 部件状态、原生与官方模型双向融合的动态贴图及实际像素变化、注册重建。世界绘制用记录顶点的消费者，牛 / 雪怪首领与来源渲染器作实际高度比较；世界插值固定为 0.5，不把 `Screen.render` 的动画增量作为世界插值。截图在隔离目录 `model-preview.png`，必须另外检查布局。完整资源管理器 F3+T、用户存档融合配方、所有第三方骨骼扩展和完整整合包光影仍需实机验收；注册重建不是完整资源重载的替代证明。

正常发布构建不带 `-I`，并检查 JAR 不含 `dev/entitybattle/check/`。

资料显示专项还需出现 `MODEL_LAYOUT PASS`：用官方 `ModelWidget` 的 66×66 裁切范围、锚点、比例与旋转检查十二个样本的实际完整顶点范围，覆盖巫妖头冠 / 装备与大型多部件；另外按原版 `StorageSlot` 的 25×25 格尺寸、锚点、比例、旋转和裁切范围检查完整 PC 格内几何。追加大小为 8 的史莱姆，确认相同物种的个体大小也自动适配两个范围。分别以 0.05、0.45、0.95 插值检查 GUI 头部角度保持鼠标输入、世界展示头部保持水平。诊断随后实际绘制同样参数的官方资料控件，保存 `summary-preview.png`，必须检查截图。此前只绘制公共入口的 `MODEL_SMOKE PASS` 不代表这些具体控件没有裁切。

头像专项还需出现 `MODEL_PORTRAIT PASS`，检查之后没有 FAIL。它按原版队伍 HUD 的 21×21 框、完整战斗血条的 28×28 框及紧凑血条的 19×19 框，分别使用实际锚点、scale 和公共头像变换，检查十二个核心样本与大小为 8 的史莱姆的完整顶点范围；每种尺寸同时覆盖正向 / 镜像。随后通过真实 `GuiUtils.drawPosablePortrait` 带裁切绘制，保存 `portrait-preview.png`。此前只调用公共头像函数成功，并不能证明头像处于队伍栏显示范围内。

首次显示专项还需 `MODEL_TEXTURE_FIRST PASS`：新建独立界面状态，先查询贴图，再按原版资料、PC 与头像参数绘制，检查娜迦和九头蛇完整顶点在显示框内；这会复现尚未准备多部件时缓存头部尺寸的问题。随后真正打开官方 `Summary`，六个实际 `PartySlotWidget` 绘制完成后输出 `MODEL_ACTUAL_SUMMARY PASS`，截图在 `actual-boss-summary.png`。必须检查截图，不能仅依据 Gradle 的成功退出。

## 玩偶生命周期与 Cobbledex / JEI

`doll-smoke.init.gradle` / `doll-src` 使用上述独立客户端世界。准备实际 Kaleidoscope Doll 1.4.1、CobblemonDoll 0.4.7、TeamRocket 1.1.6、Cobbledex 2.28.8、JEI 19.57.0.446 及它们的依赖，不修改用户存档或配置。

```powershell
.\gradlew.bat -I validation/doll-smoke.init.gradle runClient '-Pneo_version=21.1.249' *> build/cobbledex-doll-lifetime-client.log
```

需要同时出现 `DOLL_FUSION_LIFETIME PASS`、`COBBLEDEX PASS` 和 `DOLL_SMOKE PASS`，并且无后续 FAIL。生命周期检查调用 TeamRocket 的真实 `removeForm` 与 `releaseAll` 后，再绘制同一个持久化融合玩偶的物品、方块、投掷入口；重新生成的融合纹理逐像素与清理前比较。Cobbledex 检查实际物种索引与 JEI 插件 `registerIngredients`，核对全部当前有效来源，保留官方喷火龙查询，并通过其真实图标生成器绘制牛、娜迦、九头蛇、巫妖的静态 PNG。最终图标保存在隔离客户端目录 `cobbledex-*.png`。REI / EMI 未在此专项启动。

`doll-server.init.gradle` / `doll-server-src` 使用 `build/doll-server-run`，只安装两项玩偶模组及其必要依赖，刻意不安装暮色森林和天境。

```powershell
.\gradlew.bat -I validation/doll-server.init.gradle runServer '-Pneo_version=21.1.249' *> build/species-availability-server.log
```

检查 `SPECIES_AVAILABILITY PASS` 与 `DOLL_SERVER PASS` 且无 FAIL：原版牛可用，缺失来源的娜迦和恐鸟不出现在已实现列表；专服仍能执行真实玩偶转换、身份写入、物品保存与加载，客户端可选查看器 Mixin 不进入服务端路径。所有 Gradle 诊断顺序执行，正常发布前重新构建不带 `-I` 的 JAR，并检查诊断类未进入产物。

## 新 Boss 来源剧情与结算

`source-boss-smoke.init.gradle` / `source-boss-src` 使用 `build/source-boss-smoke-run`，依赖实际天境 1.5.10、Asymmetric Battles 1.1.0、Horde Encounters 1.0.0、TeamRocket 1.1.6 及其必要依赖。配置和存档仅来自该隔离目录，初始化脚本将其 Boss 世界模式设为原生，以检验手动转换；不操作用户世界。

```powershell
.\gradlew.bat -I validation/source-boss-smoke.init.gradle runServer '-Pneo_version=21.1.249' *> build/source-boss-server.log
node validation/other-boss-abilities.cjs build/source-boss-smoke-run/showdown
```

必须检查 `SOURCE_BOSS PASS` 和 `OTHER_ABILITIES PASS`，且无后续 FAIL。来源测试使用原作对话、勋章和合法伤害打开挑战资格，调用真实 R 转换和原生选招包，检验五个 Boss 的单体 / 六单位 / 四单位阵容、中断还原、原作房间解锁、巨灵永昼结束、凋灵下界之星和一次性奖励。为隔离原作移动，来源测试实体关闭 AI；首回合实际选择，完整胜利通过引擎强制 KO 验证结算，不能当成自然地牢、女仆多人或战斗平衡的完整游玩验收。强制 KO 必须等待原生战斗初始化和动画队列完成。

两个专属特性的 Node 诊断使用隔离服务器提取的原始 Showdown 引擎，涵盖普通晴天、敌方冰攻击、替身 / 间接伤害、其他天气、破格、致命伤害、半血多段攻击完整结束、甲胄球弹免疫、治疗 / 抑制 / 换下 / 新战斗。

## JEI 模型物品坐标与新 Boss 姿态

```powershell
.\gradlew.bat -I validation/item-icon-smoke.init.gradle runClient '-Pneo_version=21.1.249' *> build/item-icon-client.log
```

使用 `build/model-smoke-run` 的隔离客户端及原版、暮色、天境依赖。检查 `ITEM_ICONS PASS` 和 `OTHER_BOSS_POSES PASS`，且无后续 FAIL：十一种来源通过真正的 `PokemonItemRenderer` GUI 物品绘制，完整几何落在物品框内；来源原生 public 标记验证魔石苏醒、巨灵冷却、凋灵护甲，以及监守者攻击 / 音爆动画时钟和停止。实际物品截图为 `jei-item-origin.png`。这验证运行时入口，不替代全部整合包资源包下的视觉检查。

## 2026-10-07 流程等级、城堡训练家与 JEI

- `apply-boss-progression.cjs` 重放已审批的 13 个 Boss 等级与固定四招，巫妖仆从同级。它是开发辅助，发布 JAR 不执行。
- `gradlew.bat -I validation/trainer-smoke.init.gradle runServer -Pneo_version=21.1.249`：使用隔离 `build/trainer-smoke-run`，真实安装版本的凉亭模板、标记自动放置、原生 NBT 重载、六只队伍/持有物、原生训练家首回合/后备轮换/结束清理。日志需出现 `TRAINER_SMOKE PASS`。
- 本轮客户端 `item-icon-client.log` 已分别出现 `ITEM_ICONS PASS` 和 `OTHER_BOSS_POSES PASS`，覆盖十一来源的物品模型坐标与新 Boss 原作姿态标志 / 动画时钟。
- `encounter-new-boss-regression.log` 的 `BOSS_ENCOUNTER PASS` 验收主 Boss 先倒下再逃跑不结算，以及全场胜利只结算一次。
- 向日葵专项使用 `sunflower-smoke.init.gradle`，隔离测试目录必须安装森罗物语暮色及厨房 JAR；日志应出现 `SUNFLOWER_BOSS PASS`，覆盖 45 级 profile、原生手动世界、独立属性 / 特性 / 四招、转换器、原生攻击归属清理、原生来源中断恢复、胜利后的深色宝箱和持有物不掉落。专项缺少来源会失败；正常发布包缺少该可选模组则跳过 profile，仍可加载。
- 新 Boss 来源验证分两次记录：`source-boss-aether-passed.log` 三个天境 `CASE PASS`，`source-boss-server.log` 两个原版 `CASE PASS`，不把五个案例说成同一次运行。
- 测试角色为 FakePlayer；源码测试通过不能代替实际自然地牢游玩、客户端交互与平衡测试。所有验证 sourceSet 通过 `-I` 临时加入，正常构建不包含。

向日葵最终日志 `sunflower-boss-server.log` 已出现 `SUNFLOWER_BOSS PASS`，`item-icon-sunflower-client.log` 已出现 `SUNFLOWER_POSES PASS`、`OTHER_BOSS_POSES PASS` 与覆盖十二来源的 `ITEM_ICONS PASS`；均无后续 FAIL。服务器全场胜利使用强制 KO 验证原生宝箱与一次性结算，客户端四招分别验证来源同步时钟起止，不能替代自然游玩的动画和原作流程验收。城堡训练家最终 `twilight-lord-server.log` 的 `TRAINER_SMOKE PASS` 真实验证首只倒下后的原生 AI 后备换宠；测试保存首只对象，避免换宠调整列表顺序造成误判。

## 2026-10-08 剧情后自动转换与中文设定

上述手动世界 / 城堡训练家记录属于对应日期的历史检查。用户已取消后续暮色领主制作；最新天境与向日葵默认自动转换。

```powershell
.\gradlew.bat -I validation/deep-aether-smoke.init.gradle runServer '-Pneo_version=21.1.249' *> build/deep-aether-boss-server.log
```

`DEEP_AETHER_BOSS PASS` 覆盖四个原作开战门槛、自动转换、控制者身体清理、真实首回合、中断、原生钥匙与地牢，以及向日葵自动转换、谜题羊原生人工智能 / 任务领奖 / 捕捉政策。使用 `build/deep-aether-smoke-run`，依赖实际天境、深入天境、暮色、森罗暮色与厨房及必需依赖。完整胜利强制击倒不代表自然平衡测试。测试玩家必须通过原生地牢入场回调登记，避免原作无人入场减少身体分段；测试实体应远离尚未可访问的区块边界。

`update-boss-docs.py` 从当前物种与接入配置生成十五份中文设定，`--check` 检查一致性并拒绝页面正文出现英文。公开的 `validation/profile_document.py` 同步两份既有 Word 属性表，不在发布包中运行。两个脚本都通过 `--mods "你的模组目录"` 或 `ENTITYBATTLE_DOC_MODS` 指定本地化资源目录，不依赖开发机路径。两份 Word 正文不得有英文标识，字段集中在开发接入文档；修改后必须渲染检查。

客户端最终 `item-icon-deep-aether-client.log` 已出现 `OTHER_BOSS_POSES PASS`、`SUNFLOWER_POSES PASS`、`DEEP_AETHER_POSES PASS` 与十三来源的 `ITEM_ICONS PASS`。实际物品截图 `jei-item-origin.png` 已查看，狂瞳龙卷的完整身体可见，模型在物品绘制范围内。天境带入的旧嵌套饰品库在该隔离客户端有注入冲突；验证目录安装与用户整合包一致的 Accessories 1.1.0-beta.53 后通过，未修改第三方 JAR 或用户目录。

```powershell
.\gradlew.bat -I validation/source-availability-smoke.init.gradle runServer '-Pneo_version=21.1.249' *> build/source-availability-server.log
```

该专项使用 `build/source-availability-run`，不安装暮色、天境、深入天境和森罗物语。最终日志已出现 `SOURCE_AVAILABILITY PASS`：缺少可选来源不会加载其实体类，相关 profile 与已实现物种被排除，原版牛接入仍可用。检查成功标记和后续失败，不能只以 Gradle 退出码判断游戏成功。

## 火箭队招募机与招募球

```powershell
.\gradlew.bat -I validation/recruit-smoke.init.gradle runServer '-Pneo_version=21.1.249' *> build/recruitment-server.log
```

隔离目录 `build/recruit-smoke-run` 安装火箭队 1.1.6 及其前置，包括其实际初始化所需的机械动力和 RCTAPI。要求 `RECRUITMENT PASS` 且无后续失败。专项实际调用装球、球保存加载和使用入口，检查五种来源的个体标识、变种、招式、特性、性格、昵称、携带物；也调用真实机器抽取池与抽取方法，验证完整物种标识、不可捕捉来源过滤。旧路径球、新机器球、官方喷火龙和缺失来源分别检查。不替代真人通讯机界面验收；发布构建不带 `-I`。

## 旧骑乘资源覆盖

`rebase-riding-pack.py` 的本地生成方式见 [骑乘修复说明](../RIDING_PACK_REPAIR.md)。生成资产 ZIP 不进入仓库。

`riding-smoke.init.gradle` 在 `build/riding-smoke-run` 启动独立客户端。准备大师对决与饰品库、织布库、跨平台接口、owo 等实际依赖；把两个基础外观包和新骑乘包放入 `resourcepacks`，报告命名为 `riding-report.json` 放在客户端目录。启用与实际实例相同的内置地区外观包，避免外部解析器引用未加载的姿态。资源加载顺序为模组基础资源、内置外观、外部外观、骑乘修复包。

```powershell
.\gradlew.bat -I validation/riding-smoke.init.gradle runClient '-Pneo_version=21.1.249' *> build/riding-client.log
```

要求 `RIDING_MODELS PASS` 和 `RIDING_DATA PASS`，无后续失败。诊断核对九百零七份资源确实来自修复包、模型烘焙座位、实际解析器的模型与姿态构造、全部姿态部件变换，以及九百四十四份数据通过原生骑乘反序列化器。小箭雀、火箭雀、穿山鼠与一家鼠两种家庭必须出现在实际检查中。没有逐只乘坐或执行完整动画帧，不能以此代替骑手位置与操作实机验收。

初次隔离启动遗漏 owo 前置；之后因测试资源顺序让模组基础资源覆盖外部包而被来源检查拒绝；再补齐实际实例已启用的内置地区姿态。最后发现地图图标包的一家鼠旧姿态引用，生成器已按新版模型分别更正。测试不得跳过缺少姿态，也不能用默认占位模型代替实际目标来通过。

## 转化粉与生存获取

`check-creature-acquisition.py` 对照当前 169 种 profile、十六条中文路线和真实物种追加文件，检查等级、道具标签、可获得的来源、普通野生禁捕物种的获取缺口、进化前身及首领 / 谜题羊任务隔离。要求 `ACQUISITION_DATA PASS`。

先用 `prepare-evolution-run.py --mods "你的模组目录"` 准备隔离服务器；复制来源模组及必需依赖，不修改原 JAR。完整模式安装暮色森林、天境、深入天境及传说遗迹，含来源实际依赖的大师对决、饰品库与 owo。`--base-only` 准备不含这些可选内容的独立目录。

```powershell
.\gradlew.bat -I validation/evolution-smoke.init.gradle runServer '-Pneo_version=21.1.249' *> build/evolution-server.log
.\gradlew.bat -I validation/evolution-smoke.init.gradle runServer '-Pneo_version=21.1.249' '-Pevolution_base_only=true' *> build/evolution-absent-server.log
```

完整模式要求 `EVOLUTION PASS`，无后续 `EVOLUTION FAIL`。实际调用原生 `PokemonEntity.mobInteract`、待确认进化及原生计时完成流程，检查全部十六条路线、最低等级、错误道具、缺失目标、消耗一次、不重复消耗、待确认预览模型身份、个体编号、等级、个体值、努力值、性格、昵称、携带物、原有招式、目标特性、进化前身、原生 NBT 保存读取及旧来源清理。另以官方皮卡丘雷之石进化雷丘作对照，并在放出状态等待完整原生进化时序，检查巫妖仆从身份与魂契分担。实际工作台输入也检查默认转换器配方。

缺少内容模组模式要求 `EVOLUTION_ABSENT PASS`。分别检查缺失目标及仍可用的原版僵尸马目标：空转化粉标签均不能把泥土等任意物品识别成粉，也不能登记待进化或扣物品。

首次完整模式发现方可梦 1.8.1 的超极巨化因子设置器通过名称接口读取完整命名空间，导致预览复制与 NBT 加载报错；公共标识读取兼容层修复后上述原生流程通过。待确认显示在发事件前快照外观，事件中重建原生显示对象才能更新模型身份；结果物种同时补齐 `preEvolution`，避免原生进化成就因缺前身而跳过。测试使用 FakePlayer，不代表真人队伍确认界面、JEI 进化页、客户端动画或全部来源自然世界的游玩验收。正常发布构建不带 `-I`，不包含诊断类。

2026-10-08 的最终日志分别在 18:17:32 出现 `EVOLUTION PASS`、18:19:27 出现 `EVOLUTION_ABSENT PASS`，无后续专项失败；普通 `clean build` 通过。两份中文属性表重新渲染并逐页检查，修正表头单独留在页末及装备表超出页宽的问题。
