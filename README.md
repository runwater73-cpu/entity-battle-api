# Entity Battle API

让已接入的 Minecraft 原生或模组生物使用 Cobblemon 的物种数据、野生对战与捕捉流程，同时复用来源生物的模型和基础渲染。

## 版本与安装

- Minecraft 1.21.1、NeoForge 21.1.244 或更新的 21.1.x
- Cobblemon 1.8.1、Kotlin for Forge 5.3 或更新版本
- 客户端和服务器都需要安装本模组；单人游戏只需装在整合包的 `mods` 目录
- 暮色森林、天境、深入天境、车万女仆等内容模组为可选依赖。仅在对应模组及生物 ID 存在时，其预置配置才会生效

预置 167 种独立物种和生物绑定，包括 79 种原版生物、87 种其他模组生物及车万女仆的野外妖精。缺少对应内容模组时，相关绑定会被跳过并记录加载错误；不要求安装所有内容模组。

## 游戏内使用

默认的 `native_mob` 模式保留生物在大世界中的原生 AI、攻击、交互和通常的死亡流程。玩家可以直接按 Minecraft 方式战斗。使用创造标签页“生物宝可梦化”中的 `entitybattle:converter` 右击已接入生物，可将该个体**永久转换**为野生 `PokemonEntity`，随后按 Cobblemon 的方式对战或捕捉。转换器在生存模式也可使用，但目前没有合成配方。

对原生生物按 `R` 临时开战默认关闭。单人游戏可在“模组列表 → Entity Battle API → 配置”中开启；也可修改 `config/entitybattle-common.toml` 中的 `enableRChallenge`，然后重启游戏。多人服务器由服主修改服务端配置并重启服务器。关闭此项不影响转换器，也不改变 Cobblemon 对普通野生宝可梦的按键行为。

永久转换会移除原生 Mob。Boss 原有的阶段 AI、原生死亡事件和依赖它们的任务回调不会自动迁移；`nativeDrops`、`nativeExperience` 仅尝试补充战利品表掉落和 Minecraft 经验。请在正式存档使用前验证重要 Boss 的任务进度。

## 接入开发

- [接入指南](INTEGRATION.md)：profile、species、两种世界模式、Java 扩展点与验证步骤
- [预置生物配置](PACK_PROFILES.md)：文件位置、字段修改和 Boss 捕捉策略
- [架构说明](ARCHITECTURE.md)：实体转换、会话恢复和模块职责
- [直接模式示例](examples/direct_pokemon_entity/cow.json)：让牛从生成起成为 `PokemonEntity`，默认不加载

预置 profile 位于 `src/main/resources/data/entitybattle/battle_profiles/`；每种物种的属性、种族值、特性和招式位于 `src/main/resources/data/entitybattle/species/`。这些 JSON 是生效数据，可以通过数据包覆盖。来源模组的 JAR、模型和纹理不会随本仓库分发。

## 构建与验证

需要 JDK 21。运行 `./gradlew build`，Windows 使用 `gradlew.bat build`。Gradle 默认从 Modrinth 获取 Cobblemon 1.8.1；也可通过 `-Pcobblemon_local_jar=<路径>` 指向本地 JAR。输出位于 `build/libs/`。

已验证 1.8.1 编译通过，以及整合包中 167 个 profile 加载。不同来源生物的外观、捕捉、战败结算、Boss 任务回调及换宠等游戏内流程仍需逐项回归测试。本仓库当前版本号为 `0.1.0-dev`。

## 许可

本仓库自有代码与配置以 [MIT License](LICENSE) 发布。Minecraft、Cobblemon 和其他模组属于各自作者；使用本模组仍需遵守它们各自的许可。
