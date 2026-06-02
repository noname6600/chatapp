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
    { duration: '30s', target: 30  },
    { duration: '1m',  target: 30  },
    { duration: '30s', target: 50  },
    { duration: '1m',  target: 50  },
    { duration: '30s', target: 100 },
    { duration: '2m',  target: 100 },
    { duration: '30s', target: 0   },
  ],
  thresholds: {
    ws_connect_time_ms: ['p(95)<5000'],
    ws_error_rate:      ['rate<0.05'],
    http_req_duration:  ['p(95)<5000'],
    http_req_failed:    ['rate<0.10'],
  },
};

const BASE    = 'https://api.chatweb.nani.id.vn';
const WS_BASE = 'wss://api.chatweb.nani.id.vn';
const ROOM_ID = 'b84bb48f-e5cd-4275-96f0-b5bdac495b37';

export default function () {
  const vuId = __VU % 50 + 1;

  const loginRes = http.post(
    `${BASE}/api/v1/auth/login`,
    JSON.stringify({ username: `loadtest${vuId}@test.com`, password: 'LoadTest123!' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const ok = check(loginRes, { 'login 200': (r) => r.status === 200 });
  if (!ok) { wsErrors.add(1); sleep(2); return; }

  const token = loginRes.json('data.accessToken');
  if (!token) { wsErrors.add(1); sleep(2); return; }

  const ticketRes = http.post(
    `${BASE}/api/v1/realtime/ticket`,
    null,
    { headers: { Authorization: `Bearer ${token}` } }
  );

  const ticket = ticketRes.json('ticket');
  if (!ticket) { wsErrors.add(1); sleep(2); return; }

  const start = Date.now();

  const wsRes = ws.connect(
    `${WS_BASE}/ws/realtime?ticket=${ticket}`,
    {},
    function (socket) {
      socket.on('open', () => {
        wsConnectTime.add(Date.now() - start);
        wsErrors.add(0);

        socket.setInterval(() => {
          const sendRes = http.post(
            `${BASE}/api/v1/chat/messages`,
            JSON.stringify({ roomId: ROOM_ID, content: `msg from vu${vuId} at ${Date.now()}` }),
            { headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`
            }}
          );
          check(sendRes, { 'message sent 200': (r) => r.status === 200 });
          msgSent.add(1);
        }, 5000);
      });

      socket.on('message', () => { msgReceived.add(1); });
      socket.on('error',   () => { wsErrors.add(1); });
      socket.setTimeout(() => socket.close(), 60000);
    }
  );

  check(wsRes, { 'ws upgraded 101': (r) => r && r.status === 101 });
  sleep(1);
}
