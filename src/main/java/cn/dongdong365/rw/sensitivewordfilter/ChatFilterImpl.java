package cn.dongdong365.rw.sensitivewordfilter;

import net.rwhps.server.data.global.Data;
import net.rwhps.server.game.player.PlayerHess;
import net.rwhps.server.net.Administration;
import net.rwhps.server.net.core.server.AbstractNetConnect;
import net.rwhps.server.util.Time;
import net.rwhps.server.util.log.Log;
import net.rwhps.server.util.log.exp.ImplementedException;

import java.util.concurrent.TimeUnit;

/**
 * 聊天过滤器，接入 RW-HPS 的 Administration#addChatFilter。
 */
public class ChatFilterImpl implements Administration.ChatFilter {
    private final SensitiveWordFilterPlugin plugin;

    public ChatFilterImpl(SensitiveWordFilterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String filter(PlayerHess player, String message) {
        if (message == null) return null;
        if (plugin.getWordFilter().isExempt(player)) return message;

        WordFilter.FilterResult result = plugin.getWordFilter().filter(message);
        if (!result.hit) return message;

        int count = plugin.getViolationData().increment(player.getConnectHexID());
        FilterConfig.Threshold threshold = plugin.getConfig().getThresholdFor(count);
        String actionMsg = formatMessage(threshold.message, count, threshold.durationMinutes);

        if (plugin.getConfig().logToConsole) {
            Log.clog("[敏感词] {0} 触发违禁 (第{1}次): {2}", player.getName(), count, message);
        }

        switch (threshold.action.toUpperCase()) {
            case "WARN":
                safeSendSystemMessage(player, actionMsg);
                if ("censor".equalsIgnoreCase(plugin.getConfig().onViolationAction)) {
                    return result.filtered;
                }
                return null;
            case "MUTE":
                int muteMinutes = Math.max(1, threshold.durationMinutes);
                long muteUntil = Time.getTimeFutureMillis(muteMinutes * 60L * 1000L);
                player.setMuteTime(muteUntil);
                plugin.getViolationData().setMuteUntil(player.getConnectHexID(), muteUntil);
                safeSendSystemMessage(player, actionMsg);
                return null;
            case "KICK":
                player.kickPlayer(actionMsg);
                return null;
            case "BAN":
                applyBan(player, count, threshold);
                return null;
            default:
                safeSendSystemMessage(player, actionMsg);
                return null;
        }
    }

    private void applyBan(PlayerHess player, int count, FilterConfig.Threshold threshold) {
        String uuid = player.getConnectHexID();
        long banUntil;
        boolean permanent = threshold.durationMinutes < 0;
        if (permanent) {
            banUntil = -1;
        } else {
            banUntil = Time.concurrentMillis() + threshold.durationMinutes * 60L * 1000L;
        }
        plugin.getViolationData().setBanUntil(uuid, banUntil);

        Data.core.getAdmin().getBannedUUIDs().add(uuid);
        if (player.getCon() != null) {
            Data.core.getAdmin().getBannedIPs().add(((AbstractNetConnect) player.getCon()).getIp());
        }

        String actionMsg = formatMessage(threshold.message, count, threshold.durationMinutes);
        player.kickPlayer(actionMsg);

        if (!permanent && banUntil > 0) {
            long delayMs = banUntil - Time.concurrentMillis();
            if (delayMs > 0) {
                int delaySec = (int) (delayMs / 1000);
                net.rwhps.server.core.thread.Threads.newTimedTask(
                        "SensitiveWordFilter-Unban-" + uuid,
                        "SensitiveWordFilter",
                        "自动解封",
                        delaySec,
                        delaySec,
                        TimeUnit.SECONDS,
                        () -> plugin.unban(uuid)
                );
            }
        }
    }

    public static String formatMessage(String template, int count, int durationMinutes) {
        if (template == null) return "";
        String durationText;
        if (durationMinutes < 0) {
            durationText = "永久";
        } else if (durationMinutes >= 60) {
            durationText = (durationMinutes / 60) + "小时";
        } else {
            durationText = durationMinutes + "分钟";
        }
        return template.replace("{count}", String.valueOf(count))
                .replace("{duration}", durationText);
    }

    private static void safeSendSystemMessage(PlayerHess player, String msg) {
        try {
            player.sendSystemMessage(msg);
        } catch (ImplementedException.PlayerImplementedException e) {
            Log.error("[敏感词] 发送系统消息失败", e);
        }
    }
}
