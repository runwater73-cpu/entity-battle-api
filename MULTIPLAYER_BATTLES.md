# Asymmetric Battles 与 Horde Encounters：真人协作入口核查

核查版本：Minecraft 1.21.1、Cobblemon 1.8.1、NeoForge，安装的 Asymmetric Battles 1.1.0 与 Horde Encounters 1.0.0。2026-10-07。

## 当前怎样与其他玩家一起打 Boss？

**仅安装这两个版本，当前没有可开启的“加入 Boss 战”按钮、邀请快捷键或组队命令。** 我们当前的 Boss 阵容走 `hordeBattle`，第二名玩家按 R 不会加入同一战斗。不要分别对同一只已在战斗的 Boss 开战。

| 功能 | 实际作用 | 是否等于真人协作 |
| --- | --- | --- |
| Horde Encounters | 从野生宝可梦的 herd 名单构建一个 HordeBattleActor，普通野生战入口改走 Horde 格式 | 否，多个单位是敌方野生宝可梦 |
| Asymmetric Battles 的 Horde | 一名玩家对最多六个敌方在场位置，放宽人数与选招限制 | 否，不自动创建队友玩家 |
| Quadruple / Pentuple / Sextuple | 四打、五打、六打等同时在场宝可梦规则 | 单位数不是人类玩家人数 |
| Asymmetric Battles 的 Multi | 两个阵营各预留两个 actor，支持占位 actor 与开始后替换占位 | 是开发接口；尚无玩家邀请流程 |
| TeamRocket 女仆协作 | 女仆作为额外 actor 进入战斗，已有项目兼容 | 女仆控制入口不等于真人玩家入口 |

## 原理

Horde Encounters 在 Cobblemon 的野外 `pve` 建战入口检查目标 herd 信息，用 `BattleParticipant.horde` 打包名单，再调用 Asymmetric Battles 的 `hordeBattle`。本 API 先提供明确的 Boss / 随从名单，然后复用这条链路；没有复制群战引擎。

Asymmetric Battles 修改 Cobblemon / Showdown 的战斗格式、活动槽、目标选择和请求处理。`hordeBattle` 创建一名玩家 actor 与一个野生群体 actor；`multiBattle` 创建 p1+p3 对 p2+p4，p3/p4 可以是 DummyBattleActor 占位。`setMultiBattleActor(actor,battle,side)` 可填入 Multi 预留位置，side=3 是 p1 的队友，side=4 是 p2 的队友。

安装 JAR 的字节码明确要求 `battleType.name == "multi"`，对当前 `horde` 直接调用热加入接口会返回。普通 Multi 每名 actor 的槽位规则也不能直接充当“两名真人对六只 Boss 队伍”的完整实现。

## 配置里的 PvP 开关

安装的 NeoForge 版本生成 `config/asymmetricbattles-common.toml`。其中 `enable_pvp_challenges = true` 控制玩家对战邀请页能否选择四打、五打、六打；它不启用合作挑战 Boss，也不关闭这些格式的开发接口。这里没有替玩家修改整合包配置。

## 后续真正接入真人合作所需的工作

1. 玩家明确邀请、接受后，校验同维度、距离、队伍、玩家是否忙碌和 Boss 来源资格，再一起建战。
2. 两名玩家对单 Boss / 两个敌方 actor 可以从原生 Multi 与该 API 的建战接口开始；每人操控自己的宝可梦。
3. 两名真人对四 / 六单位 Horde 需另做格式、actor 与客户端请求兼容，沿用两模组接口并验证所有人的出招、换宠和阵亡空槽；不能只把 actor 加入名单。
4. 定义共同逃跑、断线、失败恢复和奖励归属。当前 Boss 球结算选第一名玩家，不能宣称每名参与者都会获得奖励。
5. 实测两个独立游戏客户端。当前研究不等于已实现或已实机验收的多人功能。

热加入 API 会直接替换位置，调用者还必须保证只填空位、同意参战、没有重复玩家，并核对更新包发送。该版本遍历 actor 发送通知时遇非玩家 actor 会返回，PvE 的真人加入还要验证客户端通知完整性。本文不提供会误覆盖已有玩家的调试命令。

## 资料

- [Asymmetric Battles 作者说明](https://github.com/necro50n3/asymmetric-battles-api)：列出 Horde / Multi / 开战后加入。
- [AsymmetricBattleBuilder](https://github.com/necro50n3/asymmetric-battles-api/blob/master/common/src/main/java/com/necro/asymmetric/battles/common/api/AsymmetricBattleBuilder.java)。
- [AsymmetricAPI](https://github.com/necro50n3/asymmetric-battles-api/blob/master/common/src/main/java/com/necro/asymmetric/battles/common/api/AsymmetricAPI.java)。
- [Horde Encounters 作者仓库](https://github.com/necro50n3/cobblemon-horde-encounters)。

公开分支可能继续更新。以上玩家入口与格式限制同时核对了整合包中安装的 JAR；本次未修改对方 JAR。
