package cn.dongdong365.rw.sensitivewordfilter;

import net.rwhps.server.core.thread.Threads;
import net.rwhps.server.util.Time;
import net.rwhps.server.util.file.FileUtils;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 玩家违禁记录，按 UUID(connectHexID) 永久保存。
 * 使用 ConcurrentHashMap 保证并发安全，并通过延迟写入避免每条消息都触发 IO。
 */
public class ViolationData {
    private final FileUtils file;
    private final Map<String, PlayerRecord> records = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

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
                r.playerName = o.optString("playername", null);
                records.put(uuid, r);
            }
        } catch (Exception ignored) {}
    }

    public void save() {
        dirty.set(false);
        JSONObject json = new JSONObject();
        for (PlayerRecord r : records.values()) {
            JSONObject o = new JSONObject();
            o.put("count", r.count);
            o.put("lastTime", r.lastTime);
            o.put("banUntil", r.banUntil);
            o.put("muteUntil", r.muteUntil);
            if (r.playerName != null) o.put("playername", r.playerName);
            json.put(r.uuid, o);
        }
        file.writeFile(json.toString(2), false);
    }

    private void markDirty() {
        dirty.set(true);
        scheduleSave();
    }

    private void scheduleSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            Threads.closeTimeTask("SensitiveWordFilter-Save", "SensitiveWordFilter");
            Threads.newTimedTask(
                    "SensitiveWordFilter-Save",
                    "SensitiveWordFilter",
                    "保存违禁记录",
                    5,
                    Integer.MAX_VALUE,
                    TimeUnit.SECONDS,
                    () -> {
                        save();
                        saveScheduled.set(false);
                        if (dirty.get()) {
                            scheduleSave();
                        }
                    }
            );
        }
    }

    public int increment(String uuid, String name) {
        PlayerRecord r = records.computeIfAbsent(uuid, k -> {
            PlayerRecord nr = new PlayerRecord();
            nr.uuid = k;
            return nr;
        });
        r.count++;
        r.lastTime = Time.concurrentMillis();
        if (name != null && !name.isEmpty()) {
            r.playerName = name;
        }
        markDirty();
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
        markDirty();
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
        markDirty();
    }

    public void clearMute(String uuid) {
        PlayerRecord r = records.get(uuid);
        if (r != null) {
            r.muteUntil = 0;
            markDirty();
        }
    }

    public void clear(String uuid) {
        records.remove(uuid);
        markDirty();
    }

    public void clearAll() {
        records.clear();
        markDirty();
    }

    public PlayerRecord getRecord(String uuid) {
        return records.get(uuid);
    }

    public PlayerRecord findByName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (PlayerRecord r : records.values()) {
            if (r.playerName != null && r.playerName.equalsIgnoreCase(name)) {
                return r;
            }
        }
        return null;
    }

    public void ensureName(String uuid, String name) {
        if (name == null || name.isEmpty()) return;
        PlayerRecord r = records.get(uuid);
        if (r == null) {
            r = new PlayerRecord();
            r.uuid = uuid;
            r.playerName = name;
            records.put(uuid, r);
            markDirty();
        } else if (r.playerName == null || !r.playerName.equalsIgnoreCase(name)) {
            r.playerName = name;
            markDirty();
        }
    }

    public Map<String, PlayerRecord> getAll() {
        return new ConcurrentHashMap<>(records);
    }

    public static class PlayerRecord {
        public String uuid;
        public String playerName;
        public int count = 0;
        public long lastTime = 0;
        public long banUntil = 0;   // -1 永久，0 未封禁，>0 解封时间戳
        public long muteUntil = 0;  // 0 未禁言，>0 解禁时间戳
    }
}
