#!/bin/bash
BASE="https://api.chatweb.nani.id.vn/api/v1"
TOTAL=200
PASS="LoadTest123!"

echo "Creating $TOTAL test users..."
for i in $(seq 1 $TOTAL); do
  res=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"loadtest${i}@test.com\",\"password\":\"${PASS}\"}")
  if [ "$res" = "200" ] || [ "$res" = "201" ]; then
    echo "  [OK]   loadtest${i}@test.com"
  else
    echo "  [SKIP] loadtest${i}@test.com (status $res — may already exist)"
  fi
done
echo ""
echo "Done. Now log in as an admin and add all loadtest users to the target room."
echo "ROOM_ID in chat-loadtest.js: b84bb48f-e5cd-4275-96f0-b5bdac495b37"
