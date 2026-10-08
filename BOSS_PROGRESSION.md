# Boss 流程等级

更新日期：2026-10-08。下表为用户确认的源码内置等级；野外随机上下浮动 2 级。暮色领主后续制作已取消，新整合包由其他模组生成训练家；固定 50 级队伍仅保留历史记录。

| 地区 / 阶段 | Boss | 基准 | 实际等级 |
| --- | --- | --- | --- |
| 暮色入口 | 娜迦 | 25 | 23–27 |
| 暮色前置 | 巫妖 | 30 | 28–32 |
| 三条分支前段 | 米诺菇、幻影骑士、雪怪首领 | 35 | 33–37 |
| 三条分支终点 | 九头蛇、暮初恶魂、冰雪女王 | 40 | 38–42 |
| 暮色支线 | 森罗物语暮色向日葵 | 45 | 43–47 |
| 终焉城堡（历史方案） | 暮色领主六只训练家队伍，后续制作已取消 | 50 | 原方案固定 50 |
| 天境青铜地牢 | 滑行魔石 | 55 | 53–57 |
| 天境白银地牢 | 武神女王 | 60 | 58–62 |
| 天境黄金地牢 | 烈阳巨灵 | 65 | 63–67 |
| 深入天境黄铜地牢 | 狂瞳龙卷 | 65 | 63–67 |
| 主世界后续 | 监守者 | 70 | 68–72 |
| 下界后续 | 凋灵 | 75 | 73–77 |

## 为什么并列

核对安装的暮色森林 4.8.3345 原生 advancement：娜迦 → 巫妖后分为迷宫、黑暗森林、冰雪三条路线。米诺菇 → 九头蛇；奖杯基座 / 幻影骑士 → 暮初恶魂；雪怪首领 → 冰雪女王。三条终点的进度在 `progress_merge` 汇合，再走巨人、灰烬之灯、终焉高原 / 城堡路线。

这是宝可梦等级安排，不新增维度进入限制，不改变 MC 原作任务前置。向日葵已作为 45 级独立支线接入；其胜利没有新增为领主挑战的前置条件。

## 保留的战斗内容

已有种族值、特性、四招、随从数量和等级偏移保留。巫妖和普通野生巫妖仆从范围统一 28–32，群战仆从偏移仍为 0。其他临时随从沿用各自 `levelOffset`；普通生物 profile 等级不跟随 Boss 全局上调。

部分四招的学习等级高于新 Boss 等级。因此野外 Boss profile 的可选 `moves` 显式给齐已审批的四招；仅新建个体时应用。species 的学习表不改，玩家奖励仍按原学习等级学招，捕捉 / 存档中已有个体不会被批量覆写等级和招式。

## 如何修改

- 野外等级：`src/main/resources/data/entitybattle/battle_profiles/pack_mobs.json` 与 `vanilla_mobs.json` 的对应 `level.min`、`level.max`。
- 向日葵：`src/main/resources/data/entitybattle/battle_profiles/sunflower_boss.json`，独立物种数据位于 `species/kaleidoscope_twilight_umbral_sunflower.json`。
- 狂瞳龙卷：`src/main/resources/data/entitybattle/battle_profiles/deep_aether_boss.json`，独立物种数据位于 `species/deep_aether_eots_controller.json`。与烈阳巨灵同级，未改变后续 70 / 75 级流程。
- 固定战斗招式：同一 profile 的 `moves` 数组，1–4 个不重复的 Showdown 招式 ID；删除字段恢复按等级自动学招。已有个体保留存储的招式。
- 领主：`src/main/resources/data/entitybattle/npcs/twilight_lord.json` 每个队员的 `level=50`、`moves=...`、`held_item=...`。
- 学习表 / 种族值 / 特性：对应 species JSON。这些是物种共享设定，会影响玩家拥有的个体。

可用数据包覆盖同路径资源；修改源码后构建 JAR 即可随模组分发。`validation/apply-boss-progression.cjs` 保留本次审批结果供开发重放；它不随 JAR 运行，也不会动态重写用户文件。

临时随从的固定四招由对应 `bossBattle.minionMoves` 配置，优先于普通随从 profile 的 `moves`；省略两者时依等级学习。这样降低 Boss 与援军等级不会删掉已审批的战斗招式，也不会给普通野生随从提前学习高等级技能。
