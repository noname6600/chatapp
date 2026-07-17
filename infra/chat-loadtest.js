import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const wsConnectTime = new Trend('ws_connect_time_ms');
const msgSent       = new Counter('messages_sent');
const msgReceived   = new Counter('messages_received');
const wsErrors      = new Rate('ws_error_rate');

export const options = {
  stages: [
    { duration: '30s', target: 10  },
    { duration: '1m',  target: 10  },
    { duration: '30s', target: 50  },
    { duration: '1m',  target: 50  },
    { duration: '30s', target: 100 },
    { duration: '1m',  target: 100 },
    { duration: '30s', target: 200 },
    { duration: '3m',  target: 200 },
    { duration: '30s', target: 0   },
  ],
  thresholds: {
    ws_connect_time_ms: ['p(95)<5000'],
    ws_error_rate:      ['rate<0.05'],
    http_req_duration:  ['p(95)<3000'],
    http_req_failed:    ['rate<0.05'],
  },
};

const BASE    = 'https://api.chatweb.nani.id.vn';
const WS_BASE = 'wss://api.chatweb.nani.id.vn';
const ROOM_ID = 'b84bb48f-e5cd-4275-96f0-b5bdac495b37';

// Token cache — each VU has its own JS context so this is per-VU, not shared.
// Avoids re-logging in on every iteration (was hammering auth-service every 60s).
let _token  = null;
let _expiry = 0;

function getToken(vuId) {
  if (_token && Date.now() < _expiry) return _token;
  const res = http.post(
    `${BASE}/api/v1/auth/login`,
    JSON.stringify({ username: `loadtest${vuId}@test.com`, password: 'LoadTest123!' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (!check(res, { 'login 200': (r) => r.status === 200 })) return null;
  _token  = res.json('data.accessToken');
  _expiry = Date.now() + 4 * 60 * 1000; // reuse for 4 min
  return _token;
}

export default function () {
  // __VU is 1-indexed and unique per VU, max = 200 (matches setup-users.sh).
  const vuId = __VU;

  const token = getToken(vuId);
  if (!token) { wsErrors.add(1); sleep(3); return; }

  const ticketRes = http.post(
    `${BASE}/api/v1/realtime/ticket`,
    null,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  const ticket = ticketRes.json('ticket');
  if (!check(ticketRes, { 'ticket 200': (r) => r.status === 200 }) || !ticket) {
    wsErrors.add(1); sleep(3); return;
  }

  const start = Date.now();

  // Jitter: each VU holds the WS connection for 80-120s instead of a fixed 60s.
  // Fixed timeout caused all VUs to reconnect simultaneously, which is why nginx
  // couldn't distribute connections fairly — it saw a burst where both upstreams
  // showed 0 active connections at the same moment.
  const sessionMs = 80000 + Math.floor(Math.random() * 40000);

  const wsRes = ws.connect(
    `${WS_BASE}/ws/realtime?ticket=${ticket}`,
    {},
    function (socket) {
      socket.on('open', () => {
        wsConnectTime.add(Date.now() - start);
        wsErrors.add(0);

        // Send a message every 20s (was 5s — at 200 users that was 40 msg/s
        // hitting chat-service constantly, which saturated the DB pool).
        socket.setInterval(() => {
          const sendRes = http.post(
            `${BASE}/api/v1/chat/messages`,
            JSON.stringify({ roomId: ROOM_ID, content: `msg from vu${vuId} at ${Date.now()}` }),
            { headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            }}
          );
          check(sendRes, { 'message sent': (r) => r.status === 200 || r.status === 201 });
          msgSent.add(1);
        }, 20000);

        socket.setTimeout(() => socket.close(), sessionMs);
      });

      socket.on('message', () => msgReceived.add(1));
      socket.on('error',   () => wsErrors.add(1));
    }
  );

  check(wsRes, { 'ws upgraded 101': (r) => r && r.status === 101 });

  // Jitter before reconnect — prevents the synchronized reconnect burst.
  sleep(2 + Math.random() * 5);
}
