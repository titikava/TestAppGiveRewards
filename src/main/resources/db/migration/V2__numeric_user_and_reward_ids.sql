DROP VIEW grant_details;

-- Keep the old keys on migrated profiles and rewards for looking up saved UUIDs.
ALTER TABLE app_user RENAME COLUMN id TO legacy_id;
ALTER TABLE app_user ADD COLUMN id bigint GENERATED ALWAYS AS IDENTITY
    (START WITH 100000 MINVALUE 100000 MAXVALUE 999999999999)
    CHECK (id BETWEEN 100000 AND 999999999999);

ALTER TABLE reward RENAME COLUMN id TO legacy_id;
ALTER TABLE reward ADD COLUMN id bigint CHECK (id > 0);
-- V1 had no creation timestamp. Assign its rewards numbers in stable code order.
WITH numbered AS (
    SELECT legacy_id, row_number() OVER (ORDER BY code) AS id FROM reward
)
UPDATE reward r SET id = n.id FROM numbered n WHERE r.legacy_id = n.legacy_id;

ALTER TABLE reward_grant ADD COLUMN numeric_user_id bigint, ADD COLUMN numeric_reward_id bigint;
UPDATE reward_grant g SET numeric_user_id = u.id, numeric_reward_id = r.id
FROM app_user u, reward r
WHERE g.user_id = u.legacy_id AND g.reward_id = r.legacy_id;

ALTER TABLE reward_grant
    ALTER COLUMN numeric_user_id SET NOT NULL,
    ALTER COLUMN numeric_reward_id SET NOT NULL,
    DROP COLUMN user_id,
    DROP COLUMN reward_id;
ALTER TABLE reward_grant RENAME COLUMN numeric_user_id TO user_id;
ALTER TABLE reward_grant RENAME COLUMN numeric_reward_id TO reward_id;

ALTER TABLE app_user DROP CONSTRAINT app_user_pkey;
ALTER TABLE app_user ALTER COLUMN legacy_id DROP NOT NULL, ADD UNIQUE (legacy_id), ADD PRIMARY KEY (id);
ALTER TABLE reward DROP CONSTRAINT reward_pkey;
ALTER TABLE reward ALTER COLUMN legacy_id DROP NOT NULL, ADD UNIQUE (legacy_id), ADD PRIMARY KEY (id);

ALTER TABLE reward_grant
    ADD FOREIGN KEY (user_id) REFERENCES app_user(id),
    ADD FOREIGN KEY (reward_id) REFERENCES reward(id),
    ADD UNIQUE (user_id, reward_id);
CREATE INDEX grants_by_user ON reward_grant(user_id, issued_at DESC, id);
CREATE INDEX grants_by_reward ON reward_grant(reward_id, issued_at DESC, id);

-- The counter advances in the reward creation transaction, including on concurrent requests.
-- Rolling back a failed creation also rolls back its number.
CREATE TABLE reward_id_counter (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    last_id bigint NOT NULL CHECK (last_id >= 0)
);
INSERT INTO reward_id_counter (last_id) SELECT coalesce(max(id), 0) FROM reward;

CREATE VIEW grant_details AS
SELECT g.*, CASE WHEN used_at IS NOT NULL THEN 'USED'
                 WHEN expires_at <= statement_timestamp() THEN 'EXPIRED'
                 ELSE 'ACTIVE' END AS status
FROM reward_grant g;
