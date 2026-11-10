import http from 'k6/http';
import { BASE_URL, PASSWORD, USERS } from './config.js';

// Signs in each test account once, in setup(), and the virtual users share the tokens.
// Signing in per virtual user would hit the login rate limits (10 per email and 60 per IP per
// 15 min, by design) and measure BCrypt instead of browsing. Real students rarely sign in, since a
// session lasts 60 days.
//
// Access tokens last 1 hour and every profile is shorter. There is no renewal: /auth/refresh
// rotates the refresh token, so virtual users sharing an account would invalidate each other's.
export function signInAll() {
    if (USERS.length === 0 || !PASSWORD) {
        throw new Error('Set LT_USERS (comma-separated emails) and LT_PASSWORD to confirmed test accounts');
    }
    return USERS.map((email) => {
        const res = http.post(
            `${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password: PASSWORD }),
            { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
        );
        if (res.status !== 200) {
            throw new Error(`Sign-in failed for ${email}: HTTP ${res.status} ${res.body}`);
        }
        const body = res.json();
        if (body.role && /ADMIN/.test(body.role)) {
            throw new Error(`${email} is an admin: use student accounts, as real traffic is students`);
        }
        return { userId: body.userId, token: body.token };
    });
}

export function authParams(session, name) {
    return { headers: { Authorization: `Bearer ${session.token}` }, tags: { name } };
}
