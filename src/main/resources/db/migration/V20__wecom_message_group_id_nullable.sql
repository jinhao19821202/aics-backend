-- P005 F002 补丁：支持直聊（1:1）消息落库审计。
-- 企微机器人直接对话时回调 payload 不含 ChatId，原 NOT NULL 约束导致此类消息被 IGNORE，
-- 违反 "所有已验签消息都应审计留痕" 的原则。此处放开约束；上层过滤通过 msg_type / chat_id IS NULL 实现。
ALTER TABLE wecom_message ALTER COLUMN group_id DROP NOT NULL;
