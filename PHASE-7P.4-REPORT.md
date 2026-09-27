# Phase 7P.4 — Contact Request Hardening & Direct-Message Unlock — Final Report

**Date:** 2026-09-25
**Scope:** Contact request create/list/target/count/status flows, the explicit-recipient direct-message
authorization seam, targeted contact-request SSE, and the frontend workflow for all four roles.
**Verdict:** **PASS WITH ISSUES** (all required behaviour verified; issues are hardening notes, not functional failures)

---

## 1. FILES CHANGED

### Backend — contact requests (7P.4)

| File | Change |
|---|---|
| `backend/src/main/java/com/college/placement/contact/ContactRequestRepository.java` | Fetch-join list queries (`findIncomingForUser`, `findSentByUser`), `existsBetween` (PENDING duplicate), `existsMessagingGrantBetween` (ACCEPTED/RESOLVED grant), grouped per-status counts query |
| `backend/src/main/java/com/college/placement/contact/ContactRequestService.java` | Role/department/active/PO/duplicate guards, explicit state machine, idempotent same-state replay, 409 on invalid transition, `/targets`, `/counts`, `requesterUserId` mapping, targeted SSE + audit, `hasDirectMessagingPermission` |
| `backend/src/main/java/com/college/placement/contact/ContactRequestController.java` | Status update now takes a JSON body (`UpdateContactRequestStatus`), added `GET /targets`, `GET /counts` |
| `backend/src/main/java/com/college/placement/contact/dto/ContactRequestResponse.java` | Added `requesterUserId` (needed for router-state compose preselection) |
| `backend/src/main/java/com/college/placement/contact/dto/ContactRequestEvent.java` | **New** — metadata-only SSE payload (`type`, `requestId`) |
| `backend/src/main/java/com/college/placement/contact/dto/ContactRequestTargetResponse.java` | **New** — scoped, active `PC` target for the compose modal |
| `backend/src/main/java/com/college/placement/contact/dto/ContactRequestCountsResponse.java` | **New** — `pending/accepted/rejected/resolved/total` |

### Backend — messaging seam (7P.4)

| File | Change |
|---|---|
| `backend/src/main/java/com/college/placement/messaging/MessageService.java` | In the explicit-recipient branch only: existing rules first, then PO hard-stop, then the contact-request grant. Department/role/Everyone audiences untouched |
| `backend/src/main/java/com/college/placement/messaging/MessageNotificationService.java` | Added contact-request event broadcast (targeted, metadata-only, no unread state) |
| `backend/src/main/java/com/college/placement/user/UserRepository.java` | Scoped active-`PC`-by-department query backing `/targets` |
| `backend/src/main/java/com/college/placement/common/exception/GlobalExceptionHandler.java` | Added `HttpMessageNotReadableException` → **400** (previously a missing/malformed body fell through to 500) |

### Frontend (7P.4)

| File | Change |
|---|---|
| `frontend/src/pages/pr/ContactRequestsPage.tsx` | Role-aware Incoming/Sent tabs, request modal, **Declined** label for `REJECTED`, accept/decline/resolve actions, per-status counts, empty states, guard copy, "Send Message" preselection |
| `frontend/src/pages/pr/MessagesPage.tsx` | Specific-recipient compose enabled for `STUDENT`/`PR`, recipient name chips, safe router-state preselect + replace |
| `frontend/src/context/NotificationsContext.tsx` | `contactRequestSignal` revalidation; deliberately placed **before** `NEW_MESSAGE` handling so contact events never touch unread/red-dot state |
| `frontend/src/api/api.ts` | `updateStatus(id, status)` now sends a body; `getTargets()`, `getCounts(view)` |
| `frontend/src/types/index.ts` | Target/count/event/`requesterUserId` types |

### Tests (new)

| File | Tests |
|---|---|
| `backend/src/test/java/com/college/placement/contact/ContactRequestWorkflowTest.java` | 27 |
| `backend/src/test/java/com/college/placement/messaging/MessageServiceContactGrantTest.java` | 9 |

### Not modified (locked)

`ContactRequest` entity, `ContactRequestStatus` enum (no domain change), clarification semantics,
`ClarificationService`, email/Resend/outbox, resume analyzer, preparation content, CSV import,
seed/demo sources. **No commit created** — `HEAD` is still `3344754`.

