package org.example.rewards;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import static org.example.rewards.Models.*;

@RestController
@RequestMapping("/api/v1")
public class RewardsApi {
    private final Users users;
    private final Rewards rewards;
    private final Grants grants;

    RewardsApi(Users users, Rewards rewards, Grants grants) {
        this.users = users;
        this.rewards = rewards;
        this.grants = grants;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    UserProfile createUser(@Valid @RequestBody CreateUser body) {
        var profile = users.create(body);
        ApiRequestLog.field("userId", profile.id());
        return profile;
    }

    @GetMapping("/users/{userId}")
    UserProfile getUser(@PathVariable @Min(MIN_USER_ID) @Max(MAX_USER_ID) long userId, Authentication caller) {
        return users.accessibleProfile(userId, caller);
    }

    @PostMapping("/rewards")
    @ResponseStatus(HttpStatus.CREATED)
    Reward createReward(@Valid @RequestBody CreateReward body) {
        var reward = rewards.create(body);
        ApiRequestLog.field("rewardId", reward.id());
        return reward;
    }

    @PutMapping("/rewards/{rewardId}")
    Reward updateReward(@PathVariable @Positive long rewardId, @Valid @RequestBody UpdateReward body) {
        return rewards.update(rewardId, body);
    }

    @GetMapping("/rewards/{rewardId}")
    Reward getReward(@PathVariable @Positive long rewardId) { return rewards.get(rewardId); }

    @GetMapping("/rewards")
    Page<Reward> listRewards(@RequestParam(required = false) Boolean active,
                             @RequestParam(defaultValue = "0") @Min(0) int page,
                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return rewards.list(active, page, size);
    }

    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.CREATED)
    Grant issueReward(@RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody IssueReward body) {
        var receipt = grants.issue(key, body);
        ApiRequestLog.grant(receipt);
        return receipt;
    }

    @GetMapping("/users/{userId}/grants")
    Page<Grant> listUserGrants(@PathVariable @Min(MIN_USER_ID) @Max(MAX_USER_ID) long userId, Authentication caller,
                               @RequestParam(required = false) GrantStatus status,
                               @RequestParam(defaultValue = "0") @Min(0) int page,
                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        users.accessibleProfile(userId, caller);
        return grants.byUser(userId, status, page, size);
    }

    @GetMapping("/rewards/{rewardId}/grants")
    Page<Grant> listRewardGrants(@PathVariable @Positive long rewardId,
                                 @RequestParam(required = false) GrantStatus status,
                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return grants.byReward(rewardId, status, page, size);
    }

    @PostMapping("/users/{userId}/grants/{grantId}/use")
    Grant useGrant(@PathVariable @Min(MIN_USER_ID) @Max(MAX_USER_ID) long userId, @PathVariable UUID grantId, Authentication caller) {
        users.accessibleProfile(userId, caller);
        return grants.use(userId, grantId);
    }
}
