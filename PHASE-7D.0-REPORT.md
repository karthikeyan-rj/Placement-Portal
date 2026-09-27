# Phase 7D.0 — Database Migration Hardening (Flyway)

Introduces a real, versioned schema-migration mechanism so production deployments no
longer depend on anyone hand-executing SQL before Spring Boot starts.

**Scope discipline:** this phase changed build configuration, configuration YAML, two new
SQL migration files, and documentation. **Zero Java, TypeScript, or test files were
modified.** No seed, messaging, profile, or business behaviour was touched.

---

## 1. What changed

| File | Change |
|---|---|
| `backend/pom.xml` | Added `flyway-core` and `flyway-database-postgresql` (Flyway 10.20.1, version-managed by Spring Boot 3.4.5) |
| `backend/src/main/resources/db/migration/V1__baseline_existing_schema.sql` | **New.** Baseline of the entire pre-Flyway schema: 39 tables, 39 PKs, 59 FKs, 14 secondary indexes, 17 unique constraints, 14 CHECKs, 38 identity columns |
| `backend/src/main/resources/db/migration/V2__create_staff_profiles.sql` | **New.** Additive `staff_profiles` table, exactly the schema certified in `PHASE-7P.5-REPORT.md` §8 |
| `backend/src/main/resources/application.yml` | Flyway enabled; `baseline-on-migrate: true`, `baseline-version: 1`, `validate-on-migrate: true`, `out-of-order: false` |
| `backend/src/main/resources/application-dev.yml` | Flyway **disabled** — that profile is an ephemeral in-memory H2 scratch DB built by `create-drop` |
| `docs/database-migrations.md` | **New.** How to apply migrations in dev / test / prod, plus the rules for adding new ones |
| `README.md` | One link to the new migrations doc |

Division of responsibility is now explicit:

* **Flyway** creates, alters and drops schema objects.
* **Hibernate** only *validates* that the schema matches the entities
  (`spring.jpa.hibernate.ddl-auto=validate`), so a missing migration fails at startup
  rather than at the first request.
* Seed/business data still belongs to `DataInitializer`, never to migrations.

---

## 2. How the baseline works

The repository had no migration tool: schema had been accumulated by Hibernate
`ddl-auto=update` across many phases. Two situations had to be satisfied at once.

**Existing populated database.** Flyway finds a non-empty schema with no
`flyway_schema_history` and records it as the baseline at version 1. V1 is therefore
*skipped* — every object it defines already exists, so nothing is recreated and nothing
is dropped. V2 onwards then apply normally.

**Brand-new empty database.** Flyway does not baseline an empty schema; it runs V1 then V2
from scratch, producing the full schema, after which Hibernate `validate` confirms the
result.

Observed on the local database:

```
Schema history table "public"."flyway_schema_history" does not exist yet
Successfully validated 2 migrations
Creating Schema History table "public"."flyway_schema_history" with baseline ...
Current version of schema "public": 1
Migrating schema "public" to version "2 - create staff profiles"
Successfully applied 1 migration to schema "public", now at version v2
```

Resulting history — note V1 is a `BASELINE` row, not an executed script:

```
 installed_rank | version |                description                |   type   | success
----------------+---------+-------------------------------------------+----------+---------
              1 | 1       | Pre-Flyway schema baselined by Phase 7D.0 | BASELINE | t
              2 | 2       | create staff profiles                     | SQL      | t
```

### Why V1 was generated, not hand-written

V1 was produced from the live schema with
`pg_dump --schema-only --no-owner --no-privileges --no-comments`, so it reflects exactly
what Hibernate had actually created, including 17 Hibernate-generated unique-constraint
names, 14 CHECK constraints and 38 identity columns that would be easy to get wrong by
hand. `staff_profiles` was deliberately excluded — V2 owns it.

### A note on `IF NOT EXISTS` in V2

V2 uses `CREATE TABLE IF NOT EXISTS staff_profiles`. This is **not** a substitute for
Flyway history, and it is deliberately **not** implemented as a startup check in
application code. Flyway records V2 in `flyway_schema_history`, so on any given database
it executes exactly once. The guard exists only so that long-lived development databases
built by the old `ddl-auto=update` path — which already contain `staff_profiles` with real
rows — can adopt Flyway without that table being dropped and recreated. All schema-change
decisions live in the versioned migration directory; no migration logic was added to any
Java class.

---

## 3. Verification

### Clean database from scratch, with `validate` only

Created an empty `placement_fresh` database and booted against it with
`SPRING_JPA_HIBERNATE_DDL_AUTO=validate` (production-equivalent; the `local` profile's
`update` was explicitly overridden):

```
Schema history table "public"."flyway_schema_history" does not exist yet
Successfully validated 2 migrations
Creating Schema History table "public"."flyway_schema_history" ...
Migrating schema "public" to version "1 - baseline existing schema"
Migrating schema "public" to version "2 - create staff profiles"
Successfully applied 2 migrations to schema "public", now at version v2
Started PlacementPortalApplication in 10.069 seconds
Seed data initialized successfully.
```

