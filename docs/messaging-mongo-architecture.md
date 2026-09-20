# Messaging Storage Architecture (MongoDB-authoritative)

Status: **Certified** (Phase 6F.4). Effective from 6F.2 cutover onward.

## Summary

Placement messaging (messages, recipients, reactions, clarifications) is stored in
**MongoDB**, which is authoritative. PostgreSQL retains a certified historical
snapshot of the same dataset and serves exclusively as rollback storage. Normal
traffic never dual-writes and never reads messaging from PostgreSQL. All other
platform data (users, departments, students, drives, audit logs, etc.) remains in
PostgreSQL.

## Data layout (MongoDB, database `placement_portal`)

| Collection               | Key fields / unique constraints                              |
|--------------------------|--------------------------------------------------------------|
| `messages`               | `messageId` (long, unique), `senderUserId`, `createdAt`      |
| `message_recipients`     | `messageId` + `recipientUserId` (unique); `deliveredAt`, `readAt`, `reaction` |
| `clarification_threads`  | `threadId` (unique); `messageId` + `requesterUserId` (unique); `status` |
| `clarification_entries`  | `entryId` (unique); `threadId`, `createdAt`                  |
| `mongo_sequences`        | per-key counters (`message`, `clarificationThread`, `clarificationEntry`) |

- Public/API ids (`id`, `threadId`, `entryId`) are the numeric long values stored
  in Mongo; the Mongo `_id` is internal only and never exposed.
- `mongo_sequences` stores the **last assigned** value; the next id is `seq+1`.
  `seedToMax` uses `$max`, so counters never regress.
- Reactions are stored as a single `reaction` field per
  `(messageId, recipientUserId)` document (UPVOTE/DOWNVOTE), enforced by the
  unique compound index.

## Runtime storage selection

`app.messaging.storage` (`APP_MESSAGING_STORAGE`, default `mongo`):

- `mongo`  -> `MongoMessagingStore` (default, all profiles)
- `postgres` -> `PostgresMessagingStore` (explicit, logged fallback only)

At startup the active store logs `[MESSAGING] activeStorage=...`. Because the base
configuration always defines the property, no profile can silently fall back to
PostgreSQL. If Mongo is chosen but unreachable, requests fail at call time; set
`APP_MONGODB_REQUIRED=true` to fail startup instead.

## Startup checks & maintenance runners (defaults)

| Runner (order)                 | Gate / default        | Behaviour                                      |
|--------------------------------|-----------------------|------------------------------------------------|
| `DatabaseMigration` (0)        | always (idempotent)   | PG column-type fixes + performance indexes; no messaging-data rewrite |
| `MongoStartupHealthCheck` (10) | `APP_MONGODB_ENABLED` (true) | read-only ping + collection presence check (`[MONGO] HEALTH/COLLECTION`) |
| `MongoBackfillRunner` (20)     | `MIGRATE_MESSAGES_TO_MONGO` (false) | **insert-missing only**; never overwrites Mongo data |
| `MongoValidationRunner` (30)   | `VALIDATE_MESSAGES_MIGRATION` (false) | read-only PG <-> Mongo comparison |
| `MongoPerfProbe` (40)          | `MONGO_PERF_PROBE` (false) | read-only perf probe |
| `MongoRollbackRunner` (40)     | `MIGRATE_MESSAGES_MONGO_TO_POSTGRES` (false) | reverse-migrate to PG; `MONGO_ROLLBACK_DRY_RUN` (true) = no writes |

Rollback runner writes only to the five legacy PG messaging tables and never
truncates them; dry-run reports inserted/updated/unchanged/conflicted without
writing.

## Operational guidance

- Production: set `APP_MONGODB_REQUIRED=true`; keep `APP_MESSAGING_STORAGE=mongo`.
- Reverse migration is a deliberate, dry-run-first operation, NOT an automatic
  path; normal traffic never writes to PG messaging tables.
- MongoDB `auto-index-creation` is enabled in the base config.