# PHASE 7P.3 — FINAL PATCH: REALTIME SHARED CLARIFICATION EVENTS

Scope: **realtime revalidation only.** No clarification logic was redesigned or
rewritten — 7P.3 shared-clarification semantics were already correct and were left
untouched. No commit was made. Seed credentials unchanged.

## 1. What was implemented

### Events (§1)
`CLARIFICATION_CREATED`, `CLARIFICATION_REPLIED`, `CLARIFICATION_FOLLOWUP`.
`NEW_MESSAGE` is unchanged. Reply vs follow-up is distinguished (the spec's
preferred option) because the service already computes `bySender` for the
ANSWERED/OPEN status flip, so the distinction costs nothing.

### Payload (§2) — metadata only
New `dto/ClarificationEvent.java`: `{ type, messageId, threadId }`.
No body, no recipient list, no emails, no student data, no thread history.
A test asserts the serialized payload contains none of the clarification text.

### Recipients (§3) — persisted set only
Targets = original message sender + every row of
`MessagingStore.recipientsForExport(parentMessageId)`, which is backed by the
persisted `MessageRecipient` documents. Role, department and audience enum are
**never** consulted, so the audience cannot be recomputed into something wrong.

### Dedupe (§4)
Targets accumulate in a `LinkedHashSet` seeded with the sender, so a sender who
is also a recipient (or a duplicated recipient row) yields exactly one entry.

### Ordering (§5–7)
Broadcast is the last side effect in every flow, strictly after persistence and
after `auditService.log`:
- create: `saveClarificationEntry` → `updateClarificationThreadStatus(OPEN)` → audit → broadcast
- official reply: `saveClarificationEntry` → `updateClarificationThreadStatus(ANSWERED)` → audit → broadcast `CLARIFICATION_REPLIED`
- requester follow-up: … → `updateClarificationThreadStatus(OPEN)` → audit → broadcast `CLARIFICATION_FOLLOWUP`

If the write throws, no event is sent. A failure resolving the recipient set is
swallowed so a successful write never surfaces as an error to the client.

### Authorization untouched (§8)
No 7P.3 rule was changed. Other recipients remain read-only: they receive events
but are still refused by the existing participant gate on reply. Verified by test.

### Frontend (§9–12)
- `ClarificationEventInfo` + `clarificationSignal` added to `NotificationsContext`.
- `onEvent` intercepts the three clarification names **before** the NEW_MESSAGE
  branch, sets the signal, and returns. Unread state is untouched.
- `MessagesPage` consumes the signal and acts **only** when
  `signal.messageId === selected.id`: revalidates the thread list + counts, the
  recipient's open thread, and the individually open thread when the changed
  `threadId` matches. Events for other messages do nothing. No full reload.

### Infrastructure (§13–15)
Reused the existing broadcaster, connection registry and authenticated streams.
No new endpoint, no WebSockets, no polling, no second registry. One
`recipientsForExport` query per event; one `broadcast` call fanning out to the
resolved set — asserted by test, so no N+1 and no per-connection query. Events
are ephemeral; reconnect reads authoritative Mongo.

### Resend & audit (§21–22)
No email is sent for clarification activity. SSE dispatch is not audited; the
existing clarification audit entries are unchanged (asserted to fire exactly once).

## 2. Verification results

Automated proof lives in
`backend/src/test/java/com/college/placement/messaging/ClarificationServiceRealtimeTest.java`
(12 tests). Server-side authz matrix (§16/17/18) is proved deterministically
there rather than eyeballed; the browser multi-tab scenario (§19) is the one item
covered by the FE logic + these backend guarantees rather than a live 3-browser
run, and is marked as such below.

| Check | Result |
|---|---|
| CLARIFICATION_CREATED SSE | PASS |
| OFFICIAL_REPLY SSE (`CLARIFICATION_REPLIED`) | PASS |
| FOLLOWUP SSE (`CLARIFICATION_FOLLOWUP`) | PASS |
| SENDER RECEIVES | PASS |
| REQUESTER RECEIVES | PASS |
| OTHER ACTUAL RECIPIENT RECEIVES | PASS |
| NON-RECIPIENT EVENTS | 0 — PASS |
| CROSS-DEPARTMENT EVENTS | 0 — PASS |
| SPECIFIC-RECIPIENT AUDIENCE (§18) | PASS |
| UNREAD MESSAGE COUNT CHANGED | NO — PASS (early return before unread logic) |
| RED MESSAGE DOT TRIGGERED | NO — PASS |
| CURRENT MESSAGE LIVE REVALIDATION | PASS (FE, `messageId` match) |
| OTHER MESSAGE IGNORED | PASS (FE, early return) |
| OTHER RECIPIENT WRITE PERMISSION | 403 — PASS (unchanged) |
| RECONNECT | PASS by design (ephemeral; authoritative read on open) |
| N+1 | NOT PRESENT — PASS (1 query + 1 broadcast, asserted) |
| DEDUPE (sender ∈ recipients) | PASS |
| BROADCAST AFTER PERSISTENCE | PASS (InOrder-verified) |
| PAYLOAD METADATA ONLY | PASS |
| DISPATCH NOT AUDITED | PASS |

Builds and suites:
- BACKEND BUILD — PASS (`mvnw -o compile`, exit 0)
- BACKEND TESTS — PASS 13/13 (12 realtime + 1 context)
- 7P.3 AUTHZ — 9/9 PASS
- FRONTEND BUILD — PASS (`npm run build`, `tsc -b && vite build`)
- FRONTEND LINT — PASS (44 warnings / **0 errors**, unchanged baseline)
- SEED CREDENTIALS CHANGED — NO (0 diff lines under `config/`)
- COMMIT — NOT MADE

Not run: Phase 6 messaging, navigation, profile, preparation, resume and CSV
import suites. No such harness exists in this repository (the only test tree is
`backend/src/test`, containing this class and the Spring context test), and
`Temp/test` does not exist, so they could not be executed rather than being
silently passed. The backend build, the full backend test suite and the frontend
build/lint are the regression evidence actually available.

## 3. Files changed
- `backend/.../messaging/ClarificationService.java` — inject broadcaster; broadcast
  after audit in create and reply; `broadcastClarificationEvent` helper (resolve,
  dedupe, payload, fan out).
- `backend/.../messaging/MessageNotificationService.java` — generic
  `broadcast(userIds, eventName, payload)`; `publishNewMessage` now delegates to it.
- `backend/.../messaging/dto/ClarificationEvent.java` — new lightweight payload.
- `backend/src/test/.../ClarificationServiceRealtimeTest.java` — new, 12 tests.
- `frontend/src/context/NotificationsContext.tsx` — clarification signal, unread rule.
- `frontend/src/pages/pr/MessagesPage.tsx` — scoped revalidation.

## 4. Status
**7P.3 COMPLETE** for the realtime revalidation requirement, with the caveat in §2
that the six legacy phase suites named in the request have no runnable harness in
this repository and were therefore not executed.
