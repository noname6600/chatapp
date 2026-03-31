# Task 4.4 Manual Verification Guide

## Objective
Verify that with two users active in the same chat room, new notifications appear in the receiver UI **immediately without page refresh**.

---

## Prerequisites
- Frontend dev server running (`npm run dev` in chatappFE)
- Backend services running (or sufficient test environment)
- Two browser windows/tabs available
- Familiarity with the chat app's notification UI

---

## Test Scenario

### Setup Phase (5 min)

1. **Open Browser Window A (User A)**
   - Navigate to `http://localhost:5173` (or your FE dev server)
   - Log in as **User A** (e.g., `user_a@example.com`)
   - Navigate to or create a **shared chat room** with User B
   - Keep this window open and visible

2. **Open Browser Window B (User B)**
   - Open a second browser window/tab
   - Navigate to the same instance
   - Log in as **User B** (e.g., `user_b@example.com`)
   - Navigate to the **same chat room** as User A
   - Keep this window visible

3. **Position Windows**
   - Arrange windows side-by-side if possible so you can observe both simultaneously
   - Both should show the chat room message list and notification bell/count

---

## Test Execution

### Test Case 1: User A Sends → User B Receives in Real-Time

**User A:**
- Type a message: `"Real-time test from A - message 1"`
- Send the message
- Observe: Message appears in your own UI

**User B (Critical Observation):**
- **Without refreshing the page**
- Observe the notification bell or notification list in the top-right/corner
- ✅ **Expected:** 
  - Notification bell count increments immediately (within 1-2 seconds)
  - OR new notification appears in the notification droplist immediately
  - Message appears in the room's message list instantly

**Result:** ✅ or ❌ (document any delays or need to refresh)

---

### Test Case 2: User B Sends → User A Receives in Real-Time

**User B:**
- Type a message: `"Real-time test from B - message 1"`
- Send the message
- Observe: Message appears in your own UI

**User A (Critical Observation):**
- **Without refreshing the page**
- Observe the notification bell or notification list
- ✅ **Expected:**
  - Notification bell count increments immediately (within 1-2 seconds)
  - OR new notification appears in the notification droplist immediately
  - Message appears in the room's message list instantly

**Result:** ✅ or ❌ (document any delays or need to refresh)

---

### Test Case 3: Rapid Fire (Optional)

**User A → User B:**
- Send 2-3 messages rapidly
- User B observes each one appearing instantly without refresh

**User B → User A:**
- Send 2-3 messages rapidly
- User A observes each one appearing instantly without refresh

**Result:** ✅ or ❌

---

## Verification Checklist

- [ ] Test Case 1: User A→B notification appears immediately (no refresh needed)
- [ ] Test Case 2: User B→A notification appears immediately (no refresh needed)
- [ ] Test Case 3 (optional): Rapid messages are handled correctly
- [ ] Notification bell count updates correctly
- [ ] Notification list shows new messages with correct sender/timestamp
- [ ] No console errors during tests (check browser dev tools)
- [ ] Room message list updates in real-time for both users

---

## What to Document

Once testing is complete, record:

1. **Overall Result:** ✅ PASS or ❌ FAIL
2. **Observations per test case:**
   - Latency (how long notification took to appear)
   - Bell count accuracy
   - Notification list accuracy
3. **Any issues encountered:**
   - Delays > 2 seconds
   - Notifications not appearing until refresh
   - Incorrect counts or stale data
   - Console errors

4. **Environment Details:**
   - Browser & version
   - Network latency (normal/good)
   - Any filtering or muting in effect

---

## Success Criteria

✅ **PASS** if:
- Both directions (A→B and B→A) show notifications appearing within 1-2 seconds
- No page refresh required to see new notifications
- Notification counts are accurate
- Bell visual indicator updates immediately

❌ **FAIL** if:
- Notifications only appear after page refresh
- Delays > 3 seconds
- Counts become incorrect or stale
- Error messages in console related to notifications

---

## Next Steps After Verification

Once manual testing is complete:
1. Return here and confirm result (PASS/FAIL)
2. Agent will update task 4.4 checkbox
3. Change will be ready for archival in OpenSpec

---

**Ready to test? Let me know the results and I'll mark task 4.4 complete!**