Hibernate `validate` passed with no schema-validation errors, which proves V1 + V2
reproduce a schema that satisfies every entity mapping.

**Column-level equivalence proof.** All tables, columns, types, lengths and nullability
were compared between the pre-existing database and the from-scratch database:

```
columns in ORIGINAL (excl flyway) : 337
columns in FRESH    (excl flyway) : 337
=== COLUMN-LEVEL DIFF (empty = identical) ===
  IDENTICAL - every table, column, type, length and nullability matches
```

The fresh database ended with 41 tables (40 application + `flyway_schema_history`), and
`staff_profiles` was created exactly as certified:

```
 id              | bigint          | not null | generated by default as identity
 user_id         | bigint          | not null |
 phone           | character varying(25)
 designation     | character varying(120)
 office_location | character varying(150)
 bio             | character varying(1000)
 linkedin_url    | character varying(500)
 expertise       | jsonb
 created_at      | timestamp(6) without time zone
 updated_at      | timestamp(6) without time zone
Indexes:  "staff_profiles_pkey" PRIMARY KEY  |  "uq_staff_profiles_user" UNIQUE CONSTRAINT
Foreign-key constraints:  "fk_staff_profiles_user" -> users(id)
```

The scratch database was dropped afterwards.

### Idempotency across restarts

Second boot against the already-migrated local database:

```
Successfully validated 3 migrations
Current version of schema "public": 2
Schema "public" is up to date. No migration necessary.
Seed data already exists. Skipping initialization.
```

### Existing data preserved

Counted before any change and again after the migration and the full test run:

| Table | Before | After |
|---|---|---|
| `users` | 1711 | 1711 |
| `student_profiles` | 1690 | 1690 |
| `messages` | 203 | 203 |
| `staff_profiles` | 2 | 2 |
| `departments` | 10 | 10 |
| `audit_logs` | 1858 | 1858 |
| `companies` | 40 | 40 |
| `placement_drives` | 75 | 75 |
| public tables | 40 | 41 (+ `flyway_schema_history`) |

Both `staff_profiles` rows survived with byte-identical content:

```
 user_id | designation           | expertise
---------+-----------------------+--------------------------------------------------------------
 1       | Placement Officer     | NULL
 2       | Placement Coordinator | ["Campus Recruitment","Java","SQL","Technical Recruiting","Interview Panels"]
```

No seed rerun: the existing database logged `Seed data already exists. Skipping
initialization.`, while the genuinely new database seeded exactly once
(`Initializing seed data... initialized successfully.`).

### Runtime smoke test after migration

Login, `GET /api/profile/me`, and `PUT /api/profile/me/staff` all succeeded against the
migrated database, and the PC expertise data was restored to its pre-test value.

### No secrets in migrations

Scanned both migration files for passwords, secrets, tokens, API keys, credentials and
connection strings. The only matches are the column *name* `password_hash` in the
baseline DDL and a comment stating that no credentials appear. No values, no hostnames,
no `jdbc:` URLs.

---

## 4. Applying migrations

Full detail is in `docs/database-migrations.md`. Summary:

* **Development** — just run the app; Flyway migrates on startup. Inspect state with
  `SELECT * FROM flyway_schema_history ORDER BY installed_rank;`
* **Test** — `./mvnw test`. Stop any running instance first, otherwise the test context
  contends for port 8080 and the same database.
* **Production** — build, deploy, start. Flyway migrates, then Hibernate validates. No
  manual SQL step, in any environment.

New migration: add `V<n>__snake_case_description.sql` to
`backend/src/main/resources/db/migration/`. Keep it additive; never edit or delete a
migration that has been applied anywhere (`validate-on-migrate` checksums it); put no
credentials in SQL; put no seed data in migrations.

---

## 5. Known limitations

* Flyway 10.20.1 logs `Flyway upgrade recommended: PostgreSQL 18.3 is newer than this
  version of Flyway and support has not been tested. The latest supported version of
  PostgreSQL is 17.` Migrations applied and validated correctly against 18.3, but this is
  an upstream support-matrix warning worth revisiting when Flyway updates.
* The pre-existing `DatabaseMigration` `CommandLineRunner` (44 ad-hoc `ALTER` /
  `CREATE TABLE IF NOT EXISTS` statements from earlier phases) was deliberately left
  untouched, since removing it would change runtime behaviour. It is now largely
  redundant for anything Flyway owns, and is the natural candidate for a follow-up phase
  to retire in favour of versioned migrations.
* `dev` (H2 in-memory) has Flyway disabled by design, so it is not a migration test
  environment. Use `local` or a real PostgreSQL database for that.

---

## 6. Final report

```
FLYWAY ENABLED:               PASS
EXISTING DB BASELINE:          PASS
STAFF_PROFILE MIGRATION:       PASS
DDL-AUTO VALIDATE:             PASS
EXISTING DATA PRESERVED:       PASS
CLEAN START:                   PASS
BACKEND TESTS:                 116 / 116
SEED CHANGED:                  NO
BUSINESS LOGIC CHANGED:        NO
COMMIT:                        DO NOT COMMIT
```

`HEAD` remains `3344754`. Nothing was committed.
