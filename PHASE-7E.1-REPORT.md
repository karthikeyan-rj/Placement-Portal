# PHASE 7E.1A — Resend Real-Delivery Verification

**Status: FAIL — real delivery is NOT yet proven.** No real email has been sent, and no message
has reached `karthikeyanrj@student.tce.edu`. Phase 7E.1A cannot be reported as PASS until the
placement officer confirms inbox receipt.

Everything that does not require a live send is complete and verified. The remaining work is
blocked on two external inputs, listed under *Outstanding*.

---

## 1. Root cause (one)

**The outbox was running against the `mock` email provider, which reports success without
sending anything.**

`MockEmailClient` is configured by default and fabricates a `mock-<uuid>` provider message id.
The outbox recorded those rows as `SENT` with `lastError: null`, and the UI presented them to
recipients as emailed. The real Resend credentials were never present in the running JVM, so no
code path existed that could have delivered mail.

Evidence from `message_email_outbox` (MongoDB `placement_portal`):

| status | rows |
|---|---|
| SENT (all `providerMessageId` = `mock-<uuid>`) | 7319 |
| FAILED | 648 |
| CONFIG_ERROR | 162 |
| SKIPPED_INVALID_EMAIL | 1 |
| **PENDING / IN_FLIGHT** | **0 / 0** |

All four outbox rows for the target user (user id 12) are `SENT` with a `mock-` provider id:

| messageId | status | providerMessageId | lastError |
|---|---|---|---|
| 404 | SENT | `mock-50f2d3fe-…` | null |
| 407 | SENT | `mock-bf9b2d97-…` | null |
| 410 | SENT | `mock-41f27be7-…` | null |
| 419 | SENT | `mock-988476c8-…` | null |

Supporting runtime evidence:

- The previously running process (PID 22140, a pre-fix build) had none of
  `APP_EMAIL_NOTIFICATIONS_ENABLED`, `APP_EMAIL_PROVIDER`, `APP_EMAIL_FROM_ADDRESS`,
  `APP_EMAIL_API_KEY`, `APP_EMAIL_WEBHOOK_SECRET` in its environment. Spring Boot does not read
  `backend/.env`, so the properties fell back to `enabled=false` / `provider=mock`.
- Log line from that process: `[EMAIL] Outbox worker disabled (app.email.enabled=false)`.
- The configured sender was the placeholder `placements@yourdomain.com`, and the Resend key is
  send-only: `GET /domains` returns `401 restricted_api_key`, i.e. the key is valid but cannot
  read domain records.

Note the root cause is **not** a Resend-side failure and **not** a bad API key. Mail was never
attempted over the real provider.

---

## 2. Secondary defect found and fixed during verification

`GET /api/placement-drives/{id}/email-recipient-count` returned **HTTP 500** on the first live
run of the new build:

```
org.hibernate.LazyInitializationException: failed to lazily initialize a collection of role:
com.college.placement.placement.EligibilityCriteria.allowedDepartments: could not initialize
proxy - no Session
```

`DriveEmailNotificationService.resolveEligibleRecipients` read the lazy `allowedDepartments`
collection, but it is called from the PO request thread, the `@TransactionalEventListener`
(AFTER_COMMIT) and the scheduled outbox worker — none of which hold a Hibernate session. Every
drive-email endpoint and the entire REGISTRATION_OPEN fanout were therefore broken at runtime.
The unit tests had mocked the repository, so the native query and this failure mode were never
exercised.

Fix: `EligibilityCriteriaRepository.findAllowedDepartmentIdsByDriveId` reads the department ids
directly, so no lazy collection is touched. Regression tests use a detached `EligibilityCriteria`
whose `getAllowedDepartments()` throws, proving the collection is never read. All other
`getAllowedDepartments()` call sites are inside `@Transactional` methods and were left alone.

---

## 3. Data-integrity findings

- **Target identity confirmed.** User id 12, `karthikeyanrj@student.tce.edu`, role `STUDENT`,
  `active = true`, department 1 (CSE), profile id 4, register number `24C21031`,
  `placement_status = NOT_PLACED`, `placement_interested = true`.
- **The student had no academic record at all**, so `PlacementDriveService.checkEligibility`
  returned `false` at `academic == null` (PlacementDriveService.java:172) for **all 75 drives**.
  The batch resolver INNER JOINs `student_academics`, so the student was also excluded from
  every fanout. 7 of 1691 student profiles are in this state.
- **Eligibility percentage criteria are dead config.** `eligibility_criteria` stores
  `min_tenth_pct`, `min_twelfth_pct`, `min_diploma_pct`, but `checkEligibility` never reads
  them, and neither does the batch resolver — so the two paths agree and no divergence exists.
  `placement_status` is likewise not part of eligibility. Pre-existing gap, left unchanged.
- **Orphaned outbox rows.** The four outbox rows above point at messages 404/407/410/419, which
  no longer exist in Postgres. Outbox rows are not cleaned up when a message is deleted.

---

## 4. Controlled test fixture (applied with authorisation)

Applied in a single transaction to the local dev database:

1. `student_academics` row for profile 4 — CGPA 7.40, 0 active backlogs, 0 historical, 10th
   79.50, 12th 80.20, diploma 74.00. Values sit at the CSE cohort norm (average CGPA 7.57,
   average 10th 80.83) so the record is not anomalous. This repairs a genuine data gap; the
   CGPA and backlog figures are synthetic and this student is a real account.
