package com.aics.infra.wecom;

import lombok.Data;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component
public class WeComMessageParser {

    /** Extract echostr only (for URL validation). */
    public String parseEncrypt(String xml) {
        return tag(xml, "Encrypt");
    }

    public Parsed parse(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = (Element) doc.getElementsByTagName("xml").item(0);

            Parsed p = new Parsed();
            p.msgId = textOf(root, "MsgId");
            p.msgType = textOf(root, "MsgType");
            p.content = textOf(root, "Content");
            p.fromUsername = textOf(root, "FromUserName");
            p.chatId = textOf(root, "ChatId"); // 群ID
            p.agentId = textOf(root, "AgentID");
            p.createTime = parseLong(textOf(root, "CreateTime"));

            // 机器人群聊专用：MentionedList 下的 Item（用户 userid）
            NodeList mentions = root.getElementsByTagName("MentionedList");
            if (mentions.getLength() > 0) {
                Element ml = (Element) mentions.item(0);
                NodeList items = ml.getElementsByTagName("Item");
                for (int i = 0; i < items.getLength(); i++) {
                    p.mentionedList.add(items.item(i).getTextContent().trim());
                }
            }
            return p;
        } catch (Exception e) {
            throw new RuntimeException("parse wecom xml failed", e);
        }
    }

    private static String textOf(Element root, String tag) {
        NodeList nl = root.getElementsByTagName(tag);
        if (nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return n.getTextContent() == null ? null : n.getTextContent().trim();
    }

    private static long parseLong(String s) {
        try { return s == null ? 0L : Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    private static String tag(String xml, String name) {
        int i = xml.indexOf("<" + name);
        if (i < 0) return null;
        int s = xml.indexOf("<![CDATA[", i);
        if (s >= 0) {
            int e = xml.indexOf("]]>", s);
            return xml.substring(s + 9, e);
        }
        int gt = xml.indexOf('>', i) + 1;
        int close = xml.indexOf("</" + name, gt);
        return xml.substring(gt, close).trim();
    }

    @Data
    public static class Parsed {
        private String msgId;
        private String msgType;
        private String content;
        private String fromUsername;
        private String chatId;
        private String agentId;
        private long createTime;
        private List<String> mentionedList = new ArrayList<>();
    }
}
