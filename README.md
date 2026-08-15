# SWF 敏感词过滤插件（SensitiveWordFilter）

## 概述

近期铁锈战争圈内交流环境略显浮躁，玩家昵称或聊天内容中不时出现违禁词汇。为营造更和谐、友善的开服氛围，SWF 插件应运而生。它能够自动过滤聊天消息和玩家名称中的敏感词，降低言语冲突风险，同时帮助玩家避免因一时冲动而说出不当言论，减少误会。

本插件基于 [SensitiveWordFilter（Minecraft 版）](https://github.com/MoeLuoYu/SensitiveWordFilter/tree/main?tab=readme-ov-file) 改编，针对 RW-HPS 服务器进行了适配和增强，并内置了灵活的惩戒系统，服主可根据需要自由调整。

---

## 功能特性

- **敏感词过滤**：实时检测并处理玩家聊天内容及名称中的敏感词。
- **专有名词保护**：支持白名单机制，避免“铁锈”“战争”等正常词汇被误过滤。
- **双过滤模式**：
  - **宽松模式（permissive）**：将敏感词替换为 `***`（类似腾讯游戏风格）。
  - **严格模式（enforcing）**：将整条消息全部替换为 `***`（类似网易三星堆风格）。
- **豁免机制**：可设置房主/管理员免检（`exemptAdmins: true`），也支持指定玩家豁免名单。
- **累进惩罚系统**：根据违规次数自动触发警告、禁言、踢出或封禁，支持自定义时长和提示信息。
- **数据持久化**：违规记录自动保存，支持手动查询和清理。

---

## 安装与配置

### 文件结构
插件首次运行后，会在 `data/plugins/SensitiveWordFilter-RW/` 目录下生成以下文件：

| 文件名 | 说明 |
|--------|------|
| `config.json` | 主配置文件（模式、惩罚规则、豁免名单等） |
| `sensitive_words.txt` | 敏感词库（每行一个词，`#` 开头为注释行） |
| `proper_nouns.txt` | 专有名词白名单（不会被过滤） |
| `violations.json` | 玩家违规记录（自动维护，请勿手动修改） |

### 配置项详解

在一开始使用时请记得自己修改插件的惩罚配置，目前即下即用的体验未做优化。

一定！一定要自己先改一下啦！

#### 基础选项
- `filterMode`：过滤模式  
  - `permissive` —— 宽松（替换为 `***`）  
  - `enforcing` —— 严格（整句变 `***`）
- `onViolationAction`：违规处理方式  
  - `block` —— 拦截并私聊提示  
  - `censor` —— 替换敏感词后仍发送消息
- `checkPlayerName`：是否检查玩家名（`true` 则进房时检测，含敏感词则踢出）
- `exemptAdmins`：是否豁免房主/管理员（`true` 为豁免）
- `exemptPlayers`：豁免玩家名称列表（数组）
  -  exemptPlayers 填 玩家名或 UUID 都可以 ，已支持两种格式。

  -  示例：

  -  ```
  -  "exemptPlayers": [
  -    "你好Fun_GO!_PC",
  -    "66BBF6341xxxxxxxxxxxxFCD47941D9560991AA90C919968443A0FCCBFF70A923B"
  -  ]
  -  ```
  -  匹配时不区分大小写，列表里任意一个命中就豁免。

  -  建议用 UUID，因为玩家名可能会改。
- `autoResetHours`：每隔多少小时自动清零所有违禁次数（`0` 表示关闭）

#### 累进惩罚规则（`thresholds`）
支持多级阶梯式惩罚，示例如下：

```json
{
  "count": 2,
  "action": "mute",
  "durationMinutes": 5,
  "message": "[系统] 你因违禁被禁言 {duration} 分钟（第 {count} 次），消息已被丢弃。"
}
```
- count：触发该档惩罚所需的累计违禁次数

- action：执行动作，可选 warn（警告）、mute（禁言）、kick（踢出）、ban（封禁）

- durationMinutes：禁言或封禁的持续分钟数；-1 表示永久封禁；对 warn/kick 无意义

- message：提示文本，支持占位符 {count} 和 {duration}

> 可添加多档规则，按次数从小到大依次触发。
>
## 控制台命令（Console Commands）

在服务器控制台输入以下命令进行管理：

| 命令 | 作用 |
|------|------|
| `swf reload` | 重载配置与词库（修改后需执行此命令或重启服务器） |
| `swf status` | 查看当前敏感词数量和违规记录玩家数 |
| `swf test <消息>` | 测试某条消息是否会命中敏感词 |
| `swf add <敏感词>` | 动态添加敏感词 |
| `swf remove <敏感词>` | 动态移除敏感词 |
| `swf clear <玩家名>` | 清除指定玩家的违禁记录并解封 |(如遇到玩家不在线请使用UUID解封，或修改数据库文件)
| `swf clearall` | 清除所有玩家违禁记录并解封 |
| `swf info <玩家名>` | 查看某玩家的违禁次数和封禁状态 |
| `swf unban <玩家名>` | 手动解封某玩家 |

### 注意事项

- **配置生效**：直接修改 `config.json` 或词库文件后，务必使用 `swf reload` 命令，或重启服务器，否则更改不会生效。
- **误封处理**：若出现误封，可使用 `swf unban <玩家名>` 解封，或手动编辑 `violations.json` 后执行 `swf reload`。
- **豁免限制**：当前版本仅支持通过 `exemptAdmins` 豁免房主，暂不支持按玩家 ID 单独豁免。
- **自动重置**：`autoResetHours` 可定期清零违规次数，适合需要长期平稳运营的服务器。

### 致谢

- 感谢 [SensitiveWordFilter](https://github.com/MoeLuoYu/SensitiveWordFilter/tree/main?tab=readme-ov-file) 项目（Minecraft 版）提供的灵感与基础实现。
- 本插件大部分代码由 AI 辅助编写，若存在许可合规问题，请联系 `Dongserver@126.com`，我们将在 5 个工作日内处理。

### Open Your Eyes
本插件已加入 开眼界计划，愿为铁锈战争开服生态持续贡献力量！

同时也诚邀具备插件开发能力的诸位一同加入 开眼界计划，共筑更繁荣的开服社区。

- 感谢 [铁锈战争开服教程](http://www.rustedsvrwiki.de5.net) 提供的引流与文档支持。

---

祝您开服愉快，和谐交流！
