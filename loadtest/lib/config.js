// Settings shared by every load test, all from -e flags (or K6_* env vars), so no secret is in git.
//   BASE_URL      the API, with no trailing slash (default: the local dev backend)
//   LT_USERS      comma-separated emails of confirmed, non-admin test accounts on that server
//   LT_PASSWORD   their shared password
//   PROFILE       smoke | load | stress | spike | soak (default smoke)

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

export const USERS = (__ENV.LT_USERS || '')
    .split(',')
    .map((email) => email.trim())
    .filter((email) => email.length > 0);

export const PASSWORD = __ENV.LT_PASSWORD || '';

// Refuses prod by name: the tests record views and downloads and would skew the real statistics.
export function assertNotProd() {
    if (/(^|\/\/)api\.swisra\.app/i.test(BASE_URL) && __ENV.I_KNOW_THIS_IS_PROD !== 'yes') {
        throw new Error(`Refusing to load test prod (${BASE_URL}). Use uat.`);
    }
}

// Each profile is one k6 scenario. Virtual users (VUs) are simulated students, each looping through
// a browsing session with think time, so 150 VUs ≈ 150 students using the app at the same moment.
const PROFILES = {
    // Does the script work at all? Run it first, after every change to the script.
    smoke: { executor: 'constant-vus', vus: 1, duration: '1m' },
    // Exam-night estimate: about 10% of 7,544 students online at once, a request every few seconds.
    load: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '3m', target: 150 },
            { duration: '10m', target: 150 },
            { duration: '2m', target: 0 },
        ],
    },
    // Keeps adding students until something breaks: the step where thresholds fail is the ceiling.
    stress: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '2m', target: 100 },
            { duration: '3m', target: 100 },
            { duration: '2m', target: 250 },
            { duration: '3m', target: 250 },
            { duration: '2m', target: 500 },
            { duration: '3m', target: 500 },
            { duration: '2m', target: 800 },
            { duration: '3m', target: 800 },
            { duration: '2m', target: 0 },
        ],
    },
    // Everyone opens the app at once (e.g. a push about a new file or an exam announcement).
    spike: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '30s', target: 500 },
            { duration: '2m', target: 500 },
            { duration: '30s', target: 0 },
        ],
    },
    // Steady load for a long time: memory or DB-connection leaks show up as a slow drift.
    soak: { executor: 'constant-vus', vus: 100, duration: '30m' },
};

// Breakpoint: steps of more and more students, each held for HOLD_S and measured on its own, so
// the step where it gets slow is the maximum. browse.js stops the test at the first failing step
// (abortOnFail), so it takes at most about 11 minutes.
export const BREAKPOINT_STEPS = (__ENV.STEPS || '400,800,1200,1600,2000,2400,3000')
    .split(',')
    .map((n) => Number(n.trim()))
    .filter((n) => n > 0);
const FIRST_RAMP_S = 60; // slower first ramp, so the JVM warms up before the first measurement
const RAMP_S = 30;
const HOLD_S = 60;
// Not measured at the start of each hold: k6 judges thresholds as soon as a step has requests, and
// a step must not stop the test on its first second
const SETTLE_S = 10;
export const BREAKPOINT_MEASURED_S = HOLD_S - SETTLE_S;

PROFILES.breakpoint = {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: BREAKPOINT_STEPS.flatMap((vus, i) => [
        { duration: `${i === 0 ? FIRST_RAMP_S : RAMP_S}s`, target: vus },
        { duration: `${HOLD_S}s`, target: vus },
    ]),
};

// The step a moment of the run belongs to: its student count while it is held, 'ramp' while
// students are being added and during the first SETTLE_S of a hold (not measured)
export function breakpointStepAt(elapsedMs) {
    let end = 0;
    for (let i = 0; i < BREAKPOINT_STEPS.length; i++) {
        end += (i === 0 ? FIRST_RAMP_S : RAMP_S) * 1000;
        if (elapsedMs < end) return 'ramp';
        if (elapsedMs < end + SETTLE_S * 1000) return 'ramp';
        end += HOLD_S * 1000;
        if (elapsedMs < end) return String(BREAKPOINT_STEPS[i]);
    }
    return 'ramp';
}

export function scenarioFor(profile) {
    const scenario = PROFILES[profile];
    if (!scenario) {
        throw new Error(`Unknown PROFILE '${profile}'. Use one of: ${Object.keys(PROFILES).join(', ')}`);
    }
    // Lets a session that is still running when the ramp goes down finish instead of being cut off
    return scenario.executor === 'ramping-vus' ? { ...scenario, gracefulRampDown: '30s' } : scenario;
}
