ALTER TABLE chat_messages
    DROP CONSTRAINT IF EXISTS chat_messages_type_check;

ALTER TABLE chat_messages
    ADD CONSTRAINT chat_messages_type_check
        CHECK (type IN ('TEXT', 'ATTACHMENT', 'MIXED', 'SYSTEM'));
