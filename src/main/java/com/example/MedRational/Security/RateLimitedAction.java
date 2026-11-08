package com.example.MedRational.Security;

import java.util.function.Function;

// What a rate-limited request does; each action has its own counters and limits
public enum RateLimitedAction {

    // Signing in with a password
    LOGIN(RateLimitProperties::getLogin),
    // Anything that emails a code (sign-up, forgot password, profile and deletion codes)
    SEND_CODE(RateLimitProperties::getSendCode),
    // Anything that checks an emailed code
    VERIFY_CODE(RateLimitProperties::getVerifyCode);

    private final Function<RateLimitProperties, RateLimitProperties.Action> limits;

    RateLimitedAction(Function<RateLimitProperties, RateLimitProperties.Action> limits) {
        this.limits = limits;
    }

    RateLimitProperties.Action limitsIn(RateLimitProperties properties) {
        return limits.apply(properties);
    }
}