---

## 2. TESTS

| Test | What it proves | Mocked? | Result |
|---|---|---|---|
| `ContactRequestWorkflowTest` — create guards (PR ok, STUDENT ok, PC denied, PO target denied, cross-dept denied, duplicate 409) | Create authorization surface | repo mocked | PASS |
| `ContactRequestWorkflowTest` — state machine (all legal/illegal transitions, idempotent replay) | Transition rules + 409/200 split | repo mocked | PASS |
| `ContactRequestWorkflowTest` — `permissionFollowsStatus` | Grant is exactly PENDING→false, ACCEPTED→true, REJECTED→false, RESOLVED→true, and only for `STUDENT`/`PR` → `PC` | repo mocked | PASS |
| `ContactRequestWorkflowTest` — targeted SSE (target gets `CREATED`, requester gets accept/reject/resolve, no cross-delivery) | Event routing | notifier mocked | PASS |
| `ContactRequestWorkflowTest` — audit metadata (no subject/message leakage) | Privacy of audit rows | repo mocked | PASS |
| `MessageServiceContactGrantTest` — granted request lets the requester message the target PC | Seam wiring in `MessageService` | **contact service mocked** | PASS |
| `MessageServiceContactGrantTest` — PENDING/REJECTED do not grant | Seam wiring | **contact service mocked** | PASS |
| `MessageServiceContactGrantTest` — cross-department, wrong-role, PO targets denied | No over-grant | **contact service mocked** | PASS |
| `MessageServiceContactGrantTest` — department/role/Everyone audiences unaffected | No audience regression | mocked | PASS |
| `ClarificationServiceRealtimeTest` (existing) | Clarification SSE + semantics unchanged | mocked | PASS (12/12) |
| `PlacementPortalApplicationTests` (existing) | JPA query validation + context loads | — | PASS |

**Full suite: 49/49 PASS** (`.\mvnw.cmd -o test`), including 36 new 7P.4 tests.

> **Important honesty note:** every messaging test mocks `hasDirectMessagingPermission`, so the unit
> suite proves the *seam wiring*, not the JPQL. The live matrix in §4 is the end-to-end proof that the
> real query and both call directions behave correctly.

---

## 3. LIVE SMOKE — 3×3 (create + list scoping)

Server: `http://localhost:8080` (rebuilt jar, log `C:\Users\karth\AppData\Local\Temp\opencode\backend-7p4.log`).

| # | Scenario | Expected | Live | Result |
|---|---|---|---|---|
| 1 | `GET /targets` as CSE PR | only active same-dept `PC`s (ids 2, 3) | exactly that | PASS |
| 2 | Valid create (PR → same-dept PC) | 201 + `PENDING` | 201 | PASS |
| 3 | Duplicate create while `PENDING` | 409 | 409 | PASS |
| 4 | Cross-department target | 403 | 403 | PASS |
| 5 | Target role `PO` (as PR and as STUDENT) | 403 | 403 | PASS |
| 6 | Create as `PC` | 403 | 403 | PASS |
| 7 | Create as anonymous | 401 | 401 | PASS |
| 8 | `GET /mine` / `GET /targets` as `PC` | 403 | 403 | PASS |
| 9 | Inactive target | 403 | unit-verified (no inactive PC in seed) | PASS (unit) |

**List scoping (live):** `PC CSE 2` incoming = 3, `PC CSE 1` incoming = 2 (different scoped sets);
`PR` sent = 4, `STUDENT` sent = 1; each role only ever sees its own rows. **PASS**

**Counts (live, §28):** `PC CSE 2` → pending=0 accepted=1 declined=1 resolved=1; `PR` → pending=1 total=4.
Single grouped query, no N+1. **PASS**

---

## 4. STATE MATRIX (§31) — live

All on freshly created request rows, authenticated as the target PC.

