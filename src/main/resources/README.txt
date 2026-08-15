SensitiveWordFilter-RW 使用说明
================================

1. 文件位置
   插件首次运行后，会在服务器 data/plugins/SensitiveWordFilter-RW/ 目录下生成：
   - config.json          插件配置
   - sensitive_words.txt  敏感词库（每行一个词，# 开头为注释）
   - proper_nouns.txt     专有名词白名单（不会被替换）
   - violations.json      玩家违禁记录（自动维护，勿手动改）

2. 配置项说明
   - filterMode: permissive（宽容，命中词替换为 ***） / enforcing（严格，整句变 ***）
   - onViolationAction: block（拦截并私聊提示） / censor（*** 化后仍发送）
   - checkPlayerName: true 表示玩家进房时检查 ID 是否含敏感词，含则踢出
   - exemptAdmins: true 表示房主/管理员豁免
   - exemptPlayers: 豁免玩家名列表
   - thresholds: 累进惩罚规则
       count: 累计违禁次数达到该值时触发
       action: warn（私聊警告）、mute（禁言）、kick（踢出）、ban（封禁）
       durationMinutes: mute/ban 的时长（分钟），-1 对 ban 表示永久，0 对 warn/kick 无意义
       message: 提示文本，可用 {count} {duration} 占位符

   例如新增一档“第 2 次违禁禁言 5 分钟”：
   {
     "count": 2,
     "action": "mute",
     "durationMinutes": 5,
     "message": "[系统] 你因违禁被禁言 {duration}（第{count}次），消息已被丢弃。"
   }
   - autoResetHours: 每隔多少小时自动清零所有违禁次数，0 为关闭

3. 游戏内指令（房主/管理员可用，聊天框输入 -swf ...）
   -swf reload                    重载配置与词库
   -swf status                    查看敏感词数量与记录玩家数
   -swf test <消息>               测试消息是否命中敏感词（不发送）
   -swf add <敏感词>              添加敏感词
   -swf remove <敏感词>           移除敏感词
   -swf clear <玩家名>            清除某玩家的违禁记录并解封
   -swf clearall                  清除所有玩家违禁记录并解封
   -swf info <玩家名>             查看某玩家违禁次数与封禁状态
   -swf unban <玩家名>            解封某玩家

4. 控制台指令
   swf reload / status / clearall 等（用法同上，无需 - 前缀）

5. 注意事项
   - 修改 config.json 或词库后，使用 -swf reload 生效，或重启服务器。
   - 若误封，使用 -swf unban <玩家名> 或编辑 violations.json 后 reload。
