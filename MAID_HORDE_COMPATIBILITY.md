# TeamRocket 女仆群战兼容

针对 TeamRocket 1.1.6、Cobblemon 1.8.1、Asymmetric Battles 1.1.0、Horde Encounters 1.0.0 的动态队友接入。

## 已定位的问题

1. TeamRocket 在 Java 战斗对象和客户端增加女仆 `p3`，发送的注册脚本没有 BattleStream 要求的 `>` 指令前缀。脚本被忽略，女仆没有进入 Showdown。玩家或敌方选择女仆作为目标后，回合无法正常处理。
2. 补充协议前缀后，女仆显示名称进入 `ShowdownSide.name`，但 Cobblemon 这个字段要求 UUID。请求解析报 `Failed parsing ... as UUID; at path $.side.name`。
3. 动态注册产生的首次行动请求可能晚于原有首回合通知。Cobblemon 清理已提交回合的请求时会丢掉没有作答的女仆请求。
4. Horde 的 Java 战斗对象预留六个敌方槽，但 Showdown 只建立实际存在的敌人数。`MoveTarget.any` 把空槽也列入目标，原生 `RandomBattleAI` 和 `StrongBattleAI` 都可能选择它。女仆实际使用 `StrongBattleAI`；实机的龙之波动发送了 `+4`，只有三个敌人的 Showdown 找不到该槽对应的另一方，产生异常后停止回合输出。Java 侧三方已交完动作、请求为空，因此画面停在第 1 回合。

## 接入方式

- `MaidHordeCompatibility` 订阅 Cobblemon 公开的战斗开始后事件，在 TeamRocket 已选择并加入女仆 actor 之后执行。
- 用 `BattleRegistry.packTeam` 和 `ShowdownService.send` 注册同一只女仆队伍；协议身份使用女仆 actor UUID，游戏中的中文名字仍由原 actor 提供。
- 首次注册期间等待女仆请求就绪，通过 Cobblemon 原生 `turn()` 通知现有女仆 AI。衔接只做一次，后续回合由原战斗系统执行。
- 补充该场战斗的友方关系、群体目标和胜负计数。规则适配代码属于本 API，女仆的选招 AI 仍由 TeamRocket 和 Cobblemon 执行。
- `HordeAITargets` 在上述两种原生 AI 返回行动后检查 Horde 目标。合法且存活的目标直接保留；空槽、已退场或已倒下的目标从该招式原生合法集合中重新选择，优先敌方。保留原选中的招式及特殊机制；强制换宠、锁定招式和无需选择目标的群攻走原生路径。
- 空槽校正依据战斗格式和原生目标规则，不依赖巫妖、女仆类或特定实体配置，可复用于其他生物、Boss 和 Horde 队伍。治愈波动仍由独立的队友治疗策略处理，不会因此改为攻击敌人。
- 没有编译依赖 TeamRocket 或女仆类。当前只对明确验证的 TeamRocket 1.1.6 启用，其他版本要先验证。
- 第三方源码和 JAR 保持不变，没有针对 TeamRocket 类的方法拦截、Mixin 或反射修改。兼容层只读取已有 actor 并调用战斗接口。

## 复现

`validation/maid-smoke.init.gradle` 只在隔离验证时加载 `validation/maid-src`，正常构建不包含测试类。测试调用安装 JAR 内的实际女仆加入方法；记录行动、回合和原生请求，测试结束保存战斗日志。

原始失败记录：`build/maid-smoke-before-fix.log`；只补前缀的失败记录：`build/maid-smoke-prefix-only.log`。不能用 Gradle 正常退出判断测试成功，必须检查 `MAID_SMOKE PASS`。

## 2026-10-06 验证结果

- 隔离服务端加载安装包中的实际 JAR：TeamRocket 1.1.6、Journeysouvenirs 0.2.53、车万女仆 1.5.3、RCTAPI 0.16.1、Cobblemon 1.8.1、Asymmetric Battles 1.1.0、Horde Encounters 1.0.0；NeoForge 21.1.249。
- 使用真实女仆 actor 和原模组加入方法；兼容层通过公开战斗接口注册，发布代码不包含反射或对 TeamRocket 的 Mixin。
- 玩家第 1 回合通过真实 `BattleSelectActionsHandler` 提交招式，Journeysouvenirs 将其改为不服从 `pass`，回合正常完成。
- 第 2 回合换宠，第 3 回合确认上场 UUID 已变更；第 3 回合正常招式执行并推进到第 4 回合。女仆每回合通过原有 AI 提交行动，敌方能攻击女仆。
- 结果标记：`MAID_SMOKE PASS`，日志 `build/maid-smoke-final-passed.log`。动画队列实际执行，测试没有跳过或清空队列。
- 使用本轮 33-37 级巫妖与同级仆从、暮光支配、魂契分担及剩饭重新验证，结果同样为 `MAID_SMOKE PASS`，日志 `build/lich-ability-maid.log`；玩家换宠和原生女仆 AI 连续推进到第 4 回合。测试按每回合是否继续推进计时，避免新增扣血/回血消息的正常播放时间超过固定总时限而误报卡住。
- TeamRocket 原 JAR 与开始测试时复制的隔离副本 SHA-256 均为 `3D395B9923A3F752FE25A7575E4AD032C0CB14DFBE1AAB040E2BBA199DE2A858`。

测试人工调用第三方已有加入方法，女仆任务、徽章、队伍绑定 UI 的条件筛选不属于此测试。客户端完整整合包画面、友方全灭 / 女仆换宠 / 各种胜负组合仍需游戏验证；测试通过不代表所有战斗组合已经验收。

## 空位目标专项

- `validation/horde-empty-targets.cjs` 在未修改的 Asymmetric Showdown 上创建真实 Horde 2v3。分别给女仆空气斩、龙之波动指定 `+4`，均复现 `TypeError` 和第 1 回合停滞；对照的 `+1`、`+2`、`+3` 六种组合均到达第 2 回合。证据：`build/horde-empty-targets.log`。
- 女仆服务端诊断改为只携带空气斩和龙之波动，实际女仆每次选招都使用 `any` 目标。还显式构造两招 × 三个空位，检查校正结果；重复原生 Random / Strong AI 选择，检查目标必须为在场且存活的敌人、已选招式不变、有效目标不改动。
- 仅校正 Random AI 的中间验证仍停在第 1 回合，日志 `build/horde-any-target-maid.log` 确认女仆 Strong AI 发出 `+4`，据此把同一兼容扩展到 Strong AI。该中间包未安装到整合包。
- 两种 AI 均校正后的完整隔离服务端验证通过：`MAID_SMOKE PASS`，日志 `build/horde-any-target-maid-fixed.log`。六个显式空位修复、256 次 Random 与 64 次 Strong 选目标均通过；女仆实际使用两种 `any` 招式，首回合不服从、第二回合换宠、第三回合出招后推进到第 4 回合。未跳过动画或清空显示队列。

当前已明确验证的动态女仆握手版本仍为 TeamRocket 1.1.6。空位校正对 Horde 通用，但不能据此宣称任意第三方自制 AI、后续版本、所有持有物或所有胜负组合已经兼容。
