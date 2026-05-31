CREATE TABLE rate_limit_rules (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        VARCHAR(255),
    tier           VARCHAR(50)  NOT NULL,
    action         VARCHAR(255),
    request_limit  INTEGER      NOT NULL,
    window_seconds INTEGER      NOT NULL,
    algorithm_type VARCHAR(50)  NOT NULL DEFAULT 'sliding_window',
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tier_action    ON rate_limit_rules (tier, action);
CREATE INDEX idx_user_id_action ON rate_limit_rules (user_id, action);

-- Default tier-wide rules (user_id = null, action = null means applies to everyone in that tier)
INSERT INTO rate_limit_rules (user_id, tier, action, request_limit, window_seconds, algorithm_type) VALUES
(NULL, 'FREE',     NULL, 100,          60, 'sliding_window'),
(NULL, 'PRO',      NULL, 1000,         60, 'sliding_window'),
(NULL, 'INTERNAL', NULL, 2147483647,   60, 'token_bucket');