| Transition | Expected | Live | Result |
|---|---|---|---|
| fresh create | 201, `PENDING` | 201 `PENDING` | PASS |
| `PENDING → ACCEPTED` | 200 | 200 | PASS |
| `PENDING → REJECTED` | 200 | 200 | PASS |
| `ACCEPTED → RESOLVED` | 200 | 200 | PASS |
| same action repeated (accept ×2, resolve ×2) | 200 idempotent, no double event | 200 both | PASS |
| `ACCEPTED → REJECTED` | 409 | 409 | PASS |
| `ACCEPTED → PENDING` | 409 | 409 | PASS |
| `REJECTED → ACCEPTED` | 409 | 409 | PASS |
| `REJECTED → PENDING` | 409 | 409 | PASS |
| `RESOLVED → ACCEPTED` | 409 | 409 | PASS |
| `RESOLVED → PENDING` | 409 | 409 | PASS |
| `RESOLVED → REJECTED` | 409 | 409 | PASS |
| invalid enum `BOGUS` | 400 | 400 | PASS |
| **missing request body** | 400 | **500 → fixed → 400** | PASS (after fix) |
| unrelated PC updates someone else's request | 403 | 403 | PASS |
| requester updates their own request | 403 | 403 | PASS |
| anonymous GET / PUT / POST | 401 | 401 | PASS |

**Defect found and fixed during verification:** a `PUT` with no body raised
`HttpMessageNotReadableException`, which had no handler and fell through to the generic
`Exception` handler → **500**. §31 requires invalid input to be 400. Added a dedicated handler in
`GlobalExceptionHandler`; re-verified live (400) and the full suite still passes 49/49.

`resolvedAt` is written for terminal `REJECTED`/`RESOLVED` only; `ACCEPTED` rows keep it null.

---

## 5. MESSAGING MATRIX (§32) — live

Existing rules were measured first so the "before acceptance" column is factual, not assumed.

**Existing-rule baseline (no contact request involved):**

| Path | Result | Note |
|---|---|---|
| `STUDENT` → `PC`, same dept | 201 allowed | pre-existing |
| `STUDENT` → `PC`, **cross** dept | 201 allowed | pre-existing, deliberately unchanged |
| `PC` → `STUDENT`, same dept | 201 allowed | pre-existing |
| `PR` → `PC`, same dept | **400 blocked** | pre-existing — this is the gap the request unlocks |

**Unlock matrix (clean pairs, no pre-existing requests):**

| Scenario | Before acceptance | After acceptance | Expected | Result |
|---|---|---|---|---|
| `PR` (3027) → `PC` (3411), no request | 400 | 201 | EXISTING RULE → GRANTED | PASS |
| `PR` → `PC` while `PENDING` | 400 | — | still blocked until accepted | PASS |
| `PR` → `PC` after `ACCEPTED` | — | **201** | granted | PASS |
| `PC` → `PR` (requester) after `ACCEPTED` | — | **201** | granted (existing same-dept rule also allows) | PASS |
| `PR` → `PC` after `RESOLVED` | — | 201 | grant persists | PASS |
| `PR` → `PC` after `REJECTED` | 400 | 400 | no grant | PASS |
| `PC` → `PR` after `REJECTED` | — | 201 | **allowed by existing same-dept PC→PR rule**, not by the request | PASS (not a request grant) |
| `PR` → *different* `PC` (cross-pair) | 400 | 400 | exact pair only | PASS |
| *different* `PR` → same `PC` | 400 | 400 | exact pair only | PASS |
| `STUDENT` → `PO` | 400 | 400 | PO never reachable | PASS |
| `PR` → `PO` | 400 | 400 | PO never reachable | PASS |
| `PC` → `PO` | 400 | 400 | PO never reachable | PASS |
| `DEPARTMENT` audience as `STUDENT`/`PR` | 400 | 400 | exception is explicit-recipient only | PASS |

**Honest read of the feature:** because same-department `PC → STUDENT/PR` was already permitted, the
*observable* unlock is the **`PR → PC` direction** (400 → 201). The reverse direction is genuinely
granted by the request and returns 201, but was already reachable by the pre-existing rule. The
security-relevant negatives (cross-pair, rejected, PO, audiences) all behave correctly.

**Direction handling:** the grant query matches the requester/target pair in **either** call order, so
`MessageService` can ask "may A message B" from both sides; the roles are still constrained
(requester ∈ {`STUDENT`,`PR`}, target = `PC`) so a reversed query cannot match a `PC` requester.

---

## 6. REALTIME MATRIX (§33) — live SSE

Live `GET /api/messages/events` with two concurrent authenticated streams, then driving real transitions.

