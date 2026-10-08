# 接入生物模型与贴图替换检查

2026-10-07，检查本项目内置的 167 个有效来源：原版 79、暮色森林 58、天境 21、深入天境 6、奇异饰品 1、传说纪念碑 1、车万女仆野外妖精 1。隔离客户端安装对应来源模组、实际 TeamRocket 与两个玩偶模组，并启用整合包中的 EMF / ETF。另只读检查整合包的渲染挂钩和启用资源包。

## 实际结果

167 个来源均完成渲染器、报告贴图与实际材质检查，其中 163 个默认预览输出了真实网格。确认 2 个来源的报告贴图与实际身体材质不一致，均已由通用材质测量修复。

| 生物 | 原因 | 本整合包关闭替换时的实际贴图 |
| --- | --- | --- |
| 史莱姆 | 女仆委托 renderer 报告灵梦油库里贴图，但绘制原版史莱姆 | `minecraft:textures/entity/slime/slime.png` |
| 岩浆怪 | 女仆委托 renderer 报告魔理沙油库里贴图，但绘制原版岩浆怪 | `minecraft:textures/entity/slime/magmacube.png` |

其余有网格的来源没有发现同类错误。跨命名空间贴图也不一定是替换：暮色死亡之书使用原版附魔台书贴图、巫妖仆从 / 忠诚僵尸使用原版僵尸贴图、敌对狼使用原版狼贴图，巨人使用玩家皮肤，这是来源渲染器自己的设计。

以下 4 个来源在新建、未生成初始化的默认预览状态下没有输出普通网格，不能把本次检查当成它们的完整视觉验收：

- `aether:whirlwind`、`aether:evil_whirlwind`：旋风主要以粒子表示。
- `twilightforest:rising_zombie`：出土僵尸的显示受出土进度 / 年龄影响。
- `twilightforest:snow_guardian`：冰雪守卫的显示依赖装备与生成初始化。

这些情况与第三方错误报告贴图是不同问题。相关实体有非默认状态、装备或特殊粒子时，仍需实际个体确认。

## 通用处理与性能

来源 `Mob` 仍由来源模组 / 第三方当前有效的 renderer 绘制。本 API 在既有几何测量中同时观察 RenderType 的贴图与顶点：报告贴图真正参与身体绘制时沿用；否则选择实际身体材质。装备、物品、方块、图集和杂项材质不作为供体身体贴图。

测量结果按展示个体弱引用缓存，不逐帧分析 PNG、不扫描整个整合包，资源重载或断开世界时清理。融合算法、动态贴图缓存及材质透明度仍复用原接口。测量无法取得材质的自定义多纹理 shader、只有粒子的 renderer 或无法预览的特殊 renderer 会保留原报告值；作者可使用已有客户端材质扩展接口进一步处理。

## 资源包与验证范围

用户当前启用的外部资源包 `Minecraft-Mod-Language-Modpack-Converted-1.21.1.zip` 没有覆盖原版 / 暮色 / 天境 / 深入天境的来源实体贴图，也没有 CEM / 随机实体纹理资源。EMF / ETF 安装本身不代表这些生物已经换了模型。换整合包、增加资源包或更改第三方模型配置后，需要按新的有效 renderer 再确认。

完整逐来源记录由 `validation/doll-smoke.init.gradle` 输出到隔离目录 `native-texture-audit.json`，含 source、species、renderer、报告贴图、实际贴图、材质列表、顶点数量及资源包来源。该报告验证了本次依赖组合，不代替所有光影、优化模组与用户真实游戏存档的验收。

本次完整记录已保存在 [docs/model-source-audit-20261007.json](docs/model-source-audit-20261007.json)。

玩偶流程见 [DOLL_MODEL_COMPATIBILITY.md](DOLL_MODEL_COMPATIBILITY.md)，融合颜色原因见 [TEAMROCKET_FUSION_COLORS.md](TEAMROCKET_FUSION_COLORS.md)。
