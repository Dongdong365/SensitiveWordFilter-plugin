package cn.dongdong365.rw.sensitivewordfilter;

import net.rwhps.server.util.file.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件配置，使用 JSON 存储。
 */
public class FilterConfig {
    private final FileUtils file;

    public String filterMode = "permissive";          // permissive(替换) / enforcing(整句***)
    public String onViolationAction = "block";        // block(拦截并私聊) / censor(***化后发送)
    public boolean checkPlayerName = true;
    public boolean exemptAdmins = true;
    public List<String> exemptPlayers = new ArrayList<>(); // 可填玩家名或 UUID
    public boolean logToConsole = true;
    public String playerNameKickMessage = "你的玩家名包含违禁词，请修改后重进。";
    public int autoResetHours = 0;
    public final List<Threshold> thresholds = new ArrayList<>();

    public FilterConfig(FileUtils file) {
        this.file = file;
        load();
    }

    public void load() {
        if (!file.exists() || file.length() < 2) {
            writeDefault();
        }
        String text = file.readFileStringData();
        JSONObject json;
        try {
            json = new JSONObject(text);
        } catch (Exception e) {
            writeDefault();
            json = new JSONObject(file.readFileStringData());
        }

        filterMode = json.optString("filterMode", filterMode);
        onViolationAction = json.optString("onViolationAction", onViolationAction);
        checkPlayerName = json.optBoolean("checkPlayerName", checkPlayerName);
        exemptAdmins = json.optBoolean("exemptAdmins", exemptAdmins);
        exemptPlayers = readStringList(json, "exemptPlayers");
        logToConsole = json.optBoolean("logToConsole", logToConsole);
        playerNameKickMessage = json.optString("playerNameKickMessage", playerNameKickMessage);
        autoResetHours = json.optInt("autoResetHours", autoResetHours);

        thresholds.clear();
        JSONArray arr = json.optJSONArray("thresholds");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.optJSONObject(i);
                if (t == null) continue;
                Threshold th = new Threshold();
                th.count = t.optInt("count", i + 1);
                th.action = t.optString("action", "warn").toUpperCase();
                th.durationMinutes = t.optInt("durationMinutes", 0);
                th.message = t.optString("message", "");
                thresholds.add(th);
            }
        }
        if (thresholds.isEmpty()) {
            fillDefaultThresholds();
        }
        Collections.sort(thresholds, (a, b) -> Integer.compare(a.count, b.count));
    }

    private void writeDefault() {
        fillDefaultThresholds();
        JSONObject json = new JSONObject();
        json.put("filterMode", filterMode);
        json.put("onViolationAction", onViolationAction);
        json.put("checkPlayerName", checkPlayerName);
        json.put("exemptAdmins", exemptAdmins);
        json.put("exemptPlayers", new JSONArray(exemptPlayers));
        json.put("logToConsole", logToConsole);
        json.put("playerNameKickMessage", playerNameKickMessage);
        json.put("autoResetHours", autoResetHours);

        JSONArray arr = new JSONArray();
        for (Threshold th : thresholds) {
            JSONObject t = new JSONObject();
            t.put("count", th.count);
            t.put("action", th.action.toLowerCase());
            t.put("durationMinutes", th.durationMinutes);
            t.put("message", th.message);
            arr.put(t);
        }
        json.put("thresholds", arr);
        file.writeFile(json.toString(2), false);
    }

    private void fillDefaultThresholds() {
        thresholds.clear();
        thresholds.add(new Threshold(1, "WARN", 0, "[系统] 你的消息包含违禁词（第{count}次），请文明发言。"));
        thresholds.add(new Threshold(3, "KICK", 0, "因多次发送违禁词（第{count}次），你已被踢出房间。"));
        thresholds.add(new Threshold(5, "BAN", 60, "因多次发送违禁词（第{count}次），你已被封禁{duration}。"));
        thresholds.add(new Threshold(8, "BAN", -1, "因多次发送违禁词（第{count}次），你已被永久封禁。"));
    }

    private List<String> readStringList(JSONObject json, String key) {
        List<String> list = new ArrayList<>();
        JSONArray arr = json.optJSONArray(key);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.optString(i, ""));
            }
        }
        return list;
    }

    public void save() {
        JSONObject json = new JSONObject();
        json.put("filterMode", filterMode);
        json.put("onViolationAction", onViolationAction);
        json.put("checkPlayerName", checkPlayerName);
        json.put("exemptAdmins", exemptAdmins);
        json.put("exemptPlayers", new JSONArray(exemptPlayers));
        json.put("logToConsole", logToConsole);
        json.put("playerNameKickMessage", playerNameKickMessage);
        json.put("autoResetHours", autoResetHours);

        JSONArray arr = new JSONArray();
        for (Threshold th : thresholds) {
            JSONObject t = new JSONObject();
            t.put("count", th.count);
            t.put("action", th.action.toLowerCase());
            t.put("durationMinutes", th.durationMinutes);
            t.put("message", th.message);
            arr.put(t);
        }
        json.put("thresholds", arr);
        file.writeFile(json.toString(2), false);
    }

    /**
     * 根据累计违禁次数返回应执行的惩罚档位。
     */
    public Threshold getThresholdFor(int count) {
        Threshold result = null;
        for (Threshold th : thresholds) {
            if (count >= th.count) {
                result = th;
            } else {
                break;
            }
        }
        return result != null ? result : thresholds.get(0);
    }

    public boolean isExempt(String name, String uuid, boolean admin) {
        if (admin && exemptAdmins) return true;
        if (name == null) name = "";
        if (uuid == null) uuid = "";
        for (String s : exemptPlayers) {
            if (s == null || s.isEmpty()) continue;
            if (s.equalsIgnoreCase(name) || s.equalsIgnoreCase(uuid)) return true;
        }
        return false;
    }

    public static class Threshold {
        public int count;
        public String action; // WARN / KICK / BAN
        public int durationMinutes; // -1 永久，仅 BAN 有效
        public String message;

        public Threshold() {}

        public Threshold(int count, String action, int durationMinutes, String message) {
            this.count = count;
            this.action = action;
            this.durationMinutes = durationMinutes;
            this.message = message;
        }
    }
}