| Event | Expected recipient | Live observed | Payload | Result |
|---|---|---|---|---|
| `connected` | both | both | `{"status":"connected"}` | PASS |
| `CONTACT_REQUEST_CREATED` (req 15, 16) | target PC only | PC stream: both | `{"type":"CONTACT_REQUEST_CREATED","requestId":15}` | PASS |
| `CONTACT_REQUEST_CREATED` | requester | **not delivered** to PR | — | PASS |
| `CONTACT_REQUEST_ACCEPTED` (15) | requester PR | PR stream | `{"type":"CONTACT_REQUEST_ACCEPTED","requestId":15}` | PASS |
| `CONTACT_REQUEST_RESOLVED` (15) | requester PR | PR stream | `{"type":"CONTACT_REQUEST_RESOLVED","requestId":15}` | PASS |
| `CONTACT_REQUEST_REJECTED` (16) | requester PR | PR stream | `{"type":"CONTACT_REQUEST_REJECTED","requestId":16}` | PASS |
| accept/reject/resolve events to target PC | not delivered | PC stream contained **only** `CREATED` | — | PASS |
| unread / red-dot / `NEW_MESSAGE` side effects | none | none observed | — | PASS |
| keepalive comment | periodic | `:keepalive` seen | — | PASS |
| metadata only (no subject/message) | required | payload is `type` + `requestId` only | — | PASS |

---

## 7. PERFORMANCE (§34) — measured

Dataset built live: **108 contact-request rows** for one PC (90 create→accept→resolve cycles in 6.0 s),
so the 100-request list requirement is met with real data, not a mock.

| Measurement | Result |
|---|---|
| `GET /incoming?size=100` (100 rows) | **50.8 ms** avg (58/69/50/38/39) |
| `GET /incoming?size=1` | **12.6 ms** avg |
| `GET /incoming?size=200` (108 rows available) | **46.8 ms** avg |
| `GET /counts?view=incoming` (108 rows) | **26.6 ms** avg |
| `GET /targets` | **19.8 ms** avg |
| `POST /contact-requests` (create) | **20.0 ms** avg |
| `PUT .../status` accept | **22.1 ms** avg |
| `PUT .../status` resolve | **21.2 ms** avg |
| Direct permission check via message send — **granted** path | **30.9 ms** avg |
| Direct permission check — **denied** path (no grant) | **7.4 ms** avg |

**No N+1:** the list uses `join fetch` for requester/target in a single query, and `/counts` is one
grouped query — a 108-row count page returns in 26.6 ms, which would be impossible with per-row
lookups. List latency grows 12.6 ms → 50.8 ms going from 1 to 100 rows, and that delta is dominated by
JSON serialization of 100 rows, not by extra database round trips.

**Index note:** the audit found no dedicated index on `(student_profile_id, status)` or
`(target_user_id, status)`. At 108 rows (and the current dataset sizes) all measured paths are
comfortably fast, so no migration was added. If contact-request volume grows to 10⁵ rows, a
composite index on `target_user_id, status` and `student_profile_id, status` is the first thing to add.

---

## 8. REGRESSION (§36)

| Area | Status | Evidence |
|---|---|---|
| Backend full suite | **PASS 49/49** | `.\mvnw.cmd -o test` → `BUILD SUCCESS`, 0 failures/errors |
| Frontend TypeScript | **PASS** | `npx tsc --noEmit` exit 0 |
| Frontend build | **PASS** | `npm run build` → built in 512 ms |
| Frontend lint | **PASS** | 44 warnings, **0 errors** (unchanged baseline) |
| Clarification semantics | **PASS / unchanged** | `ClarificationServiceRealtimeTest` 12/12; `ClarificationService` not modified in 7P.4. The `MessagesPage` compose change touches the *message* composer only |
| Existing messaging audiences | **PASS / unchanged** | DEPARTMENT/ROLE/Everyone paths byte-identical in behaviour; exception is explicit-recipient only (verified live) |
| Email / Resend | **NOT RUN, not modified** | no changes in 7P.4; no email test suite exists in the repo |
| Resume analyzer | **NOT RUN, not modified** | no suite present; source untouched |
| Preparation content | **NOT RUN, not modified** | seeder log shows unchanged counts (9 modules / 92 topics / 278 questions) |
| CSV import | **NOT RUN, not modified** | no suite present; source untouched |
| Seed / demo data | **Source unchanged** | `DataInitializer` reported "Seed data already exists. Skipping initialization." Live verification created ~115 request rows in the *dev database* (not seed files) |
| Resend real send | **NOT RUN** | intentionally out of scope |
| Commits | **NONE** | `HEAD` = `3344754`, working tree uncommitted as required |

