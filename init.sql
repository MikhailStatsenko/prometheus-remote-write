CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE IF NOT EXISTS message_log
(
    id          UUID        NOT NULL,
    message     TEXT        NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    hostname    TEXT        NOT NULL,
    PRIMARY KEY (id, received_at)
);

SELECT create_hypertable('message_log', 'received_at', if_not_exists => TRUE);