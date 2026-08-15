package cn.dongdong365.rw.sensitivewordfilter;

import net.rwhps.server.game.event.core.EventListenerHost;
import net.rwhps.server.game.event.game.PlayerJoinEvent;
import net.rwhps.server.game.player.PlayerHess;
import net.rwhps.server.util.Time;
import net.rwhps.server.util.annotations.core.EventListenerHandler;
import net.rwhps.server.util.log.Log;

/**
 * 玩家加入事件：检查玩家名违禁词、清理过期封禁。
 */
public class PluginEventListener implements EventListenerHost {

    private final SensitiveWordFilterPlugin plugin;

    public PluginEventListener(SensitiveWordFilterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handleException(Throwable exception) {
        Log.error("[SensitiveWordFilter] 事件处理异常", exception);
    }

    @EventListenerHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        PlayerHess player = event.getPlayer();
        if (player == null) return;

        String uuid = player.getConnectHexID();
        long now = Time.concurrentMillis();

        // 先检查过期封禁，避免误踢
        long banUntil = plugin.getViolationData().getBanUntil(uuid);
        if (banUntil > 0 && banUntil <= now) {
            plugin.unban(uuid);
        }

        // 同步持久化的禁言状态
        long muteUntil = plugin.getViolationData().getMuteUntil(uuid);
        if (muteUntil > 0 && muteUntil > now) {
            player.setMuteTime(muteUntil);
        } else if (muteUntil > 0) {
            plugin.getViolationData().clearMute(uuid);
        }

        if (plugin.getConfig().checkPlayerName && !plugin.getWordFilter().isExempt(player)) {
            if (plugin.getWordFilter().containsSensitive(player.getName())) {
                Log.clog("[敏感词] 玩家 {0} 的玩家名包含违禁词，已踢出。", player.getName());
                player.kickPlayer(plugin.getConfig().playerNameKickMessage);
                return;
            }
        }
    }
}