---

## 9. RESIDUAL GAPS / NOT DONE

> **Update:** gaps 1, 2 and 3 below were closed in Phase 7P.4A — see **§11 Security Closure**.

1. ~~**No browser click-through.**~~ **CLOSED in §11** (23/23 real-browser checks).
2. ~~**Messaging unit tests mock the grant seam.**~~ **CLOSED in §11** — real
   `@SpringBootTest` repository/service tests (22/22) and real MockMvc API tests (13/13) now cover
   `existsMessagingGrantBetween` in both call directions.
3. ~~**The grant query does not re-check department equality.**~~ **CLOSED in §11** — the query now
   re-checks current department equality *and* both users' active status.
4. **`GET /contact-requests/incoming` is role-agnostic.** Any authenticated user may call it, but the
   query is scoped to rows where they are the target, so a requester/PO/student always gets an empty
   set. Deliberate (avoids a needless 403) but worth documenting.
5. **No composite indexes** on the grant/status columns — see §7. Fine at current volume.
6. **Absent test suites** for messaging history, SSE reconnect, email, resume, preparation and CSV
   import are reported as **NOT PRESENT**, not as passes.
7. **Dev database now holds ~115 verification rows** for PC 3411/PC CSE 2. No seed file was modified;
   a fresh database is unaffected.

---

## 10. VERDICT

**PASS WITH ISSUES.**

All locked requirements were implemented and verified: the four-role create/list/target/count surface,
the explicit state machine with 409/400/403/401 handling, idempotent replay, exact-pair
`ACCEPTED`/`RESOLVED` direct-message grant in both directions with no PO/cross-pair/audience leakage,
targeted metadata-only SSE with correct recipients, no unread side effects, 49/49 backend tests, and a
clean frontend typecheck/build/lint. Live evidence covers the state matrix, the messaging matrix
(including the "before acceptance" classification), the realtime matrix and real performance numbers on
a 108-row dataset.

One real defect surfaced during verification (missing body → 500) and was fixed to 400 before this
report. The remaining issues are the absence of a browser click-through, a JPQL-level test for the
grant query, and the deliberate decision not to add a department re-check to that query.

> **Superseded by §11:** all three issues above were closed in Phase 7P.4A. The phase verdict is now
> **7P.4 FULLY CLOSED**.

---

## 11. SECURITY CLOSURE (7P.4A)

### 11.1 The gap that was closed

7P.4's grant query recognised an `ACCEPTED`/`RESOLVED` contact request between the two users using
four conditions. It did **not** independently re-verify the invariants that the create path enforces
in `ContactRequestService.create` (target not PO, target role `PC`, **target active**, same
department, requester role `STUDENT`/`PR`). A malformed, legacy or hand-inserted `ACCEPTED` row could
therefore grant an exception that the API could never create — for example a cross-department pair,
or a pair whose PC has since been deactivated or moved.

### 11.2 The single hardened source of truth

`ContactRequestRepository.existsMessagingGrantBetween` now verifies every create-time invariant
inside one cheap boolean count projection — no entity load, no N+1, no history scan:

```java
select (count(cr) > 0) from ContactRequest cr
where cr.status in :statuses                                   -- ACCEPTED / RESOLVED
and cr.studentProfile.user.role in :requesterRoles              -- STUDENT / PR
and cr.targetUser.role = :targetRole                           -- PC
and cr.studentProfile.user.active = true                        -- 7P.4A
and cr.targetUser.active = true                                 -- 7P.4A
and cr.studentProfile.user.department.id = cr.targetUser.department.id
and ((cr.studentProfile.user.id = :userA and cr.targetUser.id = :userB)
  or (cr.studentProfile.user.id = :userB and cr.targetUser.id = :userA))
```

Notes:

- PO is excluded **structurally**: a PO satisfies neither the requester-role predicate nor the
  `PC` target predicate, so it cannot be either side of a granting row.
