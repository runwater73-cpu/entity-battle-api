# 原生生物注册到 Cobblemon 模型仓库

适用开发版本：Minecraft 1.21.1 / Cobblemon 1.8.1 / NeoForge。客户端实现随本 API 的 JAR 分发。第三方 JAR 和贴图不打包进本项目。

## 注册了什么

接入资料中的独立 species 自动获得三项实际仓库注册：

| 仓库 | 内容 |
| --- | --- |
| `VaryingModelRepository.texturedModels` | `entitybattle:native/<species 命名空间>/<species 路径>`，实现 Cobblemon 公共 `Bone` 接口的原生模型根 |
| `VaryingModelRepository.posers` | `entitybattle:native_poser/<species 命名空间>/<species 路径>`，`PokemonPosableModel` 姿态提供器 |
| `VaryingModelRepository.variations` | 该 species 自己的 resolver：模型、姿态、来源贴图供应器、可由第三方追加的 variations / layers |

例如牛使用 species `entitybattle:cow`，模型为 `entitybattle:native/entitybattle/cow`。客户端收到服务端接入资料后自动注册；公共仓库查询也会补注册尚未建立的项。作者提供的有效 Cobblemon 模型资源优先，本 API 不覆盖其 resolver。

世界实体继续由真正的 `PokemonEntity` 承担行为。独立 species 的世界渲染、资料模型和头像走 Cobblemon 原来的渲染入口；这些入口取得上述已注册模型和贴图。旧版“使用官方 species 作为模板，但强行显示另一个 Mob”的资料保留旧显示适配，不覆盖官方物种全体。

## 如何保留建模和动画

`NativePokemonBone` 在 Cobblemon 绘制过程中调用来源模组的原生渲染器，使用其现有网格、UV、基础动画与特征层；不复制来源模型源码或资源。原生 `ModelPart` 骨骼在能够取得时加入已注册模型的子骨骼集合。特殊多部件仍由 `EntityBattleNativeModels.Adapter` 更新并绘制。

贴图以 Cobblemon resolver 当前返回的结果为准，不再固定使用来源贴图。原生额外皮肤材质可通过统一纹理扩展替换；原来的材质状态、装备等独立特征保留。Cobblemon 的显示比例、放出 / 收回缩放、模型颜色及额外模型层继续参与绘制。公共 GUI 中仅为本 API 的原生根模型改为等比深度缩放，避免大型蛇身、头颈在 UI 深度范围外被裁掉；标准资源模型的缩放不变。

展示 Mob 不加入世界、不运行原生 AI、不产生原生攻击或第二次伤害。行走、基础头部 / 肢体动画来自来源渲染器；依赖原生 AI 内部状态的动作，需要客户端视觉适配。Cobblemon GUI 的累计动画时间和原生部件的单帧插值分开使用，避免头颈 / 冰盾持续向远处外推；GUI 与世界的高度补偿也分别处理。暮色 Boss 已有招式状态、多部件与原生视觉方法的适配继续使用，详见 [MODEL_DISPLAY.md](MODEL_DISPLAY.md)。

资料界面使用 poser 的 `profileSummaryScale` / `profileSummaryTranslation` 单独定位，普通资料入口继续使用 `profileScale` / `profileTranslation`。原生模型的中心和比例根据实际输出的顶点范围计算，包含碰撞箱之外的头冠、装备和多部件。范围在首次界面绘制时测量，注册时不触发原生绘制；每个展示副本只测量一次，并随模型缓存清理。同一 species 的幼年、大型或其他外观副本，按其自己的范围归一化 GUI 比例，世界比例不受影响。这样不需要为各个 PC、资料页面再次写独立绘制补丁。该范围是展示副本当前姿态的几何范围，异常的大幅攻击动作仍需视觉适配。

同一个模型仓库有两个公共 GUI 绘制入口：资料、PC 等完整模型使用 `PokemonGuiUtils.drawProfilePokemon` 的 profile 参数；队伍 HUD、战斗血条、对话头像使用 `GuiUtils.drawPosablePortrait` 的 portrait 参数。二者的锚点和裁切范围不同。居中的原生几何使用 `portraitScale=1.2`、`portraitTranslation=(0,0.95,0)`；旧的负向头像位移不适用于归一化后的模型，会让整个头像落在队伍栏裁切框上方。这个参数放在已注册 poser 中，不恢复单独的队伍栏 / 战斗界面绘制覆盖。

世界中的展示 Mob 维持水平头部，避免把 PokemonEntity 的世界注视俯仰重复当成原生 Mob 动画。GUI 保留 Cobblemon 的鼠标跟随，但同步展示 Mob 的当前和上一帧俯仰；GUI 已经平滑过的角度不会再从旧的零角度每帧插值，避免反复点头。招式动作、行走和原生特征层不因此停用。

## TeamRocket 融合换色

