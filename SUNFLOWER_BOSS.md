# 森罗物语暮色：向日葵 Boss

2026-10-07 用户追加批准 45 级。来源为 Kaleidoscope Twilight 1.1.3 的 `kaleidoscope_twilight:umbral_sunflower`；需安装来源模组及其自身依赖，缺失时本 profile 自动跳过，物种不进入可用列表。

## 战斗设定

| 项目 | 配置 |
| --- | --- |
| 等级 | 基准 45，实际 43–47，与本批野外 Boss 的上下 2 级规则一致 |
| 属性 | 草 / 钢 |
| 种族值 | HP 155，攻击 130，防御 120，特攻 55，特防 100，速度 70；总计 630 |
| 特性 | 锋锐 `sharpness`，原生切割招式威力提高 50% |
| 四招 | 叶刃 `leafblade`、圣剑 `sacredsword`、重踏 `bulldoze`、守住 `protect` |
| 携带物 | 奇迹种子 `cobblemon:miracle_seed` |
| 阵容 | 单体野生 Boss 战，玩家仍可轮换六只；无自动生成随从 |
| 捕捉 | 野外 Boss 不可捕捉；完整胜利沿用本项目的一次性 1 级奖励 |

设计取材于来源的剑技、剑气、地刺、盾牌，以原生招式和特性完成战斗。四招在 Boss profile 固定；玩家获得的个体按 species 学习表学习，1 级奖励没有 Boss 装备。

## 世界与死亡流程

2026-10-08 按追加授权统一 Boss 默认宝可梦战斗。向日葵没有剧情开战门槛，默认生成后自动成为宝可梦，不再强制手动转换。全局 `bossWorldMode=NATIVE_MOB` 时仍可保留原生 AI 与攻击，玩家使用转换器或已启用的 R 挑战选择宝可梦战斗。

转换时使用现有 `EntityNativePokemonConversion`、真实 `PokemonEntity` 和 Cobblemon 野生战接口。来源流程复用 `EntityBossSources` 完整 NBT 快照和原生死亡回调。自动模式中断后保持宝可梦；原生模式临时转换中断后恢复同一 UUID 的原生来源。两种模式都保留来源 NBT。飞剑、巨剑、地刺、震荡及标准剑气通过原生 owner 识别，转换后终止，仅清理该 Boss 自己的攻击；这些短期攻击不在退出时重放。胜利后恢复来源并调用其原生死亡流程。

该来源继承暮色 `BaseTFBoss`：原作缓冲战利品并在死亡动画结束后生成深色宝箱。奖杯和热泪之剑来自来源原生掉落表，本项目不复制宝箱算法、重写 loot table 或额外补发同名物品。战斗携带的奇迹种子依现有 Boss 装备掉落规则不额外掉落。任务插件是否接受所有死亡事件仍需游戏内检查。

原作第一次致命 MC 攻击会复活进入第二阶段，这是来源 `hurt` 的行为。当前宝可梦战斗使用原生锋锐和单条 HP，不另加复活特性或技能切换；完整胜利调用死亡回调完成结算。退出后原生二阶段状态来自转换前快照。

## 模型与动画

模型、贴图通过来源自身渲染器接入现有 Cobblemon 共享模型仓库，未复制第三方美术资产。全部物种消费者使用相同独立 species，不使用其他官方宝可梦当模板。

四招分别驱动来源显示副本的剑技、剑气、地刺、盾牌动画时钟，统一在现有 30 tick 招式提示中开始、推进、清零。兼容访问器仅客户端加载；显示副本不 tick 原生 AI，也不会生成有伤害的原生剑气或地刺实体。动画对应模型动作，不能等同于完整原作技能的世界弹幕效果。

## 修改

- 等级、携带物、固定四招：`src/main/resources/data/entitybattle/battle_profiles/sunflower_boss.json`。
- 属性、种族值、特性、学习表：`src/main/resources/data/entitybattle/species/kaleidoscope_twilight_umbral_sunflower.json`。
- 世界模式 / 原生死亡声明：`EntityBossSources.register()` 中该来源的 `EntityBattleSourceAdapter`。
- 动画映射：`client/OtherBossPoses.java`；来源同步时钟访问：`client/mixin/SunflowerAnimationAccess.java`。

可通过数据包覆盖 JSON。源码和配置随 JAR 分发，不修改整合包目录的数据包、配置或存档。

## 验证结果

- `build/sunflower-boss-server.log`：`SUNFLOWER_BOSS PASS`。真实来源实体、转换 API、单体原生首回合、全体原生攻击归属、退出后同 UUID / 二阶段 NBT 还原、攻击不重放、中断不发奖励、完整胜利宝箱内一份奖杯和热泪之剑、奇迹种子不掉落、一次性 1 级奖励均通过。
- `build/item-icon-sunflower-client.log`：`SUNFLOWER_POSES PASS`，四个原生模型动画时钟开始、推进、停止均通过；`ITEM_ICONS PASS` 包含向日葵的真实物品绘制范围。截图 `build/model-smoke-run/jei-item-origin.png` 已检查完整图标。
- 缺少森罗物语的隔离服务端也通过 `TRAINER_SMOKE PASS`。攻击归属查询使用普通 `SourceOwnedAttack` 接口，来源模组存在时由可选 Mixin 实现，避免直接加载缺失来源的 Mixin 类型。

服务端测试在独立世界使用 FakePlayer 和关闭移动的来源。首回合使用真实选招包，胜利部分通过引擎强制 KO 检验结算，不代表完整自然 Boss 游玩或强度平衡已验收。来源结构通关与其他任务模组、战斗动画视觉仍需游戏内确认。
