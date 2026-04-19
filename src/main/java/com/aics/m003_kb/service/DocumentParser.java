package com.aics.m003_kb.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class DocumentParser {

    public String parse(byte[] bytes, String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    PDFTextStripper s = new PDFTextStripper();
                    return s.getText(doc);
                }
            }
            if (lower.endsWith(".docx")) {
                try (InputStream in = new ByteArrayInputStream(bytes);
                     XWPFDocument doc = new XWPFDocument(in)) {
                    StringBuilder sb = new StringBuilder();
                    for (XWPFParagraph p : doc.getParagraphs()) {
                        sb.append(p.getText()).append("\n");
                    }
                    return sb.toString();
                }
            }
            if (lower.endsWith(".md") || lower.endsWith(".txt")) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            throw new IllegalArgumentException("unsupported file type: " + filename);
        } catch (Exception e) {
            throw new RuntimeException("parse failed: " + e.getMessage(), e);
        }
    }
}
