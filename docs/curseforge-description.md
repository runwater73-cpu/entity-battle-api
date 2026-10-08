# Entity Battle API

## Summary

Convert supported Minecraft creatures into Cobblemon Pokémon while reusing their native models and enabling configurable battles, capture, and boss encounters.

## Description

Entity Battle API adds reusable integration for supported vanilla and modded creatures. It registers independent Pokémon species and uses Cobblemon's battle engine, party storage, capture, and world entities. Native creature renderers provide their appearance without bundling other mods' models or textures.

### Features

- 169 bundled creature profiles, enabled only when their source entities are available.
- A converter item and configurable world modes: retain native creatures until conversion, or spawn them as Pokémon.
- 15 configured bosses from The Twilight Forest, The Aether, Deep Aether, vanilla Minecraft, and the supported Kaleidoscope Twilight content.
- Original story requirements before automatic conversion for supported dungeon bosses.
- Configurable levels, species stats, abilities, moves, capture permission, held items, and reinforcements.
- Optional asymmetric horde battles with up to six enemies, including the boss.
- Optional compatibility for TeamRocket fusion and maid allies, Pokémon dolls, Cobbledex / JEI, and Xaero minimap icons.

### Requirements

Minecraft 1.21.1, NeoForge, Cobblemon 1.8.1, and Kotlin for Forge. Install Entity Battle API on both the server and clients.

Asymmetric Battles and Horde Encounters are both required for the configured horde encounters. Other integrations and creature content mods are optional. Install each dependency's own required libraries.

### Getting started

Ordinary supported creatures retain their native behavior by default. Use the converter item to permanently turn one into a Pokémon. The item is in its own creative tab and can be used in survival, but currently has no built-in crafting recipe.

Temporary challenges against native creatures with the Cobblemon challenge key are disabled by default and can be enabled in the mod configuration. Converted Pokémon use normal Cobblemon interactions.

Supported bosses become Pokémon automatically after their original encounter requirements are met. Bosses without a story gate convert after spawning. Boss capture is disabled; a complete victory provides a separate level-one species reward. Supported native victory callbacks preserve original loot and dungeon progression. Battle equipment does not drop as additional loot.

The Quest Ram remains a native quest creature until its original wool quest reward is delivered. Players may then voluntarily convert and capture it.

### Beta status

This is a public testing build. Automated checks cover selected battle, lifecycle, rendering, and optional dependency paths. Natural dungeon playthroughs, balance, random capture, and every third-party interface still require testing. Native boss AI is not copied into turn-based battles, and human multiplayer co-op boss battles are not implemented.

Older resource packs that replace Pokémon geometry may be incompatible with Cobblemon 1.8.1 poses. The bundled integrations do not repair arbitrary outdated resource packs.

### Developers

Creature profiles and independent species settings can be overridden through data packs. Java extension points support source eligibility, lifecycle callbacks, appearance snapshots, multipart rendering, and move effects. The repository contains integration examples, Chinese boss settings, and validation scripts.

Project-owned code and configuration are MIT licensed. Source mods and their assets remain under their respective authors' licenses.

Source and issue tracker: [GitHub](https://github.com/runwater73-cpu/entity-battle-api), [Issues](https://github.com/runwater73-cpu/entity-battle-api/issues).

## 中文简介

为已接入的原版与模组生物注册独立宝可梦物种，复用来源模型和方可梦原生对战、队伍与捕捉流程。内置一百六十九种生物及十五种首领设定，支持按需群战、来源剧情与结算、融合、玩偶和查询兼容。

当前为公开测试版。安装与功能边界以上方英文说明及仓库中文文档为准。
