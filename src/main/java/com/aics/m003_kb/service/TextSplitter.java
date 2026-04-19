package com.aics.m003_kb.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 简化的段落 + 长度切片：~500 tokens, overlap 50 (approx by chars, 1 zh char ≈ 1 token).
 */
@Component
public class TextSplitter {

    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP = 50;

    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] paragraphs = text.split("\n\\s*\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (String p : paragraphs) {
            p = p.trim();
            if (p.isEmpty()) continue;

            if (cur.length() + p.length() + 1 <= CHUNK_SIZE) {
                if (cur.length() > 0) cur.append("\n");
                cur.append(p);
            } else {
                if (cur.length() > 0) {
                    chunks.add(cur.toString());
                    String overlap = cur.length() > OVERLAP ? cur.substring(cur.length() - OVERLAP) : cur.toString();
                    cur = new StringBuilder(overlap).append("\n");
                }
                if (p.length() > CHUNK_SIZE) {
                    int idx = 0;
                    while (idx < p.length()) {
                        int end = Math.min(idx + CHUNK_SIZE, p.length());
                        chunks.add(p.substring(idx, end));
                        idx += CHUNK_SIZE - OVERLAP;
                    }
                    cur = new StringBuilder();
                } else {
                    cur.append(p);
                }
            }
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        return chunks;
    }
}
