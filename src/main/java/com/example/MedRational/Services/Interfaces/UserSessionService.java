package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.Entities.User;

/**
 * Keeps devices signed in. Sign-in returns a short-lived JWT plus a refresh token; the app trades
 * the refresh token for a new pair when the JWT expires. Each renewal replaces the device's token
 * and gives it another full lifetime, so only a device unused for that long has to sign in again.
 */
public interface UserSessionService {

    /** A new session for this device. Returns the raw refresh token, which is never stored. */
    String start(User user);

    /**
     * Replaces the session's token and extends it. Throws BadCredentialsException (401) when the
     * token is unknown, already replaced or expired.
     */
    Renewed renew(String refreshToken);

    /** Signs this device out. Unknown tokens are ignored. */
    void end(String refreshToken);

    /** Signs every device of this user out, e.g. after a password change. */
    void endAll(User user);

    /** Removes sessions past their expiry. Returns how many. */
    int deleteExpired();

    record Renewed(User user, String refreshToken) {
    }
}
