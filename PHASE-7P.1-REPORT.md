
==============================================================================
7P.3 PRE-FLIGHT — SHARED CLARIFICATIONS (accurate status, no fabrication)
==============================================================================

VERIFIED ON DISK (real committed file, byte-truth):
- Class:  backend/src/main/java/com/college/placement/messaging/ClarificationService.java
- The `listThreadsForMessage` recipient branch ALREADY returns the SHARED view:
      StoredPage<StoredThread> page = store.threadsForMessage(messageId, pageable);
      responses = page.content().stream().map(t -> toSummaryResponse(t, names))
      return new PageImpl<>(responses, pageable, page.totalElements());
  i.e. every actual recipient already sees ALL clarification threads for the
  original message — the core 7P.3 visibility requirement. Sender branch
  (112-119) is identical shared-view. Non-recipient -> ForbiddenException. OK.

- replyToThread  (participant-gated): requester and original sender may reply;
  official reply from sender flips to ANSWERED; requester follow-up reopens to
  OPEN. This matches 7P.3 section 33/34/35 status semantics as built.

- createClarification: recipient-gated only (7P.3 keeps requester-side open to
  recipients; sender cannot self-ask). Consistent with section 18/19.

REMAINING, NOT YET DONE (no claims):
- FE shared discussion UI (official-reply label, compact threading, open filter,
  "Clarifications N / Answered M" counts) — MessagesPage clarifications still
  render per-requester layout; shared "Ask a clarification to all recipients"
  compose action not yet switching to the shared thread list.
- SSE CLARIFICATION_CREATED / CLARIFICATION_REPLIED revalidation events.
- Full 7P.3 authz matrix (32-37) + FE DOM verification + regression re-run +
  PHASE-7P.3-REPORT.md.

Clean-demo-login seed swap: STILL DEFERRED per user directive (dedicated
seed-cleanup phase); bulk.* corpus preserved so all 4 regression suites stay
green. Real Resend SMTP smoke still intentionally skipped (mock path only).
