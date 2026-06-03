import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// Separate real server errors (5xx) from expected rate-limit denials (429)
const serverErrors = new Counter('server_errors');
const denialRate   = new Rate('denial_rate');

export const options = {
  scenarios: {
    rampToTarget: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 600,
      stages: [
        { target: 500,  duration: '20s' },  // warm up the JVM + Redis cache
        { target: 2000, duration: '20s' },  // ramp up
        { target: 5000, duration: '30s' },  // push to 5k/s
        { target: 5000, duration: '60s' },  // sustain — this is where latency numbers are captured
        { target: 0,    duration: '10s' },  // ramp down
      ],
    },
  },
  thresholds: {
    // Resume target: sub-5ms average, which maps to roughly p50<10ms under load
    http_req_duration: ['p(50)<10', 'p(95)<30', 'p(99)<100'],
    // Real errors (not 429s) should be essentially zero
    server_errors: ['count<10'],
  },
};

const TIERS   = ['FREE', 'PRO', 'INTERNAL'];
const ACTIONS = ['api:get', 'api:list', 'api:post', 'api:search'];

export default function () {
  // 500 rotating users → realistic key distribution across Redis sorted sets
  const userId = `user-${__VU % 500}`;
  const tier   = TIERS[__VU % TIERS.length];
  const action = ACTIONS[Math.floor(Math.random() * ACTIONS.length)];

  const res = http.post(
    'http://localhost:8080/check',
    JSON.stringify({ userId, tier, action }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    'responded in time':     (r) => r.timings.duration < 100,
  });

  // Track categories separately so denial rate doesn't pollute the error metrics
  if (res.status >= 500) serverErrors.add(1);
  denialRate.add(res.status === 429);
}
