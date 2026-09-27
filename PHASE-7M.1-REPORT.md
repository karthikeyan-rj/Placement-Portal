# PHASE 7M.1 — CRITICAL-IMPORTANCE MESSAGING + EMAIL DELIVERY — VERIFICATION REPORT

Scope: P1–P8 verification harness (backend), FE 7M.1 compose/audience DOM checks
Status: PARTIALLY VERIFIED — backend harness + FE DOM compose checks GREEN; real Resend
        SMTP smoke intentionally skipped per user directive (mocked path used instead)
Date: <dated this session>

## 1. Summary
- Backend `P1`–`P8` verification harness: PASS (all importance/audience/count assertions green).
- FE compose DOM checks (PO Everyone + HIGH, PC Everyone-in-My-Department): PASS.
- Server-count disclosure verified on frontend: "Everyone" = 1,668 recipients;
  "Everyone in My Department" (CSE-AIML dst) = 162.
- Audience count `Everyone` present for PO; `Everyone in My Department` for PC.

## 2. What was NOT done (explicitly deferred)
- **Real Resend/email SMTP smoke: SKIPPED per user directive.** Email delivery was
  verified only through the mocked outbox + email-status path (EMAIL_STATUS_CARD / count
  of 1,668). Real delivery remains unverified — candid.
- **Demo seed swap (bulk.* → clean po/pc/studentcse/studentece): DEFERRED to a dedicated
  seed-cleanup phase.** This phase deliberately did NOT change the committed demo logins,
  because every regression harness + lib.js logs in with `bulk.*@example.com /
  BulkSeed@123`. Landing it now would break 20+ harnesses. The planned clean demo logins
  (po@example.com/po123, pc@example.com/pc123, studentcse@example.com/studentcse123,
  studentece@example.com/studentece123, "and so on") are documented in the phase spec and
  scheduled as a separate committed seed-awaited slice.

## 3. Verified counts (from server, not hardcoded)
- PO "Everyone" audience → 1,668 total recipients.
- PC "Everyone in My Department" (CSE-AIML dept8) → 162 recipients.
- The 7M.1 harness asserted these server-returned counts (not UI text) end-to-end.

## 4. Regressions
- api (61), nav (57), resume (51), 7M.1 (28) — all PASS on the COMMITTED seed
  (unchanged). No seed change in this phase.

## 5. Demo-login scheme (planned, NOT applied here)
- po@example.com / po123
- pc@example.com / pc123
- studentcse@example.com / studentcse123
- studentece@example.com / studentece123
- and so on: studenteee@example.com/studentece123 etc.
- PR students: NO separate email. A student who is a PR only reflects in his own
  dashboard; cannot be found by email. This is the agreed design; applying it is the
  dedicated seed-cleanup phase.

==============================================================================
7P.3 PRE-FLIGHT — SHARED CLARIFICATIONS (accurate verified status)
==============================================================================

VERIFIED ON DISK (real committed ClarificationService.java, current true root):
- listThreadsForMessage: BOTH the original-sender branch (line ~112) and the
  any-actual-recipient branch (line ~121) return store.threadsForMessage(...)
  full shared page — ALL actual recipients already see the shared discussion,
  non-recipients throw Forbidden. === 7P.3 section 2 "WHO ACTUALLY SEES" CORE
  IS ALREADY PRESENT in the committed backend. === (No backend change needed —
  the private-ticket "recipient sees own thread only" branch exists only in my
  earlier duplicated temp shim, not in the committed file.)

- replyToThread: participates gate (requester + original sender); sender reply
  sets ANSWERED, requester follow-up reopens OPEN. Matches 7P.3 s33/34/35.

REMAINING (NOT done, listed without exaggeration):
- FE shared discussion panel (single shared "Clarifications 12 / Open 3 /
  Answered 9" + ONLY-recipients-visible heading + "Ask a clarification" for
  actual recipients, "Official reply" from original sender, requester
  follow-up, compact not-ticket UI in ClarificationPanel) — FE not yet
  re-verified against shared semantics-endpoint in this session.
- SSE CLARIFICATION_CREATED / CLARIFICATION_REPLIED revalidation events (not
  added).
- 7P.3 authz matrix DOM harness + full regression re-run + PHASE-7P.3-REPORT.
- Real Resend smoke: STILL SKIPPED (mock path only) per directive.
- Demo-login cleanup: STILL deferred to dedicated seed phase (per directive).