TeamRocket 1.1.6 的 `FusionVariationInjector` 会为仓库 resolver 注入融合 variation。现在本 API 的独立 species 也存在于该仓库，它会获得同样的注入。基础贴图使用公共仓库返回的融合贴图，调色和动态贴图缓存由 TeamRocket 自己生成。

原生模型可能另有羊毛、蛇身、头颈等皮肤贴图。可选适配 `TeamRocketModelTextures` 只调用 TeamRocket 的公开 payload 与 texture cache 接口，为这些额外皮肤提供相同调色；不调色盔甲、手持物、环境或图集，也没有复制其换色算法。融合供体为接入生物时，公共仓库可以返回供体的基础来源贴图；查询不会误用主个体的来源外观。

未安装 TeamRocket 时，此扩展不加载。第三方接口变化时记录一次警告并保留额外原生皮肤。本次处理的是融合显示，融合属性、配方、遗传与存储继续由 TeamRocket 管理。

实际 1.1.6 换色流程、史莱姆多种配色的解释，以及原生附加皮肤与专用图层模式的差异，见 [TEAMROCKET_FUSION_COLORS.md](TEAMROCKET_FUSION_COLORS.md)。

## 接入作者的工作

普通 `Mob` 使用独立 species 和 profile 后，会自动注册来源模型，无需为 PC、战斗和其他原生界面逐个配置。注册原生外观快照以保留颜色、幼年、变种、装备等个体差异，参见 [INTEGRATION.md](INTEGRATION.md)。

特殊动作或多部件使用已有 `EntityBattleNativeModels.register(source, adapter)`。适配器更新纯视觉状态，不能调用整个 `Mob.tick()` 或添加攻击实体。依赖世界区块绘制状态的部件，可在 `Adapter.renderPart` 复用来源模型的预览绘制接口；调用时矩阵已移到部件位置，返回 `true` 表示完成，`false` 使用原生部件渲染。

其他纹理扩展可以在客户端初始化时注册：

```java
EntityBattleModelTextures.register(
    ResourceLocation.fromNamespaceAndPath("example", "skin_effect"),
    context -> {
        // context: species、PosableState、展示 Mob、当前皮肤贴图。
        // 返回替换贴图的资源 ID；null 表示保留当前结果。
        return null;
    }
);
```

不要在每帧重复生成图片；自行缓存动态贴图并在资源重载时释放。注册类只能从客户端代码引用。

## 注册的边界

这是 Cobblemon 支持的自定义 `Bone` / poser 注册。它保留了原生渲染代码，**没有把任意来源自动导出成静态 Bedrock `.geo.json` 和 `.animation.json`**。

| 第三方读取方式 | 结果 / 需要的工作 |
| --- | --- |
| 查询 `getPoser`、`getTexture`、variations / layers，使用公共绘制入口 | 可以取得实际已注册模型及贴图；具体组合仍需验收 |
| 扫描资源包中的静态 Bedrock 文件 | 运行时注册不会产生这些文件，需要作者提供标准资源 |
| 将根骨骼强转为 `ModelPart`，或强制某组 Cobblemon 命名骨骼 | 自定义 `Bone` 不保证符合其假设，需要适配 |
| 修改某个骨骼来驱动新动画 | 来源渲染器的 `setupAnim` 可能重新设定它；需要纯视觉状态适配或标准模型资源 |
| 使用特殊多纹理 shader、原生专属渲染引擎 | 基础原生绘制保留，换色 / 额外层需专项适配 |
| 查询独立二维像素 sprite | 不自动绘制像素美术；无 sprite 时原生界面可绘制模型 |

作者也可以直接提供 Cobblemon 标准 `bedrock/pokemon/models`、`posers`、`resolvers`、动画与贴图资源，保留全部 Pokemon 数据和战斗功能。届时其资源 resolver 优先，原生渲染副本不再承担该 species 的基础绘制；程序化攻击动画需要在标准 poser 中实现，不能承诺任意源码动画都能无损自动导出。

## 生命周期和验证

资源重载时移除本 API 拥有的模型 / poser / resolver 和材质缓存，然后根据有效资料重建；保留作者新加载的资源。配置同步会移除已失效或更换来源的注册。界面状态和原生展示实体使用弱缓存。材质替换缓存上限 512，额外融合贴图复用第三方缓存。

来源渲染器可能委托另一个模型而报告错误贴图，现在复用已有几何测量观察实际身体材质，供模型仓库及融合查询使用。检查结果见 [MODEL_SOURCE_AUDIT.md](MODEL_SOURCE_AUDIT.md)。玩偶使用同一运行时仓库，补齐物种身份与绘制入口，详见 [DOLL_MODEL_COMPATIBILITY.md](DOLL_MODEL_COMPATIBILITY.md)；此兼容不需要静态几何导出。

客户端专项源码：[validation/model-src](validation/model-src)。验收包含实际公共资料 / 头像绘制、世界渲染、原生部件与招式状态、来源外观 aspects、双向融合贴图及像素变化、注册重建；正常发布 JAR 不包含诊断类。具体运行方法见 [validation/README.md](validation/README.md)。这些测试不代表用户整合包全部资源包、光影、地图、融合配方和第三方骨骼扩展已完成游戏内验收。

