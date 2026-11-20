package org.example.rewards;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static org.example.rewards.Models.*;

@Service
class Users {
    private final JdbcClient db;
    private final PasswordEncoder encoder;

    Users(JdbcClient db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

    UserProfile create(CreateUser input) {
        return db.sql("""
                INSERT INTO app_user (name, email, password_hash) VALUES (?, ?, ?)
                ON CONFLICT (email) DO NOTHING RETURNING id, name, email
                """)
                .params(input.name().strip(), input.email().toLowerCase(Locale.ROOT),
                        encoder.encode(input.password()))
                .query(UserProfile.class).optional()
                .orElseThrow(() -> ApiException.conflict("EMAIL_EXISTS", "Email is already registered."));
    }

    UserProfile get(long id) {
        return db.sql("SELECT id, name, email FROM app_user WHERE id = ?").param(id)
                .query(UserProfile.class).optional().orElseThrow(() -> ApiException.missing("USER"));
    }

    UserProfile accessibleProfile(long id, Authentication caller) {
        if (caller.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return get(id);
        return db.sql("SELECT id, name, email FROM app_user WHERE id = ? AND email = ?")
                .params(id, caller.getName()).query(UserProfile.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only your own profile is accessible."));
    }
}
