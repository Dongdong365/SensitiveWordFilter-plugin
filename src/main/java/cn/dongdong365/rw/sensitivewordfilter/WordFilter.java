package cn.dongdong365.rw.sensitivewordfilter;

import net.rwhps.server.game.player.PlayerHess;
import net.rwhps.server.util.file.FileUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 词库加载与过滤核心。
 * 使用 AC 自动机实现多模式匹配，避免词库大时对每条消息做 O(N*L) 的重复扫描。
 */
public class WordFilter {
    private final FilterConfig config;
    private final FileUtils folder;
    private final CopyOnWriteArrayList<String> sensitiveWords = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> properNouns = new CopyOnWriteArrayList<>();
    private volatile AcAutomaton automaton = new AcAutomaton(Collections.emptyList());

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

        rebuildAutomaton();
    }

    private void rebuildAutomaton() {
        automaton = new AcAutomaton(sensitiveWords);
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
        return config.isExempt(player.getName(), player.getConnectHexID(), player.isAdmin());
    }

    public boolean containsSensitive(String input) {
        if (input == null || input.isEmpty()) return false;
        return automaton.findFirst(input.toLowerCase()) >= 0;
    }

    /**
     * 过滤一条消息。
     * @return 包含是否命中、过滤后文本等信息
     */
    public FilterResult filter(String input) {
        if (input == null || input.isEmpty()) return new FilterResult(false, input);

        String text = input.toLowerCase();
        boolean[] protectedMask = buildProtectedMask(text);
        List<Match> matches = automaton.findAll(text);

        boolean hit = false;
        boolean[] starMask = new boolean[text.length()];
        for (Match m : matches) {
            if (overlapsProtected(protectedMask, m.start, m.end)) continue;
            hit = true;
            for (int i = m.start; i < m.end; i++) starMask[i] = true;
        }

        if (!hit) return new FilterResult(false, input);

        if ("enforcing".equalsIgnoreCase(config.filterMode)) {
            return new FilterResult(true, "***");
        }

        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            sb.append(starMask[i] ? '*' : input.charAt(i));
        }
        return new FilterResult(true, sb.toString());
    }

    private boolean[] buildProtectedMask(String text) {
        boolean[] mask = new boolean[text.length()];
        for (String noun : properNouns) {
            if (noun.isEmpty()) continue;
            String key = noun.toLowerCase();
            int from = 0;
            while (true) {
                int idx = text.indexOf(key, from);
                if (idx < 0) break;
                for (int i = idx; i < idx + key.length(); i++) mask[i] = true;
                from = idx + 1;
            }
        }
        return mask;
    }

    private boolean overlapsProtected(boolean[] mask, int start, int end) {
        for (int i = start; i < end; i++) {
            if (mask[i]) return true;
        }
        return false;
    }

    public void addSensitiveWord(String word) {
        if (word == null || word.trim().isEmpty()) return;
        String w = word.trim();
        if (sensitiveWords.contains(w)) return;
        sensitiveWords.add(w);
        folder.toFile("sensitive_words.txt").writeFile(w + "\n", true);
        rebuildAutomaton();
    }

    public void removeSensitiveWord(String word) {
        if (word == null) return;
        String w = word.trim();
        if (!sensitiveWords.remove(w)) return;
        rewriteWordFile();
        rebuildAutomaton();
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

    private static class Match {
        final int start;
        final int end;
        Match(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * 简单的 AC 自动机，支持大小写不敏感匹配。
     */
    private static class AcAutomaton {
        private final Node root = new Node();

        AcAutomaton(Collection<String> words) {
            for (String word : words) {
                if (word == null || word.isEmpty()) continue;
                insert(word.toLowerCase());
            }
            buildFailure();
        }

        private void insert(String word) {
            Node node = root;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                node = node.next.computeIfAbsent(c, k -> new Node());
            }
            node.outputLength = word.length();
        }

        private void buildFailure() {
            Queue<Node> queue = new ArrayDeque<>();
            for (Node child : root.next.values()) {
                child.fail = root;
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                Node current = queue.poll();
                for (Map.Entry<Character, Node> e : current.next.entrySet()) {
                    char c = e.getKey();
                    Node child = e.getValue();
                    Node fail = current.fail;
                    while (fail != null && !fail.next.containsKey(c)) {
                        fail = fail.fail;
                    }
                    child.fail = (fail == null) ? root : fail.next.get(c);
                    if (child.fail != null && child.fail.outputLength > 0 && child.outputLength == 0) {
                        child.outputLength = child.fail.outputLength;
                    }
                    queue.add(child);
                }
            }
        }

        /**
         * 返回第一个命中的结束位置，未命中返回 -1。
         */
        int findFirst(String text) {
            Node node = root;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                while (node != root && !node.next.containsKey(c)) {
                    node = node.fail;
                }
                node = node.next.getOrDefault(c, root);
                if (node.outputLength > 0) {
                    return i + 1;
                }
            }
            return -1;
        }

        /**
         * 返回所有命中的区间 [start, end)。
         */
        List<Match> findAll(String text) {
            List<Match> matches = new ArrayList<>();
            Node node = root;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                while (node != root && !node.next.containsKey(c)) {
                    node = node.fail;
                }
                node = node.next.getOrDefault(c, root);
                int len = node.outputLength;
                if (len > 0) {
                    int end = i + 1;
                    matches.add(new Match(end - len, end));
                }
            }
            return matches;
        }
    }

    private static class Node {
        final Map<Character, Node> next = new HashMap<>();
        Node fail;
        int outputLength; // 以该节点结尾的最长敏感词长度
    }
}
