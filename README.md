# Simple Story Books Mod

这是一个为 Minecraft Fabric 1.20.1 设计的模组，可以在世界的宝箱中随机生成可阅读的书籍。

## 功能
- 在地牢、废弃矿井、村庄等宝箱中随机生成成书。
- 完全可配置的书籍内容。
- 可配置的生成概率和目标宝箱。
- 可即时生效的改动

## 如何使用
### 游戏内
##### 直接输入`/simplestory`即可打开ui

### 游戏外
#### 1. 添加书籍
模组首次运行后，会在 `.minecraft/config/simplestorybooks/books/` 目录下生成一个 `example_book.json`。
你可以复制该文件并修改，或者创建新的 `.json` 文件来添加更多书籍。

**书籍 JSON 格式:**
```json
{
  "title": "书籍标题",
  "author": "作者名",
  "pages": [
    "第一页的内容...",
    "第二页的内容..."
  ]
}
```

### 2. 修改配置
在 `.minecraft/config/simplestorybooks/config.json` 中可以调整生成概率。

**配置格式:**
```json
{
  "loot_chance": 0.1,  // 生成概率 (0.0 - 1.0), 0.1 代表 10%
  "loot_tables": [     // 启用生成的战利品表列表
    "minecraft:chests/simple_dungeon",
    "minecraft:chests/abandoned_mineshaft",
    "minecraft:chests/village/village_plains_house",
    "minecraft:chests/stronghold_library"
  ]
}
```

## 构建模组
1. 确保安装了 JDK 17。
2. 在项目根目录打开终端。
3. 运行 `gradlew build` (Windows) 或 `./gradlew build` (Mac/Linux)。
4. 构建好的模组文件位于 `build/libs/` 目录。


