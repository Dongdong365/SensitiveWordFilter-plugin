package cn.dongdong365.rw.sensitivewordfilter;

import net.rwhps.server.util.file.FileUtils;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 玩家违禁记录，按 UUID(connectHexID) 永久保存。
 */
public class ViolationData {
    private final FileUtils file;
    private final Map<String, PlayerRecord> records = new HashMap<>();

    public ViolationData(FileUtils file) {
        this.file = file;
        load();
    }

    public void load() {
        records.clear();
        if (!file.exists() || file.length() < 2) {
            return;
        }
        String text = file.readFileStringData();
        if (text == null || text.trim().isEmpty()) return;
        try {
            JSONObject json = new JSONObject(text);
            Iterator<String> it = json.keys();
            while (it.hasNext()) {
                String uuid = it.next();
                JSONObject o = json.optJSONObject(uuid);
                if (o == null) continue;
                PlayerRecord r = new PlayerRecord();
                r.uuid = uuid;
                r.count = o.optInt("count", 0);
                r.lastTime = o.optLong("lastTime", 0);
                r.banUntil = o.optLong("banUntil", 0);
                r.muteUntil = o.optLong("muteUntil", 0);
                records.put(uuid, r);
            }
        } catch (Exception ignored) {}
    }

    public void save() {
        JSONObject json = new JSONObject();
        for (PlayerRecord r : records.values()) {
            JSONObject o = new JSONObject();
            o.put("count", r.count);
            o.put("lastTime", r.lastTime);
            o.put("banUntil", r.banUntil);
            o.put("muteUntil", r.muteUntil);
            json.put(r.uuid, o);
        }
        file.writeFile(json.toString(2), false);
    }

    public int increment(String uuid) {
        PlayerRecord r = records.computeIfAbsent(uuid, k -> {
            PlayerRecord nr = new PlayerRecord();
            nr.uuid = k;
            return nr;
        });
        r.count++;
        r.lastTime = System.currentTimeMillis();
        save();
        return r.count;
    }

    public int getCount(String uuid) {
        PlayerRecord r = records.get(uuid);
        return r == null ? 0 : r.count;
    }

    public long getBanUntil(String uuid) {
        PlayerRecord r = records.get(uuid);
        return r == null ? 0 : r.banUntil;
    }

    public void setBanUntil(String uuid, long until) {
        PlayerRecord r = records.computeIfAbsent(uuid, k -> {
            PlayerRecord nr = new PlayerRecord();
            nr.uuid = k;
            return nr;
        });
        r.banUntil = until;
        save();
    }

    public long getMuteUntil(String uuid) {
        PlayerRecord r = records.get(uuid);
        return r == null ? 0 : r.muteUntil;
    }

    public void setMuteUntil(String uuid, long until) {
        PlayerRecord r = records.computeIfAbsent(uuid, k -> {
            PlayerRecord nr = new PlayerRecord();
            nr.uuid = k;
            return nr;
        });
        r.muteUntil = until;
        save();
    }

    public void clearMute(String uuid) {
        PlayerRecord r = records.get(uuid);
        if (r != null) {
            r.muteUntil = 0;
            save();
        }
    }

    public void clear(String uuid) {
        records.remove(uuid);
        save();
    }

    public void clearAll() {
        records.clear();
        save();
    }

    public Map<String, PlayerRecord> getAll() {
        return new HashMap<>(records);
    }

    public static class PlayerRecord {
        public String uuid;
        public int count = 0;
        public long lastTime = 0;
        public long banUntil = 0;   // -1 永久，0 未封禁，>0 解封时间戳
        public long muteUntil = 0;  // 0 未禁言，>0 解禁时间戳
    }
}
