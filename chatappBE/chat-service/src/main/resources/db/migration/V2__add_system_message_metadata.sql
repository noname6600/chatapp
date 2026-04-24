ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS system_event_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS actor_user_id UUID,
    ADD COLUMN IF NOT EXISTS target_message_id UUID;
