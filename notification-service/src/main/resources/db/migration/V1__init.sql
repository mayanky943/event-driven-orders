CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_id VARCHAR(128) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    type VARCHAR(64) NOT NULL,
    message VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notification_order ON notifications(order_id);

CREATE TABLE poison_messages (
    id UUID PRIMARY KEY,
    original_topic VARCHAR(128) NOT NULL,
    partition INT NOT NULL,
    offset_value BIGINT NOT NULL,
    message_key VARCHAR(256),
    payload TEXT NOT NULL,
    exception_message TEXT,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(published, created_at);
