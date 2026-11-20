package org.example.rewards;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class Models {
    static final long MIN_USER_ID = 100_000L;
    static final long MAX_USER_ID = 999_999_999_999L;

    private Models() {}

    public record CreateUser(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[^\\s@]+@[^\\s@]+") String email,
            @NotBlank @Size(min = 8, max = 128) String password) {}

    public record UserProfile(long id, String name, String email) {}

    public record CreateReward(
            @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            Boolean active,
            @Min(1) @Max(315360000) Integer validitySeconds,
            @Positive Integer issuanceLimit) {}

    public record UpdateReward(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull Boolean active,
            @NotNull @Min(1) @Max(315360000) Integer validitySeconds,
            @Positive Integer issuanceLimit) {}

    public record Reward(long id, String code, String name, String description,
                         boolean active, int validitySeconds, Integer issuanceLimit) {}

    public record IssueReward(@NotNull @Min(MIN_USER_ID) @Max(MAX_USER_ID) Long userId,
                              @NotNull @Positive Long rewardId) {}
    public enum GrantStatus { ACTIVE, USED, EXPIRED }

    public record Grant(UUID id, long userId, long rewardId, String rewardCode, String rewardName,
                        OffsetDateTime issuedAt, OffsetDateTime expiresAt, OffsetDateTime usedAt,
                        GrantStatus status) {
        Grant creationReceipt() {
            return new Grant(id, userId, rewardId, rewardCode, rewardName, issuedAt, expiresAt,
                    null, GrantStatus.ACTIVE);
        }
    }

    public record Page<T>(List<T> items, int page, int size, boolean hasNext) {
        static <T> Page<T> of(List<T> rows, int page, int size) {
            return new Page<>(rows.subList(0, Math.min(rows.size(), size)), page, size, rows.size() > size);
        }
    }

    public record ApiError(String code, String message) {}
}
