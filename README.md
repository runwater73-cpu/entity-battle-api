# Entity Battle API

让已接入的 Minecraft 原生或模组生物使用 Cobblemon 的物种数据、野生对战与捕捉流程，同时复用来源生物的模型和基础渲染。

作者：**RunWater1**。

## 版本与安装

- Minecraft 1.21.1、NeoForge 21.1.244 或更新的 21.1.x
- Cobblemon 1.8.1、Kotlin for Forge 5.3 或更新版本
- 客户端和服务器都需要安装本模组；单人游戏只需装在整合包的 `mods` 目录
- 暮色森林、天境、深入天境、车万女仆等内容模组为可选依赖。仅在对应模组及生物 ID 存在时，其预置配置才会生效

预置 169 种独立物种和生物绑定，包括 79 种原版生物、89 种其他模组生物及车万女仆的野外妖精。缺少对应内容模组时，相关绑定会被跳过，对应物种保持未启用；不要求安装所有内容模组。

## 游戏内使用

默认的 `native_mob` 模式保留生物在大世界中的原生 AI、攻击、交互和通常的死亡流程。玩家可以直接按 Minecraft 方式战斗。使用创造标签页“生物宝可梦化”中的 `entitybattle:converter` 右击已接入生物，可将该个体**永久转换**为野生 `PokemonEntity`，随后按 Cobblemon 的方式对战或捕捉。转换器可用两份紫水晶碎片、两份红石和一份铁锭合成。普通僵尸现已可捕捉，28 级后可用暮色转化粉进化为巫妖仆从；全部补充路线见 [生存获取与进化](CREATURE_EVOLUTIONS.md)。

对原生生物按 `R` 临时开战默认关闭。单人游戏可在“模组列表 → Entity Battle API → 配置”中开启；也可修改 `config/entitybattle-common.toml` 中的 `enableRChallenge`，然后重启游戏。多人服务器由服主修改服务端配置并重启服务器。关闭此项不影响转换器，也不改变 Cobblemon 对普通野生宝可梦的按键行为。

永久转换会移除原生 Mob。原生阶段 AI 不会迁入回合战斗；已适配的正式 Boss 保存来源状态，在全场胜利后复用原生死亡回调，保留掉落、经验与对应地牢后续。其他作者接入的特殊生物需要来源适配器才能保留特殊死亡 / 任务事件，仅配置 `nativeDrops`、`nativeExperience` 不保证这些回调。

正式 Boss 默认自动使用真正的 `PokemonEntity`，不可捕捉；可在 `bossWorldMode` 中改为 `NATIVE_MOB`。有原作开战条件的 Boss 先保留原生交互，完成对话、交付或唤醒后自动转换，不需要转换器；无前置条件的 Boss 生成后即转换。两种模式均不能跳过剧情。深入天境的狂瞳龙卷已接入，设定与边界见 [说明](DEEP_AETHER_BOSS.md)。玩家完整赢得 Cobblemon 对战后，`BATTLE_VICTORY` 会为对应 Boss 发放一次固定 1 级奖励；安装 Team Rocket 时奖励使用招募球封装。

巫妖群战试点使用野生对战入口：安装 Asymmetric Battles 与 Horde Encounters 后，profile 中的 `bossBattle` 会自动让巫妖与两名仆从开场群战。前者提供一对多规则和目标界面，后者接管野生对战入口并创建群战；玩家仍最多携带六只、每次上一只。缺少任一模组或 Boss 没有群战定义时走普通野生单打。巫妖招式与来源动画的对应、阶段边界及实机检查项见 [Boss 战斗设定表](BOSS_BATTLES.md)。

## 接入开发

