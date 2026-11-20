CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL,
    email varchar(254) NOT NULL UNIQUE,
    password_hash text NOT NULL
);

CREATE TABLE reward (
    id uuid PRIMARY KEY,
    code varchar(64) NOT NULL UNIQUE,
    name varchar(120) NOT NULL,
    description varchar(1000),
    active boolean NOT NULL,
    validity_seconds integer NOT NULL CHECK (validity_seconds BETWEEN 1 AND 315360000),
    issuance_limit integer CHECK (issuance_limit > 0)
);

CREATE TABLE reward_grant (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user(id),
    reward_id uuid NOT NULL REFERENCES reward(id),
    reward_code varchar(64) NOT NULL,
    reward_name varchar(120) NOT NULL,
    issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > issued_at),
    used_at timestamptz CHECK (used_at >= issued_at AND used_at < expires_at),
    idempotency_key uuid NOT NULL UNIQUE,
    UNIQUE (user_id, reward_id)
);

CREATE INDEX grants_by_user ON reward_grant(user_id, issued_at DESC, id);
CREATE INDEX grants_by_reward ON reward_grant(reward_id, issued_at DESC, id);

CREATE VIEW grant_details AS
SELECT g.*, CASE WHEN used_at IS NOT NULL THEN 'USED'
                 WHEN expires_at <= statement_timestamp() THEN 'EXPIRED'
                 ELSE 'ACTIVE' END AS status
FROM reward_grant g;
