CREATE TABLE stock_items (
    sku VARCHAR(64) PRIMARY KEY,
    available INT NOT NULL,
    reserved INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_reservation_order ON reservations(order_id);
CREATE INDEX idx_reservation_status ON reservations(status);

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

-- Seed some demo stock
INSERT INTO stock_items(sku, available, reserved) VALUES
    ('SKU-A', 100, 0),
    ('SKU-B', 50,  0),
    ('SKU-C', 10,  0),
    ('SKU-OOS', 0, 0);
