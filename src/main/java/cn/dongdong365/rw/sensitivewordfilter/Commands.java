package cn.dongdong365.rw.sensitivewordfilter;

import net.rwhps.server.func.StrCons;
import net.rwhps.server.game.manage.HeadlessModuleManage;
import net.rwhps.server.game.manage.PlayerManage;
import net.rwhps.server.game.player.PlayerHess;
import net.rwhps.server.util.game.command.CommandHandler;
import net.rwhps.server.util.log.Log;
import net.rwhps.server.util.log.exp.ImplementedException;

/**
 * 管理员指令注册。
 */
public class Commands {

    public static void registerClient(SensitiveWordFilterPlugin plugin, CommandHandler handler) {
        handler.register("swf", "<action> [args...]", "敏感词过滤管理（仅管理员）", (String[] args, Object sender) -> {
            PlayerHess p = (PlayerHess) sender;
            if (!p.isAdmin()) {
                safeSendSystemMessage(p, "[SWF] 仅房主/管理员可用");
                return;
            }
            if (args.length == 0) {
                safeSendSystemMessage(p, usage());
                return;
            }
            String[] subArgs = new String[args.length - 1];
            System.arraycopy(args, 1, subArgs, 0, subArgs.length);
            String reply = handle(plugin, args[0], subArgs, p);
            if (reply != null && !reply.isEmpty()) {
                safeSendSystemMessage(p, reply);
            }
        });
    }

    public static void registerCore(SensitiveWordFilterPlugin plugin, CommandHandler handler) {
        handler.register("swf", "<action> [args...]", "敏感词过滤控制台管理", (String[] args, Object sender) -> {
            if (args.length == 0) {
                Log.clog(usage());
                return;
            }
            String[] subArgs = new String[args.length - 1];
            System.arraycopy(args, 1, subArgs, 0, subArgs.length);
            String reply = handle(plugin, args[0], subArgs, null);
            if (reply != null && !reply.isEmpty()) {
                Log.clog(reply);
            }
        });
    }

    private static String usage() {
        return "[SWF] 用法: swf <reload|status|test|add|remove|clear|clearall|info|unban> [参数]";
    }

    private static String handle(SensitiveWordFilterPlugin plugin, String action, String[] args, PlayerHess sender) {
        switch (action.toLowerCase()) {
            case "reload":
                plugin.reload();
                return "[SWF] 配置、词库与违禁记录已重载。";
            case "status":
                return "[SWF] 敏感词数量: " + plugin.getWordFilter().getSensitiveWordCount()
                        + " | 记录玩家数: " + plugin.getViolationData().getAll().size();
            case "test":
                if (args.length == 0) return "[SWF] 用法: swf test <消息>";
                String testMsg = join(args);
                WordFilter.FilterResult r = plugin.getWordFilter().filter(testMsg);
                return "[SWF] 测试消息: " + testMsg + " | 命中: " + (r.hit ? "是" : "否")
                        + " | 过滤结果: " + (r.hit ? r.filtered : testMsg);
            case "add":
                if (args.length == 0) return "[SWF] 用法: swf add <敏感词>";
                String word = join(args);
                plugin.getWordFilter().addSensitiveWord(word);
                return "[SWF] 已添加敏感词: " + word;
            case "remove":
                if (args.length == 0) return "[SWF] 用法: swf remove <敏感词>";
                String rw = join(args);
                plugin.getWordFilter().removeSensitiveWord(rw);
                return "[SWF] 已移除敏感词: " + rw;
            case "clear":
                if (args.length == 0) return "[SWF] 用法: swf clear <玩家名>";
                PlayerHess target = findOnlinePlayer(join(args));
                if (target == null) return "[SWF] 找不到该在线玩家。";
                plugin.getViolationData().clear(target.getConnectHexID());
                plugin.unban(target.getConnectHexID());
                return "[SWF] 已清除玩家 " + target.getName() + " 的违禁记录与封禁。";
            case "clearall":
                for (String uuid : plugin.getViolationData().getAll().keySet()) {
                    plugin.unban(uuid);
                    plugin.getViolationData().clearMute(uuid);
                }
                plugin.getViolationData().clearAll();
                return "[SWF] 已清除所有玩家的违禁记录、封禁与禁言。";
            case "info":
                if (args.length == 0) return "[SWF] 用法: swf info <玩家名>";
                PlayerHess t = findOnlinePlayer(join(args));
                if (t == null) return "[SWF] 找不到该在线玩家。";
                int count = plugin.getViolationData().getCount(t.getConnectHexID());
                long banUntil = plugin.getViolationData().getBanUntil(t.getConnectHexID());
                long muteUntil = plugin.getViolationData().getMuteUntil(t.getConnectHexID());
                String banInfo = banUntil == 0 ? "未封禁" : (banUntil < 0 ? "永久封禁" : "封禁至 " + formatTime(banUntil));
                String muteInfo = muteUntil == 0 ? "未禁言" : ("禁言至 " + formatTime(muteUntil));
                return "[SWF] 玩家 " + t.getName() + " | 违禁次数: " + count + " | " + banInfo + " | " + muteInfo;
            case "unban":
                if (args.length == 0) return "[SWF] 用法: swf unban <玩家名>";
                PlayerHess u = findOnlinePlayer(join(args));
                if (u == null) return "[SWF] 找不到该在线玩家。";
                plugin.unban(u.getConnectHexID());
                plugin.getViolationData().clearMute(u.getConnectHexID());
                return "[SWF] 已解封并解除禁言玩家 " + u.getName() + "。";
            default:
                return usage();
        }
    }

    private static PlayerHess findOnlinePlayer(String name) {
        try {
            PlayerManage pm = HeadlessModuleManage.INSTANCE.getHps().getRoom().getPlayerManage();
            return pm.findPlayer(new StrCons() {
                @Override
                public void invoke(String str) {}

                @Override
                public void invoke(String str, Object... objects) {
                    invoke(str);
                }
            }, name);
        } catch (Exception e) {
            return null;
        }
    }

    private static String join(String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static String formatTime(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new java.util.Date(millis));
    }

    private static void safeSendSystemMessage(PlayerHess player, String msg) {
        try {
            player.sendSystemMessage(msg);
        } catch (ImplementedException.PlayerImplementedException e) {
            Log.error("[SWF] 发送系统消息失败", e);
        }
    }
}
