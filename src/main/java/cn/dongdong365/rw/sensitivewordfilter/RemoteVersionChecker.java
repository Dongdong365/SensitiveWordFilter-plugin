package cn.dongdong365.rw.sensitivewordfilter;

import net.rwhps.server.util.log.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 基于远程文本文件的版本检查器（自定义线程池，精简线程名）。
 * <p>配置参数集中在类顶部。
 * <p>版本比较支持 "v年.月.日" 格式（如 v2026.08.16）。
 * <p>日志输出使用 net.rwhps.server.util.log.Log。
 * <p><b>所有消息硬编码，无外部文件依赖，不创建任何文件。</b>
 */
public class RemoteVersionChecker {
    // ======================== 可配置常量（只改这里） ========================
    private static final String PLUGIN_NAME = "swf-plugin";          // 远程版本列表中的键名
    private static final String CURRENT_VERSION = "v26.08.16";        // 当前插件版本
    private static final String REMOTE_URL = "http://www.rustedsvrwiki.de5.net/dongserverpluginslastversion.txt";
    private static final String PREFIX = "SensitiveWordFilter";       // 日志中的前缀（可改短）
    // =====================================================================

    // ======================== 硬编码消息（可自由修改） ========================
    private static final List<String> MESSAGES_LATEST = Arrays.asList(
        "插件是最新版本！",
        "及时更新插件是最好的习惯！请继续保持哦~"
    );
    private static final List<String> MESSAGES_UPDATE = Arrays.asList(
        "插件已过时！请注意更新！",
        "最新版号为：{version}。",
        "注意，该插件仍在持续优化并修改异常中，不更新导致的问题请不要向官方仓库反馈。",
        "请及时更新，并观看更新的仓库README描述文件以及更新日志，了解最新变化！",
        "更新链接：https://github.com/Dongdong365/SensitiveWordFilter-plugin/releases/latest"
    );
    // =====================================================================

    // 自定义单线程池，线程名为 "UpdateCheck"，简短清晰
    private static final ExecutorService UPDATE_EXECUTOR = Executors.newSingleThreadExecutor(
        (Runnable r) -> {
            Thread t = new Thread(r, "SensitiveWordFilter(SWF)");
            t.setDaemon(true); // 守护线程，不阻止JVM退出
            return t;
        }
    );

    private final String pluginName;
    private final String currentVersion;
    private final String remoteUrl;
    private final String prefix;
    private volatile boolean checked = false;

    public RemoteVersionChecker() {
        this(PLUGIN_NAME, CURRENT_VERSION, REMOTE_URL, PREFIX);
    }

    public RemoteVersionChecker(String pluginName, String currentVersion, String remoteUrl, String prefix) {
        this.pluginName = pluginName;
        this.currentVersion = currentVersion;
        this.remoteUrl = remoteUrl;
        this.prefix = prefix;
    }

    /**
     * 异步检查更新（使用自定义线程池，线程名简洁）。
     */
    public void checkAndLog() {
        if (checked) return;
        checked = true;
        CompletableFuture.runAsync(() -> {
            try {
                String latest = fetchRemoteVersion();
                boolean hasNew = latest != null && isNewerVersion(latest, currentVersion);
                List<String> messages = hasNew ? MESSAGES_UPDATE : MESSAGES_LATEST;
                for (String raw : messages) {
                    String out = raw.replace("{prefix}", prefix)
                                    .replace("{version}", hasNew ? latest : currentVersion);
                    if (hasNew) {
                        Log.warn(out);
                    } else {
                        Log.info(out);
                    }
                }
            } catch (Exception e) {
                Log.error("[" + prefix + "] 检查更新失败，请手动检查更新。当然，该插件仍能继续运行。", e);
            }
        }, UPDATE_EXECUTOR); // 使用自定义线程池
    }

    // 同步检查（保留）
    public String checkForUpdatesSync() throws IOException {
        String latest = fetchRemoteVersion();
        if (latest != null && isNewerVersion(latest, currentVersion)) {
            return latest;
        }
        return null;
    }

    public void resetCheck() { checked = false; }

    // ------------------------ 内部实现 ------------------------
    private String fetchRemoteVersion() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(remoteUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "Java-UpdateChecker");
        try {
            if (conn.getResponseCode() != 200)
                throw new IOException("HTTP " + conn.getResponseCode());
            StringBuilder content = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) content.append(line).append("\n");
            }
            String pattern = "\"" + pluginName + "\"\\s*=\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(content.toString());
            if (m.find()) return m.group(1);
            pattern = pluginName + "\\s*=\\s*([^,\\s]+)";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(content.toString());
            if (m.find()) return m.group(1);
            throw new IOException("Version not found for: " + pluginName);
        } finally {
            conn.disconnect();
        }
    }

    private boolean isNewerVersion(String latest, String current) {
        String l = latest.replaceAll("^[vV]", "");
        String c = current.replaceAll("^[vV]", "");
        String[] lp = l.split("\\."), cp = c.split("\\.");
        int max = Math.max(lp.length, cp.length);
        for (int i = 0; i < max; i++) {
            int lv = i < lp.length ? parseIntSafe(lp[i]) : 0;
            int cv = i < cp.length ? parseIntSafe(cp[i]) : 0;
            if (lv != cv) return lv > cv;
        }
        return false;
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}