# Cobbledex 图鉴兼容

目标版本：Cobbledex 2.28.8 / JEI 19.57.0.446 / Cobblemon 1.8.1 / Minecraft 1.21.1 NeoForge。

## 为什么原先没有条目

接入生物已有真正的 `Species` 注册、`Pokemon` 数据及 `PokemonEntity`，但内置物种 JSON 保留了早期的 `implemented=false` 标记。Cobblemon 的 `PokemonSpecies.implemented` 只返回标记为真的物种；Cobbledex 用这个列表建立基础信息和物种索引。

此外，Cobbledex 的 `PokemonItemCache.resolveSpecies` 和静态图标生成使用 `PokemonSpecies.getByName`，这条 Cobblemon 便利接口只查询 `cobblemon` 命名空间。接入物种在 `entitybattle` 命名空间，仍会被其可显示性筛选排除。

## 当前处理

- 在有效接入资料及物种加载完成后，用 Cobblemon 原有的 `implemented` 标记发布本项目的可用物种。来源实体已安装且有有效接入资料时启用；缺少来源模组的内置预设保持未启用。物种同步仍使用 Cobblemon 原来的数据包。
- 自动启用在加载服务器资料后进行；远程客户端保留服务器同步的标记，不用本地空资料覆盖它。退出本地世界时清除该世界资料，切换服务器或同步接入资料时清理名称索引。
- 可选兼容只补充本项目物种的名称 / 完整 ID 查询，不重注册假官方物种，不覆盖原本成功的官方查询。含糊的别名不猜测。
- 图标仍由 Cobbledex 自己通过 Cobblemon 公共模型绘制生成静态 PNG。本项目只补全物种查询，并提供既有运行时模型。
- 共同名称格式化入口使用该物种自己的翻译，支持现有中文名称。
- 名称别名索引在物种注册表更新时重建，查询不逐次扫描全部物种；每次解析仍检查当前物种的可用标记，避免数据包修改后返回已禁用条目。
- 无需修改第三方 JAR、用户整合包配置或逐个生物的 JEI 规则；未安装 Cobbledex 时这些可选 Mixin 不加载目标。

Cobbledex 的 REI / EMI 路径共享物种缓存及图标生成，但本次实际运行验收使用 JEI，不能把共享代码推断当成另外两个查看器的实机验收。

## 信息边界

基础种族值、属性、特性和招式来自真正的物种数据。原生 Minecraft / 来源模组的刷怪规则与原生死亡事件，和 Cobblemon 的生成池 / 掉落数据是不同系统；此修复不编造图鉴中的刷怪概率或原生掉落页。若需那些页，必须另外把来源规则转换成图鉴可读数据。

第三方作者使用自己的物种命名空间时，应在物种完成建模后设置合适的 `implemented` 标记；本项目自动管理的是内置 `entitybattle` 物种。作者有效的 Cobblemon 资源模型仍优先。

## 验证结果

2026-10-07 的隔离客户端输出 `COBBLEDEX PASS` 与 `DOLL_SMOKE PASS`，无后续 FAIL：167 个当前有效接入物种全部出现在真实 Cobbledex 物种索引及其 JEI 插件注册的条目列表中；官方喷火龙查询保留。牛、娜迦、九头蛇、巫妖通过原模组图标生成器生成静态 PNG，逐图检查可见像素及物种名称翻译。诊断调用在客户端 tick 中进行，避开测试界面绘制中的模型视图 / 裁切状态。

缺少暮色森林与天境的独立专服输出 `SPECIES_AVAILABILITY PASS`、`DOLL_SERVER PASS`：牛标记可用，两项缺失来源物种保持未启用；三种物种均经 Cobblemon 自己的 species 编解码验证该标记往返一致。未安装 Cobbledex 的服务端也正常完成真实玩偶转换与物品保存 / 加载。

最终客户端的 Cobbledex JEI 配方注册耗时约 5.6 秒；首次自动生成整份精灵图集仍由 Cobbledex 自己控制，有独立的生成耗时。这里不保证任何整合包都达到相同速度。诊断方法及日志位置见 [validation/README.md](validation/README.md)。

## JEI 模型物品向上偏移

2026-10-07 后续修复：Cobbledex 没有精灵图集时回退到 Cobblemon 的 `PokemonItemRenderer`。它使用物品展示变换，而本项目原生 Bone 使用资料模型的原点平移，两者少了一段 1.5 单位的坐标抵消，造成模型比格子向上偏移。

`NativePokemonItemMixin` 仅在本项目原生 Bone 的物品绘制入口补齐原点；GUI 居中读取 Cobblemon 本身的物品展示比例与位移，不逐生物修改 species、世界位置或资料页。官方 Bone 保持原流程。实际物品渲染测试覆盖牛、僵尸、史莱姆、娜迦、九头蛇、巫妖及本批五个 Boss，十一份完整几何均在物品范围内，输出 `ITEM_ICONS PASS`；截图为隔离目录 `jei-item-origin.png`。此测试验证真实物品回退路径，不代表第三方已有 PNG 缓存也被重新生成。