- Department equality compares the *authoritative current* department IDs, so a row inserted after
  the two users were moved apart stops granting immediately. A null department on either side
  cannot satisfy the comparison, so it never grants.
- Active status is re-checked on **both** sides, so deactivating either participant revokes the
  grant immediately. This closes the "active target" create invariant. (Approved during 7P.4A after
  the ambiguity between the brief's opening paragraph and its numbered condition list was raised.)
- Pre-existing messaging rules are still evaluated first. The exception applies only to explicitly
  listed recipients and never widens a department, role or Everyone audience.
- `MessageService.isDirectRecipientPermitted` remains the single call site, so there is one place
  where the decision is made.

### 11.3 Final status per condition

| # | Condition | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Exact pair matches (either order) | **PASS** | security suite; live `valid-*`, `mal-unrelated` |
| 2 | Status `ACCEPTED` or `RESOLVED` | **PASS** | security suite; live `valid-accepted`, `valid-resolved` |
| 3 | Requester role is `STUDENT` or `PR` | **PASS** | security suite; live `mal-pc-requester`, `mal-pr-student` |
| 4 | Target role is `PC` | **PASS** | security suite; live `mal-pr-student` |
| 5 | Same current department | **PASS** | security suite; live `mal-xdept` |
| 6 | Neither side is PO | **PASS** | security suite; live `mal-po-target`, `mal-pc-to-po` |
| 7 | Reversed direction (target → requester) | **PASS** | live `valid-reverse`; API test `malformedCrossDepartmentAcceptedRowStaysBlockedInReverse` |
| 8 | Both users still active (7P.4A) | **PASS** | live `inactive-target`, `inactive-requester` + restore cases |
| — | `PENDING` request grants nothing | **PASS** | live `valid-pending`; `pendingRequestLeavesPairBlocked` |
| — | `REJECTED` request grants nothing | **PASS** | live + `rejectedRequestLeavesPairBlocked`; browser `DECLINED` has no action |
| — | Malformed / legacy rows grant nothing | **PASS** | 6 planted rows, all 400; 7 API tests |
| — | Existing rules unaffected | **PASS** | live `base-stu-pc` 201, `base-stu-po` 400, `base-stu-stu` 201, `base-pc-crossdept` 201 |

### 11.4 Tests

Real database and real HTTP throughout — no mocked grant seam.

| Suite | Result |
|-------|--------|
| `ContactRequestGrantSecurityTest` (repository/service, real PostgreSQL, transactional) | **22 / 22 PASS** |
| `ContactRequestGrantApiTest` (real MockMvc + Spring Security + real `MessageService`) | **13 / 13 PASS** |
| Full backend suite | **84 / 84 PASS**, 0 failures, 0 errors |
| Backend build | **PASS** (`mvnw test` → BUILD SUCCESS) |
| Frontend `tsc --noEmit` | **PASS** (0 errors) |
| Frontend `npm run build` | **PASS** (built in ~0.5s) |
| Frontend `npm run lint` | **PASS** (0 errors, 44 pre-existing warnings) |

`ContactRequestGrantFixtures` writes rows straight through the repositories so malformed cases are
real database state rather than mocks. Every test is `@Transactional` and rolls back.

Added in 7P.4A: `inactiveTargetDoesNotGrant`, `inactiveRequesterDoesNotGrant`,
`inactiveTargetDoesNotGrantInReverse`, `targetDeactivatedAfterAcceptanceStopsGranting`,
`requesterDeactivatedAfterAcceptanceStopsGranting`.

### 11.5 Live authorization matrix — 19 / 19 PASS

Run against the running workspace backend with real logins and real recipients.

Valid lifecycle (clean pair PR 3036 → PC 3411, both CSE-AIML):

| Case | Expected | Got |
|------|----------|-----|
| No request exists | 400 | 400 |
| Request `PENDING` | 400 | 400 |
| After `ACCEPTED` | 201 | 201 |
| After `RESOLVED` | 201 | 201 |
| Reverse direction PC → requester | 201 | 201 |

Malformed / legacy rows planted directly in SQL (all `ACCEPTED`):

| Case | Expected | Got |
|------|----------|-----|
| Cross-department PR → PC | 400 | 400 |
| PR → PO | 400 | 400 |
| PR → Student (cross-department) | 400 | 400 |
| PC as requester → PC (cross-department) | 400 | 400 |
| PC as requester → PO | 400 | 400 |
| Unrelated pair (no row) | 400 | 400 |

Active-status invariant (users toggled through the app's own JDBC path, always restored):

| Case | Expected | Got |
|------|----------|-----|
| PR → PC with PC deactivated | 400 | 400 |
| PR → PC after PC reactivated | 201 | 201 |
| PC → PR with PR deactivated | 400 | 400 |
| PC → PR after PR reactivated | 201 | 201 |

Pre-existing rules intact, no contact request involved: STUDENT → PC same dept **201**,
STUDENT → PO **400**, STUDENT → STUDENT **201** (the malformed row granted nothing),
PC CSE → STUDENT CSE **201**.

All 7 planted malformed rows and the 1 synthetic profile were removed afterwards
(`MALFORMED-GRANT%` → 0 rows, `MALFORMED-REG%` → 0 rows), and both toggled users were verified
`active=true` again.

### 11.6 Browser click-through — 23 / 23 PASS

Driven in real Chrome (`--headless=new`) over the DevTools Protocol against the workspace frontend
on `:5173` and backend on `:8080`, with real form logins for both roles. Screenshots and the result
JSON are in `%TEMP%\opencode\shots\`.

Requester side:

- PR logs in through the real login form → lands on `/dashboard`. **PASS**
- Contact Requests page lists the `ACCEPTED`, `RESOLVED` and `DECLINED` requests. **PASS**
- The UI shows the **Declined** label, never the `REJECTED` enum. **PASS**
- Opening the `ACCEPTED` request offers a **Send Message** action, and it is clickable. **PASS**
- Clicking it navigates to `/messages` and opens the compose modal (`title="Compose Message"`). **PASS**
- The **exact request target is preselected**: chip `Bulk PC CSE-AIML 1`, `1 selected`, and a
  `Remove Bulk PC CSE-AIML 1` control — i.e. the real recipient, not a guessed one. **PASS**
- Title and content are present, editable, hold the typed text, and Continue is enabled. **PASS**
- Continue advances to the confirm step (`Send this message to 1 recipient?`). **PASS**
- Send succeeds, the modal closes, and the message appears in the conversation list. **PASS**
- The `RESOLVED` request still offers **Send Message**. **PASS**
- The `DECLINED` request offers **no** Send Message action. **PASS**

Recipient side:

- PC logs in through the real login form. **PASS**
- The PC receives the click-through message and sees it on the exact sender conversation. **PASS**
- The planted malformed rows surface no actionable request to the PC. **PASS**

### 11.7 Environment ownership

Verified safe path/PID only; nothing outside the workspace was touched.

| Component | PID | Notes |
|-----------|-----|-------|
| Backend (`:8080`) | 17452 | `mvnw spring-boot:run`, classpath `...\Placement Portal\backend\target\classes`; started after the final compile, confirmed to contain both `active = true` predicates |
| Frontend (`:5173`) | 46268 | Vite dev server for `...\Placement Portal\frontend` |
| Headless Chrome (CDP `:9222`) | throwaway | isolated browser contexts, temp profile, closed after the run |

### 11.8 Scope discipline

- **No commit was made.** `HEAD` is still `3344754`; all 7P.4/7P.4A work is uncommitted.
- **No seed data was changed.** `DataInitializer` is untouched; all verification data was created
  through the API or through throwaway helpers kept outside the repo, and the planted rows were removed.
- The state machine, `REJECTED` → *Declined* labelling, `RESOLVED` semantics, SSE delivery, unread
  counts, clarifications, HIGH-importance email, Everyone/audience sends, CSV, resume, preparation
  and student import were all left exactly as 7P.4 delivered them.
- Existing messaging rules were not modified; the contact-request exception is additive and only
  reachable for explicit recipients.

### 11.9 Verdict

**7P.4 FULLY CLOSED.**

Every condition in the grant contract, including the active-status invariant, is enforced inside one
central query, is covered by real repository/service and real API tests (35 new tests, 84/84 total),
is proven against the live running stack (19/19), and is exercised end-to-end in a real browser
(23/23). No 7P.4 or 7P.4A issue remains open.