- [接入指南](INTEGRATION.md)：profile、species、两种世界模式、Java 扩展点与验证步骤
- [预置生物配置](PACK_PROFILES.md)：文件位置、字段修改和 Boss 捕捉策略
- [生存获取与转化粉进化](CREATURE_EVOLUTIONS.md)：十六条路线、最低等级、原生确认、个体数据与修改方法
- [架构说明](ARCHITECTURE.md)：实体转换、会话恢复和模块职责
- [模型注册与融合显示](MODEL_REPOSITORY.md)：Cobblemon 模型仓库、原生动画保留、自动接入及格式边界
- [玩偶显示兼容](DOLL_MODEL_COMPATIBILITY.md)：运行时模型、放置 / 投掷显示、命名空间与融合外观保存
- [火箭队招募兼容](TEAMROCKET_RECRUITMENT.md)：招募机与招募球的物种标识、完整个体、变种外观与获取规则
- [旧骑乘包修复](RIDING_PACK_REPAIR.md)：本地座位迁移、模型与动画取舍、独立资源包安装和验证边界
- [Cobbledex 图鉴兼容](COBBLEDEX_COMPATIBILITY.md)：物种可用标记、JEI 收录、中文名称及图标生成
- [来源模型检查](MODEL_SOURCE_AUDIT.md)：167 个生物的贴图检查、女仆委托渲染器及验证边界
- [Boss 战斗设定表](BOSS_BATTLES.md)：15 个 Boss 参数、群战与招式表现
- [中文首领设定](docs/boss-design/README.md)：十五种首领与机制清单；修改设定文档不会自动改变游戏
- [原版与天境实施记录](OTHER_BOSS_IMPLEMENTATION.md)：五个新 Boss 的数值、剧情资格、原生死亡回调、特性与验证边界
- [多人战斗入口核查](MULTIPLAYER_BATTLES.md)：Asymmetric Battles / Horde Encounters 的实际接口与真人协作限制
- [Boss 流程等级](BOSS_PROGRESSION.md)、[向日葵 Boss](SUNFLOWER_BOSS.md)、[狂瞳龙卷](DEEP_AETHER_BOSS.md)：连续等级、剧情与原生结算
- [谜题羊](QUEST_RAM.md)：原生行为、十六色任务与领奖后的自愿捕捉
- [完整中文生物属性表](EntityBattle原版与整合包生物属性表.docx)、[模组生物中文属性表](EntityBattle整合包生物属性表.docx)：169 种来源配置及当前装备
- [更新记录](CHANGELOG.md)、[发布准备](docs/发布准备.md)：本批改动、公开测试范围与网站发布资料
- [直接模式示例](examples/direct_pokemon_entity/cow.json)：让牛从生成起成为 `PokemonEntity`，默认不加载

预置 profile 位于 `src/main/resources/data/entitybattle/battle_profiles/`；每种物种的属性、种族值、特性和招式位于 `src/main/resources/data/entitybattle/species/`。这些 JSON 是生效数据，可以通过数据包覆盖。来源模组的 JAR、模型和纹理不会随本仓库分发。

## 构建与验证

需要 JDK 21。运行 `./gradlew build`，Windows 使用 `gradlew.bat build`。Gradle 默认从 Modrinth 获取 Cobblemon 1.8.1；也可通过 `-Pcobblemon_local_jar=<路径>` 指向本地 JAR。输出位于 `build/libs/`。

当前版本为 `0.2.0-beta.3`，公开测试目标为 Minecraft 1.21.1 / NeoForge / Cobblemon 1.8.1。全部可选来源安装时预置 169 个 profile。服务端剧情 / 战斗结算、客户端来源姿态 / 实际物品模型和缺少可选来源启动均有专项验证，详见 [验证记录](BOSS_VERIFICATION.md)。本版本补齐原生来源模型的步行动画时钟和转换后模型来源回退，避免跟随移动时平移以及刚生成宝可梦尚未收到追踪包时出现空模型。自然地牢完整游玩、随机投球捕捉、全部第三方显示界面及战斗平衡仍需游戏测试；暂不宣称稳定正式版或真人多人协作已完成。

## 许可

本仓库自有代码与配置以 [MIT License](LICENSE) 发布。Minecraft、Cobblemon 和其他模组属于各自作者；使用本模组仍需遵守它们各自的许可。

## 终焉城堡训练家

暮色领主自动生成已按取消要求停用，新整合包由其他模组负责训练家。原六只固定 50 级队伍与实现仅保留为 [历史示例](TWILIGHT_LORD.md)。
