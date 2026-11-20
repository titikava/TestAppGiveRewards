package org.example.rewards;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.example.rewards.Models.*;

@Service
class Grants {
    private final JdbcClient db;
    private final Users users;
    private final Rewards rewards;

    Grants(JdbcClient db, Users users, Rewards rewards) {
        this.db = db;
        this.users = users;
        this.rewards = rewards;
    }

    @Transactional
    public Grant issue(UUID key, IssueReward input) {
        ApiRequestLog.field("userId", input.userId());
        ApiRequestLog.field("rewardId", input.rewardId());
        // All writers take the key lock before the reward lock, including concurrent retries.
        db.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))")
                .param(key).query((rs, i) -> true).single();
        var previous = db.sql("SELECT * FROM grant_details WHERE idempotency_key = ?")
                .param(key).query(Grant.class).optional();
        if (previous.isPresent()) {
            var grant = previous.get();
            if (grant.rewardId() != input.rewardId() || grant.userId() != input.userId()) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Key was used with a different request.");
            }
            ApiRequestLog.field("replayed", true);
            return grant.creationReceipt();
        }

        users.get(input.userId());
        var reward = rewards.lock(input.rewardId());
        if (db.sql("SELECT EXISTS (SELECT 1 FROM reward_grant WHERE user_id = ? AND reward_id = ?)")
                .params(input.userId(), reward.id()).query(Boolean.class).single()) {
            throw ApiException.conflict("ALREADY_GRANTED", "User has already received this reward.");
        }
        if (!reward.active()) throw ApiException.conflict("REWARD_INACTIVE", "Reward is inactive.");
        if (reward.issuanceLimit() != null && rewards.issuedCount(reward.id()) >= reward.issuanceLimit()) {
            throw ApiException.conflict("LIMIT_REACHED", "Issuance limit has been reached.");
        }
        UUID id = UUID.randomUUID();
        db.sql("""
                INSERT INTO reward_grant (id, user_id, reward_id, reward_code, reward_name,
                    issued_at, expires_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, statement_timestamp(),
                    statement_timestamp() + make_interval(secs => ?), ?)
                """).params(id, input.userId(), reward.id(), reward.code(), reward.name(), reward.validitySeconds(), key)
                .update();
        return get(id).creationReceipt();
    }

    @Transactional
    public Grant use(long userId, UUID id) {
        db.sql("SELECT id FROM reward_grant WHERE id = ? AND user_id = ? FOR UPDATE").params(id, userId)
                .query(UUID.class).optional().orElseThrow(() -> ApiException.missing("GRANT"));
        // Start a fresh statement after acquiring the lock so waiting never extends the lifetime.
        int changed = db.sql("""
                UPDATE reward_grant SET used_at = statement_timestamp()
                WHERE id = ? AND used_at IS NULL AND expires_at > statement_timestamp()
                """).param(id).update();
        var grant = get(id);
        ApiRequestLog.grant(grant);
        if (changed == 0) {
            throw ApiException.conflict("GRANT_" + grant.status().name(), "Grant is " + grant.status().name() + ".");
        }
        return grant;
    }

    private Grant get(UUID id) {
        return db.sql("SELECT * FROM grant_details WHERE id = ?").param(id).query(Grant.class).single();
    }

    Page<Grant> byUser(long userId, GrantStatus status, int page, int size) {
        return list("user_id", userId, status, page, size);
    }

    Page<Grant> byReward(long rewardId, GrantStatus status, int page, int size) {
        rewards.get(rewardId);
        return list("reward_id", rewardId, status, page, size);
    }

    private Page<Grant> list(String column, long id, GrantStatus status, int page, int size) {
        var rows = db.sql("SELECT * FROM grant_details WHERE " + column + " = :id " + """
                AND (CAST(:status AS text) IS NULL OR status = :status)
                ORDER BY issued_at DESC, id LIMIT :limit OFFSET :offset
                """).param("id", id).param("status", status == null ? null : status.name())
                .param("limit", size + 1).param("offset", (long) page * size).query(Grant.class).list();
        return Page.of(rows, page, size);
    }
}
