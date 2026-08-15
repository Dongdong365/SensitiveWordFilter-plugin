package cn.dongdong365.rw.sensitivewordfilter;

import net.rwhps.server.data.global.Data;
import net.rwhps.server.game.event.EventManage;
import net.rwhps.server.plugin.Plugin;
import net.rwhps.server.util.Time;
import net.rwhps.server.util.file.FileUtils;
import net.rwhps.server.util.game.command.CommandHandler;
import net.rwhps.server.util.log.Log;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * RW-HPS 敏感词过滤插件主类。
 */
public class SensitiveWordFilterPlugin extends Plugin {
    private FilterConfig config;
    private ViolationData violationData;
    private WordFilter wordFilter;
    private ChatFilterImpl chatFilter;

    @Override
    public void onEnable() {
        Log.clog("[SensitiveWordFilter] 插件初始化...");
        FileUtils folder = pluginDataFileUtils;
        releaseResourceIfMissing(folder, "config.json", "/config.json");
        releaseResourceIfMissing(folder, "README.txt", "/README.txt");
        config = new FilterConfig(folder.toFile("config.json"));
        violationData = new ViolationData(folder.toFile("violations.json"));
        wordFilter = new WordFilter(folder, config);
        chatFilter = new ChatFilterImpl(this);
        cleanupExpiredPunishments();

    // ========== 调试更新检查 ==========
    new RemoteVersionChecker().checkAndLog();
    // ==================================

        cleanupExpiredPunishments();
    }

    private void releaseResourceIfMissing(FileUtils folder, String dataFileName, String resourcePath) {
        FileUtils target = folder.toFile(dataFileName);
        if (target.exists()) return;
        try (java.io.InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return;
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            target.writeFile(sb.toString(), false);
        } catch (Exception e) {
            Log.error("[SensitiveWordFilter] 释放默认文件失败: " + dataFileName, e);
        }
    }

    @Override
    public void registerEvents(EventManage eventManage) {
        eventManage.registerListener(new PluginEventListener(this));
    }

    @Override
    public void registerCoreCommands(CommandHandler handler) {
        Commands.registerCore(this, handler);
    }

    @Override
    public void init() {
        Data.core.getAdmin().addChatFilter(chatFilter);
        scheduleAutoReset();
        Log.clog("[SensitiveWordFilter] 已启用，敏感词数量: " + wordFilter.getSensitiveWordCount());
        Log.clog("[SensitiveWordFilter] 使用 swf help 查看可用命令！");
    }

    @Override
    public void onDisable() {
        violationData.save();
        Log.clog("[SensitiveWordFilter] 已保存数据并禁用。");
    }

    public void reload() {
        config.load();
        wordFilter.reload();
        violationData.load();
        cleanupExpiredPunishments();
        scheduleAutoReset();
        Log.clog("[SensitiveWordFilter] 配置、词库、记录已重载。");
    }

    /**
     * 解除指定 UUID 的封禁。
     */
    public void unban(String uuid) {
        Data.core.getAdmin().getBannedUUIDs().remove(uuid);
        ViolationData.PlayerRecord r = violationData.getAll().get(uuid);
        if (r != null && r.banUntil != 0) {
            r.banUntil = 0;
            violationData.save();
        }
    }

    private void cleanupExpiredPunishments() {
        long now = Time.concurrentMillis();
        for (Map.Entry<String, ViolationData.PlayerRecord> e : violationData.getAll().entrySet()) {
            long banUntil = e.getValue().banUntil;
            if (banUntil > 0 && banUntil <= now) {
                unban(e.getKey());
            } else if (banUntil > 0) {
                long delayMs = banUntil - now;
                int delaySec = (int) (delayMs / 1000);
                if (delaySec > 0) {
                    final String uuid = e.getKey();
                    String taskName = "SensitiveWordFilter-Unban-" + uuid;
                    net.rwhps.server.core.thread.Threads.closeTimeTask(taskName, "SensitiveWordFilter");
                    net.rwhps.server.core.thread.Threads.newTimedTask(
                            taskName,
                            "SensitiveWordFilter",
                            "启动时自动解封",
                            delaySec,
                            Integer.MAX_VALUE,
                            TimeUnit.SECONDS,
                            () -> unban(uuid)
                    );
                }
            }

            long muteUntil = e.getValue().muteUntil;
            if (muteUntil > 0 && muteUntil <= now) {
                violationData.clearMute(e.getKey());
            }
        }
    }

    private void scheduleAutoReset() {
        net.rwhps.server.core.thread.Threads.closeTimeTask(
                "SensitiveWordFilter-AutoReset",
                "SensitiveWordFilter"
        );
        int hours = config.autoResetHours;
        if (hours <= 0) return;
        int seconds = hours * 3600;
        net.rwhps.server.core.thread.Threads.newTimedTask(
                "SensitiveWordFilter-AutoReset",
                "SensitiveWordFilter",
                "定时清零违禁次数",
                seconds,
                seconds,
                TimeUnit.SECONDS,
                () -> {
                    violationData.clearAll();
                    Log.clog("[SensitiveWordFilter] 已按配置定时清零所有玩家违禁次数。");
                }
        );
    }

    public FilterConfig getConfig() {
        return config;
    }

    public ViolationData getViolationData() {
        return violationData;
    }

    public WordFilter getWordFilter() {
        return wordFilter;
    }
}
