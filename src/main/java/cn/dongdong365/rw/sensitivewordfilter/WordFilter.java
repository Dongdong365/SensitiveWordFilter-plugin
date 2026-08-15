package cn.dongdong365.rw.sensitivewordfilter;

import net.rwhps.server.game.player.PlayerHess;
import net.rwhps.server.util.file.FileUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 词库加载与过滤核心。
 */
public class WordFilter {
    private final FilterConfig config;
    private final FileUtils folder;
    private final List<String> sensitiveWords = new ArrayList<>();
    private final List<String> properNouns = new ArrayList<>();

    public WordFilter(FileUtils folder, FilterConfig config) {
        this.folder = folder;
        this.config = config;
        reload();
    }

    public void reload() {
        releaseDefaultIfMissing("sensitive_words.txt", "/sensitive_words.txt");
        releaseDefaultIfMissing("proper_nouns.txt", "/proper_nouns.txt");

        sensitiveWords.clear();
        sensitiveWords.addAll(readLines(folder.toFile("sensitive_words.txt")));

        properNouns.clear();
        properNouns.addAll(readLines(folder.toFile("proper_nouns.txt")));
    }

    private void releaseDefaultIfMissing(String dataFileName, String resourcePath) {
        FileUtils target = folder.toFile(dataFileName);
        if (target.exists()) return;
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            target.writeFile(sb.toString(), false);
        } catch (Exception ignored) {}
    }

    private List<String> readLines(FileUtils file) {
        List<String> lines = new ArrayList<>();
        if (!file.exists()) return lines;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputsStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
        } catch (Exception ignored) {}
        return lines;
    }

    public int getSensitiveWordCount() {
        return sensitiveWords.size();
    }

    public boolean isExempt(PlayerHess player) {
        return config.isExempt(player.getName(), player.isAdmin());
    }

    public boolean containsSensitive(String input) {
        if (input == null || input.isEmpty()) return false;
        for (String word : sensitiveWords) {
            if (input.contains(word)) return true;
        }
        return false;
    }

    /**
     * 过滤一条消息。
     * @return 包含是否命中、过滤后文本等信息
     */
    public FilterResult filter(String input) {
        if (input == null || input.isEmpty()) return new FilterResult(false, input);

        String work = input;
        Map<String, String> placeholders = new HashMap<>();

        // 保护专有名词
        for (int i = 0; i < properNouns.size(); i++) {
            String noun = properNouns.get(i);
            if (noun.isEmpty()) continue;
            String ph = "@@PN" + i + "@@";
            placeholders.put(ph, noun);
            work = work.replace(noun, ph);
        }

        boolean hit = false;
        String result;
        if ("enforcing".equalsIgnoreCase(config.filterMode)) {
            for (String word : sensitiveWords) {
                if (word.isEmpty()) continue;
                if (work.contains(word)) {
                    hit = true;
                    break;
                }
            }
            result = hit ? "***" : work;
        } else {
            result = work;
            for (String word : sensitiveWords) {
                if (word.isEmpty()) continue;
                if (result.contains(word)) {
                    hit = true;
                    result = result.replace(word, repeat(word.length()));
                }
            }
        }

        // 还原专有名词
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }

        return new FilterResult(hit, result);
    }

    private String repeat(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append("*");
        return sb.toString();
    }

    public void addSensitiveWord(String word) {
        if (word == null || word.trim().isEmpty()) return;
        String w = word.trim();
        if (sensitiveWords.contains(w)) return;
        sensitiveWords.add(w);
        folder.toFile("sensitive_words.txt").writeFile(w + "\n", true);
    }

    public void removeSensitiveWord(String word) {
        if (word == null) return;
        String w = word.trim();
        sensitiveWords.remove(w);
        rewriteWordFile();
    }

    private void rewriteWordFile() {
        StringBuilder sb = new StringBuilder();
        for (String w : sensitiveWords) {
            sb.append(w).append("\n");
        }
        folder.toFile("sensitive_words.txt").writeFile(sb.toString(), false);
    }

    public static class FilterResult {
        public final boolean hit;
        public final String filtered;

        public FilterResult(boolean hit, String filtered) {
            this.hit = hit;
            this.filtered = filtered;
        }
    }
}
