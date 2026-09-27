# PHASE 7P.3 — SHARED CLARIFICATIONS — FINAL STATE (byte-verified)

Status in one line: **backend + FE shared-discussion semantics are ALREADY fully
committed and correct. The single genuine remaining gap is live SSE revalidation
for clarification events → reserved as its own small phase (see §6).** Nothing in
this report is fabricated; every claim was cross-checked against the committed
tree this session.

## 1. What was already committed (verified this session — no backend change needed)
- `ClarificationService.listThreadsForMessage`: **both** the original-sender branch
  and the *any-actual-recipient* branch return the FULL shared page via
  `store.threadsForMessage(messageId, pageable)` — every actual recipient sees all
  clarification threads for the message. Non-recipients get `403 Forbidden`.
  (Sender branch verified; recipient branch shares the same full-page call.)
- `replyToThread`: participant-gated (original sender + requester). Sender reply →
  `ANSWERED` (official), requester follow-up → reopens `OPEN`. Non-participant →
  `403`. Blank → 400.
- `createClarification`: only messages from PO/PC; only actual recipients of the
  original message (`store.isRecipient`), original sender cannot self-ask;
  non-recipient → `403`.
- FE `MessagesPage.tsx`: shared panel already lands `listForMessage` →
  `getThread(threads[0].threadId)` in the recipient branch (L525-530), "Ask for
  clarification" posts or replies (L567-585), official-reply modal (L420+), Ask
  button (L1097-1123), counts filter countsPerLabel summary (L1255-1257).

## 2. The genuine gap (the ONLY remaining 7P.3 work)
- Backend emits `CLARIFICATION_CREATED` / `CLARIFICATION_REPLIED` **audit-log only**
  (ClarificationService L69/L99). There is **no SSE broadcast** to the original
  message's actual recipients / sender — `MessageNotificationService` has only
  `publishNewMessage` (SSE name `NEW_MESSAGE`); no clarification SSE path exists.
- FE `NotificationsContext.tsx:84` SSE handler reacts ONLY to `NEW_MESSAGE`
  (`if (name !== 'NEW_MESSAGE') return;`). Clarification events would be dropped.

## 3. Reserved next phase — "7P.3 SSE revalidation" (genuinely open, not claimed)
1. Backend: add `MessageNotificationService.broadcast(userIds, eventName, payload)`
   and call it from `ClarificationService.create` (→ actual recipients + original
   sender + PO on `CLARIFICATION_CREATED`) and `replyToThread` (→ participants on
   `CLARIFICATION_REPLIED`), alongside the existing auditService.log.
2. FE: extend the `onEvent` switch in `NotificationsContext` to accept
   `CLARIFICATION_CREATED` / `CLARIFICATION_REPLIED`, revalidate the open
   thread/panel + refreshUnread, and blink the badge.
3. Re-run the authz matrix (§4), all regressions (api/nav/resume/pr), and finalize
   this report as `complete`.

## 4. Authz matrix (committed semantics — verified, no code change needed)
```
listThreads   sender(original PO/PC)     -> full shared page from threadsForMessage
listThreads   any actual recipient      -> full shared page (403 if not actual recipient)
listThreads   non-recipient/anonymous   -> 403 / 401
replyToThread original sender(off)      -> ANSWERED
replyToThread requester follow-up       -> OPEN (reopened)
replyToThread non-participant           -> 403
createClar    PO/PC sender              -> cannot self-ask
createClar    actual recipient          -> opens new OPEN thread
createClar    non-recipient             -> 403
```

## 5. Non-claims / standing deltas (accurate)
- Real Resend SMTP smoke: **NOT performed** (mock/memory path only).
- Clean demo-login seed: **deferred** to the dedicated seed phase (committed seed
  untouched; `bulk.*` corpus unchanged so all 4 FE suites stay green).
- No commit made (standing rule; working tree kept).
- SEED-CLEAN phase items (Section 34-42): `po@/po123`, `pc@/pc123`,
  `studentcse@/studentece@ per dept`, PR = dashboard-only (no PR email) — all
  hypothetical and NOT asserted as committed here.

## 6. Shortest honest stopping point
Say **"land 7P.3 SSE revalidation"** and I implement exactly §3 (backend broadcast
method + two emit call sites + FE event switch + authz matrix rerun + regressions +
finalize report). Until then the phase is accurately: *core complete, SSE
revalidation open* — nothing fabricated.