2. Company `Phase 7E.1A Delivery Test Co`.
3. Drive **153** — "Backend Engineer", 8.50 LPA, registration deadline 2026-11-20, drive date
   2026-12-10, location Chennai, status `UPCOMING`.
4. Criteria for drive 153: `min_cgpa` 6.00, `max_active_backlogs` 2, department 1 (CSE) only.

The student was **not** force-emailed. Eligibility is genuine: CSE is an allowed department,
7.40 >= 6.00, and 0 <= 2 backlogs. Drive 153 is deliberately left `UPCOMING` so the
REGISTRATION_OPEN transition is triggered through the application once a verified sender exists,
exercising the real listener and fanout rather than raw SQL.

---

## 5. Verification performed

Live HTTP against the restarted backend (`local` profile, current build):

| check | result |
|---|---|
| `GET /api/placement-drives/153/eligibility/4` (target) | `true` |
| `GET /api/placement-drives/153/eligibility/3` (negative control, no academic row) | `false` |
| `GET /api/placement-drives/153/email-recipient-count` | `145` — matches the equivalent SQL run directly in psql |
| `GET /api/placement-drives/153/email-status` | `queued=0 pending=0 suppressed=0` |
| Runtime email config | `[EMAIL] Outbox worker disabled (app.email.enabled=false)` from the explicitly injected variable |
| `POST /api/placement-drives/153/eligibility` raw SQL equivalent | 145 eligible, target user included |
| Unauthenticated `GET /api/auth/me`, `/api/drives`, `/api/messages/events` | `401` — auth enforced, no `500` |

The negative control matters: it shows `eligible = true` is a real evaluation and not a blanket
pass.

- Backend suite: **240/240 passing** (was 218; 22 added).
- Frontend: `tsc --noEmit` clean, production build clean, lint `0 errors` / `49 warnings`
  (no new warnings).
- Frontend dev server on `:5173` proxies to the backend: `/api/*` returns the backend's `401`
  rather than a proxy `502`.

### 5.1 SSE client-disconnect defect (fixed)

The prior run's log showed repeated errors on `GET /api/messages/events` whenever a browser tab
was closed:

```
Unhandled exception on /api/messages/events: An established connection was aborted ...
org.springframework.http.converter.HttpMessageNotWritableException:
  No converter for [class java.util.HashMap] with preset Content-Type 'text/event-stream'
Exception Processing [ErrorPage[errorCode=0, location=/error]]
```

A peer aborting an SSE stream surfaces as a raw `IOException`, not the
`AsyncRequestNotUsableException` that was already handled, so it fell through to the generic
handler. The response was by then already committed as `text/event-stream`, so writing a JSON
error body raised a second `HttpMessageNotWritableException` and the `/error` dispatch then
failed as well. Every closed tab therefore produced two `ERROR` entries. A disconnect is not a
server fault, so it is now detected (socket exceptions plus abort/reset/broken-pipe wording,
including when wrapped) and absorbed quietly; genuine I/O faults are still logged as errors.

Note: the earlier `Process terminated with exit code: 1` was **not** an application crash. The log
contained no `Application run failed`, no `OutOfMemoryError`, and no shutdown sequence — the
launcher had been started with `Start-Process` and was reaped with the shell's process tree. The
backend is now launched via `Win32_Process::Create` so it is parented by the WMI service and
survives session teardown.

Changes made:

- `email.suppressed` now maps to a new terminal `SUPPRESSED` outbox state, guarded in both
  `applyDeliveryEvent` implementations so a late `email.sent` cannot downgrade it, and surfaced
  in the aggregate counters (previously the `default` branch would have miscounted it as
  pending) and in the PO/PR UI.
- HIGH email subject/body confirmed as `[High Priority] {title}` with sender, title, content and
  a "View Message" CTA.
- Drive email subject/body changed to the required template:
  `Placement Opportunity: {Company} — {Role}`, with company, role, CTC/package, deadline, drive
  date, an eligibility statement and a "View Drive" CTA. `packageLpa` is now passed from the
  drive through the listener. Header injection via company name is neutralised.

---

## 6. Outstanding — required before PASS

1. **A verified sender address.** Supply the exact `from` address on a domain showing verified
   SPF and DKIM in the Resend dashboard. The current value is the placeholder
   `placements@yourdomain.com`. The send-only API key cannot verify domains, so this must be
   confirmed in the dashboard.
2. **A publicly reachable webhook URL plus `RESEND_WEBHOOK_SECRET`.** Resend cannot call
   `localhost:8080`, so without a tunnel no genuine `DELIVERED` or `SUPPRESSED` event can be
   recorded. Signature verification, replay rejection, dedupe and monotonicity are covered by
   tests, but not yet against live provider events.
3. **Then, in order:** one direct test email to the target inbox, awaiting confirmation; then a
   HIGH specific-recipient message; then a HIGH broadcast; then opening drive 153 so the real
   fanout runs.

Until step 1 and 2 are supplied, the backend is intentionally running with
`APP_EMAIL_NOTIFICATIONS_ENABLED=false` so nothing can be dispatched by accident. No bulk
delivery will be attempted before a single real send is confirmed.

**No commit has been made.**
