ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS forwarded_from_message_id UUID;

CREATE TABLE IF NOT EXISTS room_pinned_messages (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    message_id UUID NOT NULL,
    pinned_by UUID NOT NULL,
    pinned_at TIMESTAMP NOT NULL,
    CONSTRAINT uniq_room_message_pin UNIQUE (room_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_pin_room_pinned_at
    ON room_pinned_messages (room_id, pinned_at);
