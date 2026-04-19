package com.aics.m005_admin.sensitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DFA 前缀树敏感词匹配器。线程安全：构造后只读。
 */
public class DfaFilter {

    public static class WordMeta {
        public final String word;
        public final String category;
        public final String level;
        public final String action;

        public WordMeta(String word, String category, String level, String action) {
            this.word = word;
            this.category = category;
            this.level = level;
            this.action = action;
        }
    }

    public static class Hit {
        public final int start;
        public final int end;
        public final WordMeta meta;

        public Hit(int start, int end, WordMeta meta) {
            this.start = start;
            this.end = end;
            this.meta = meta;
        }
    }

    private static class Node {
        final Map<Character, Node> children = new HashMap<>(4);
        WordMeta end;
    }

    private final Node root = new Node();

    public DfaFilter(List<SensitiveWord> words) {
        for (SensitiveWord w : words) {
            addWord(w);
        }
    }

    private void addWord(SensitiveWord w) {
        if (w.getWord() == null || w.getWord().isEmpty()) return;
        String lower = w.getWord().toLowerCase();
        Node cur = root;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            cur = cur.children.computeIfAbsent(c, k -> new Node());
        }
        cur.end = new WordMeta(w.getWord(), w.getCategory(), w.getLevel(), w.getAction());
    }

    /** 从头到尾最左最长匹配，允许全角半角大小写差异。 */
    public List<Hit> match(String text) {
        List<Hit> hits = new ArrayList<>();
        if (text == null || text.isEmpty()) return hits;
        String lower = text.toLowerCase();
        int n = lower.length();
        int i = 0;
        while (i < n) {
            Node cur = root;
            int j = i;
            int lastEnd = -1;
            WordMeta lastMeta = null;
            while (j < n) {
                Node next = cur.children.get(lower.charAt(j));
                if (next == null) break;
                j++;
                if (next.end != null) {
                    lastEnd = j;
                    lastMeta = next.end;
                }
                cur = next;
            }
            if (lastMeta != null) {
                hits.add(new Hit(i, lastEnd, lastMeta));
                i = lastEnd;
            } else {
                i++;
            }
        }
        return hits;
    }
}
