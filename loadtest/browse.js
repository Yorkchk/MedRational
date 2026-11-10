// A student's session in the app, repeated by every virtual user (VU) with think time in between.
// It only reads, plus the view and download records the app itself sends: no uploads, workshops or
// suggestions, so no push reaches anyone and nothing is written to R2.
//
//   k6 run -e BASE_URL=https://uat.swisra.app -e LT_USERS=a@x.ma,b@x.ma -e LT_PASSWORD=... \
//          -e PROFILE=load loadtest/browse.js
//
// See loadtest/README.md for the profiles and what to watch on the server during a run.
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { assertNotProd, BASE_URL, scenarioFor } from './lib/config.js';
import { authParams, signInAll } from './lib/auth.js';

const PROFILE = __ENV.PROFILE || 'smoke';
// Share of sessions that download or preview a file. Downloads stream through the API, so they
// cost far more than browsing; raise these to test a "everyone downloads the exam pack" night.
const DOWNLOAD_RATE = Number(__ENV.DOWNLOAD_RATE || 0.03);
const PREVIEW_RATE = Number(__ENV.PREVIEW_RATE || 0.02);

const failedChecks = new Rate('failed_checks');

export const options = {
    scenarios: { students: scenarioFor(PROFILE) },
    // Downloads and previews are tagged apart (kind:file): a 5 MB PDF is slow for reasons browsing isn't.
    thresholds: {
        'http_req_failed': ['rate<0.01'],
        'http_req_duration{kind:api}': ['p(95)<500', 'p(99)<1500'],
        'http_req_duration{kind:file}': ['p(95)<5000'],
        'failed_checks': ['rate<0.01'],
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
    assertNotProd();
    const sessions = signInAll();
    const categories = http.get(`${BASE_URL}/api/v1/categories`, { tags: { name: 'categories' } });
    if (categories.status !== 200 || categories.json().length === 0) {
        throw new Error(`No categories on ${BASE_URL}: the test needs some content to browse`);
    }
    return { sessions };
}

function pick(list) {
    return list[Math.floor(Math.random() * list.length)];
}

function think(min, max) {
    sleep(min + Math.random() * (max - min));
}

// GET as the app does, and records whether it answered 200
function get(session, path, name, kind = 'api') {
    const params = authParams(session, name);
    params.tags.kind = kind;
    if (kind === 'file') {
        params.responseType = 'none'; // count the bytes on the wire, don't keep them
    }
    const res = http.get(`${BASE_URL}${path}`, params);
    const ok = check(res, { [`${name} is 200`]: (r) => r.status === 200 });
    failedChecks.add(!ok);
    return res;
}

function post(session, path, name) {
    const params = authParams(session, name);
    params.tags.kind = 'api';
    const res = http.post(`${BASE_URL}${path}`, null, params);
    const ok = check(res, { [`${name} is 2xx`]: (r) => r.status >= 200 && r.status < 300 });
    failedChecks.add(!ok);
    return res;
}

export default function (data) {
    // Each VU sticks to one account, like a student on their phone
    const session = data.sessions[(__VU - 1) % data.sessions.length];

    let file = null;
    let reasoning = null;
    let category = null;

    group('home', () => {
        const categories = get(session, '/api/v1/categories', 'categories');
        get(session, '/api/v1/novelties/badge-count', 'novelties badge');
        if (categories.status === 200) {
            const list = categories.json();
            category = list.length > 0 ? pick(list) : null;
        }
    });
    think(2, 5);
    if (!category) return;

    group('category', () => {
        const reasonings = get(session, `/api/v1/reasonings/category/${category.id}`, 'reasonings of category');
        if (reasonings.status === 200) {
            const list = reasonings.json();
            reasoning = list.length > 0 ? pick(list) : null;
        }
    });
    think(2, 5);
    if (!reasoning) return;

    group('reasoning', () => {
        const res = get(session, `/api/v1/reasonings/${reasoning.id}`, 'reasoning');
        if (res.status === 200) {
            const files = res.json().files || [];
            file = files.length > 0 ? pick(files) : null;
        }
    });
    think(3, 8);

    if (file) {
        group('file', () => {
            get(session, `/api/v1/files/${file.id}/ratings`, 'rating summary');
            get(session, `/api/v1/users/${session.userId}/favorites/${file.id}/status`, 'favorite status');
            get(session, `/api/v1/files/${file.id}/hashtags`, 'file hashtags');
            post(session, `/api/v1/files/${file.id}/views`, 'record view');
            if (file.previewable && Math.random() < PREVIEW_RATE) {
                get(session, `/api/v1/files/${file.id}/preview`, 'preview', 'file');
            }
            if (Math.random() < DOWNLOAD_RATE) {
                get(session, `/api/v1/downloads/file/${file.id}`, 'download', 'file');
            }
        });
        think(5, 15);
    }

    // About a third of the sessions also check favorites or search
    const extra = Math.random();
    if (extra < 0.15) {
        group('favorites', () => {
            get(session, `/api/v1/users/${session.userId}/favorites?size=1000`, 'favorites');
        });
        think(2, 5);
    } else if (extra < 0.35) {
        group('search', () => {
            const query = pick(['cardio', 'ecg', 'diab', 'pneumo', 'neuro', 'rein', 'a']);
            get(session, `/api/v1/search/files?query=${query}&categoryId=${category.id}`, 'search');
        });
        think(2, 5);
    }
}