### 2026-10-07 实际结果

隔离客户端最终输出 `MODEL_SMOKE PASS`，随后正常退出。使用 Cobblemon 1.8.1、NeoForge 21.1.249、暮色森林 4.8.3345、TeamRocket 1.1.6 和测试所需的其他依赖。

- 138 个有效独立 species 通过公共仓库取得实际 `NativePokemonBone`，逐个执行 Cobblemon 资料绘制；核心样本另外执行公共头像和世界模型绘制。
- 八个暮色 Boss 的已有招式视觉状态继续工作；娜迦十二节、九头蛇三个头、女王冰盾位置检查通过。最终截图另行检查，修复了 GUI 深度裁剪、累计时间外推和冰盾区块材质问题。
- 牛与雪怪首领的世界几何输出高度，与原生渲染器输出按 Pokemon 显示比例及招式视觉位移换算后吻合；没有额外的 1.5 格偏移。
- 红色幼年羊的 aspects 显示恢复通过。
- 牛作融合主模型、喷火龙作主模型与牛作供体，均取得 TeamRocket 动态融合贴图。测试参数为完全重着色；牛贴图 1632 个像素、喷火龙贴图 11880 个像素发生变化。该检查验证公共调色入口及实际像素，不代替整合包内完整融合配方操作。
- 模型注册清理 / 重建后，再次取得融合贴图并验证像素变化。
- 对照更新前已安装的 JAR，176 个 `data` 资源内容均未改变，包括巫妖及仆从物种与两项专属特性脚本。

### 2026-10-07 首次贴图查询与多部件尺寸

实际界面先查询贴图，再应用姿态动画。此前材质观察同时缓存模型范围，在全新展示副本上可能先记录尚未激活的娜迦节段；后续完整模型沿用头部尺寸，导致资料和队伍格过大。九头蛇未初始化时的预览头颈也会让范围和正式绘制不一致。贴图供应器现在先准备该副本的展示姿态与部件，再进行材质 / 几何测量；使用通用模型路径，不给界面或物种单独叠加缩放。

诊断增加首次贴图查询后再绘制的情况，修复前娜迦的 66×66 资料框实际范围为 X=9.88..148.97、Y=12.70..105.19；修复后 X=20.13..45.66、Y=25.96..42.93。资料、PC、头像均继续使用完整个体几何归一化。GUI 适配显示区域，不按世界大小让大型 Boss 挤出队伍格。

Cobbledex 条目及物种可用状态见 [COBBLEDEX_COMPATIBILITY.md](COBBLEDEX_COMPATIBILITY.md)。

最终 `build/model-cold-summary-final.log` 同时输出 `MODEL_TEXTURE_FIRST PASS`、`MODEL_SMOKE PASS`、`MODEL_LAYOUT PASS`、`MODEL_PORTRAIT PASS` 与 `MODEL_ACTUAL_SUMMARY PASS`，无后续 FAIL。167 个有效来源继续完成公共绘制；新建状态的完整模型范围检查通过。使用 `Summary.Companion.open` 打开原版资料界面，真实六个队伍格截图 `build/model-smoke-run/actual-boss-summary.png` 已目视核对，娜迦、九头蛇及另外四个 Boss 均处于各自显示范围内。诊断计时按客户端 tick 累加一次，避免把每个样本计成一帧造成提前超时。

### 资料位置与头部修正验证记录

用户实机发现资料 / PC 模型上方被裁掉，以及巫妖持续点头。修正为共享模型参数加实际个体几何归一化；世界脚底坐标和显示比例保留。几何测量从注册阶段移到首次界面绘制，避免世界相机未准备好时缓存碰撞箱备用范围；测量只包含原生模型和部件，不包含 dispatcher 的阴影、火焰或调试边框。

最终隔离日志 `build/model-layout-verified-client.log` 同时输出 `MODEL_SMOKE PASS` 和 `MODEL_LAYOUT PASS`，之后正常退出，无原生绘制失败。

- 十二个核心样本及大小为 8 的史莱姆，全部实际顶点位于原版资料大预览和 PC 储存格的裁切范围内；幼年羊与大型史莱姆验证同一物种不同尺寸的归一化。
- 资料和 PC 大预览共用的官方 `ModelWidget` 实际绘制截图为 `build/model-smoke-run/summary-preview.png`，另作目视检查；PC 格检查使用其准确的尺寸、变换、旋转与裁切范围，没有在诊断中打开整套 PC 操作流程。
- GUI 鼠标跟随俯仰在三个帧插值下保持输入角度，巫妖世界头部保持水平。行走、招式视觉状态、八个 Boss 部件以及双向融合贴图检查继续通过。
- 这些专项不代表整合包内所有第三方界面、资源包和光影组合已实机验收；世界高度对照仍覆盖牛和雪怪首领。
