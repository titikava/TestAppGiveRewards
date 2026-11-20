package org.example.rewards;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.example.rewards.Models.*;

@Service
class Rewards {
    private final JdbcClient db;

    Rewards(JdbcClient db) { this.db = db; }

    @Transactional
    public Reward create(CreateReward input) {
        return db.sql("""
                WITH next_id AS (
                    UPDATE reward_id_counter SET last_id = last_id + 1 RETURNING last_id
                )
                INSERT INTO reward (id, code, name, description, active, validity_seconds, issuance_limit)
                SELECT last_id, ?, ?, ?, ?, ?, ? FROM next_id
                ON CONFLICT (code) DO NOTHING RETURNING *
                """)
                .params(input.code(), input.name().strip(), input.description(),
                        input.active() == null || input.active(),
                        input.validitySeconds() == null ? 604800 : input.validitySeconds(), input.issuanceLimit())
                .query(Reward.class).optional()
                .orElseThrow(() -> ApiException.conflict("REWARD_CODE_EXISTS", "Reward code is already in use."));
    }

    Reward get(long id) { return find(id, false); }

    Reward lock(long id) { return find(id, true); }

    private Reward find(long id, boolean lock) {
        return db.sql("SELECT * FROM reward WHERE id = ?" + (lock ? " FOR UPDATE" : ""))
                .param(id).query(Reward.class).optional().orElseThrow(() -> ApiException.missing("REWARD"));
    }

    long issuedCount(long id) {
        return db.sql("SELECT count(*) FROM reward_grant WHERE reward_id = ?").param(id).query(Long.class).single();
    }

    @Transactional
    public Reward update(long id, UpdateReward input) {
        lock(id);
        if (input.issuanceLimit() != null && input.issuanceLimit() < issuedCount(id)) {
            throw ApiException.conflict("LIMIT_BELOW_ISSUED", "Limit is smaller than the number already issued.");
        }
        return db.sql("""
                UPDATE reward SET name = ?, description = ?, active = ?, validity_seconds = ?, issuance_limit = ?
                WHERE id = ? RETURNING *
                """)
                .params(input.name().strip(), input.description(), input.active(), input.validitySeconds(), input.issuanceLimit(), id)
                .query(Reward.class).single();
    }

    Page<Reward> list(Boolean active, int page, int size) {
        var rows = db.sql("""
                SELECT * FROM reward WHERE (CAST(:active AS boolean) IS NULL OR active = :active)
                ORDER BY code LIMIT :limit OFFSET :offset
                """).param("active", active).param("limit", size + 1).param("offset", (long) page * size)
                .query(Reward.class).list();
        return Page.of(rows, page, size);
    }
}
