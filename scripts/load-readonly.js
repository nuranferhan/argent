import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const targetRate = Number(__ENV.TARGET_RPS || 3);
const soakDuration = __ENV.SOAK_DURATION || '10m';
const tickerStatuses = new Counter('ticker_status');

export const options = {
  scenarios: {
    readonly_ticker_soak: {
      executor: 'constant-arrival-rate',
      rate: targetRate,
      timeUnit: '1s',
      duration: soakDuration,
      preAllocatedVUs: 2,
      maxVUs: 4,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<750', 'p(99)<1500'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  // Health checks can take longer than ticker reads because they probe each backing service.
  // Allow a slow but healthy local startup/readiness check without failing before the soak begins.
  const health = http.get(`${baseUrl}/health`, { timeout: '30s', tags: { phase: 'preflight' } });
  let payload;
  try {
    payload = health.json();
  } catch (_) {
    fail(`Health preflight returned non-JSON HTTP ${health.status}; no load was started.`);
  }
  if (health.status !== 200 || payload.status !== 'UP') {
    fail(`Health preflight is not UP (HTTP ${health.status}, status ${payload.status}); no load was started.`);
  }
  return { startedAt: new Date().toISOString() };
}

export default function () {
  const response = http.get(`${baseUrl}/api/v1/ticker/BTCUSDT`, {
    timeout: '5s',
    tags: { endpoint: 'ticker_readonly' },
  });
  tickerStatuses.add(1, { status: String(response.status) });
  let ticker;
  try {
    ticker = response.json();
  } catch (_) {
    ticker = null;
  }
  check(response, {
    'ticker responds HTTP 200': (r) => r.status === 200,
    'ticker payload is BTCUSDT with a positive price': () =>
      ticker?.symbol === 'BTCUSDT' && Number(ticker.price) > 0,
  });
}
