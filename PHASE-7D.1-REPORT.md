# Phase 7D.1 — Retire the Legacy `DatabaseMigration` Startup Runner

**Status: COMPLETE**
**Date: 2026-09-26**
**Branch HEAD: `3344754` (unchanged — no commit made)**

---

## Summary

The legacy `DatabaseMigration` `CommandLineRunner` — which issued 44 hand-written DDL
statements against PostgreSQL on **every** application boot — has been deleted. Flyway is
now the only component that can change a PostgreSQL schema. All 44 statements were audited
and every one of their effects was already represented in the frozen `V1`/`V2` migrations,
so nothing was lost and **no `V3` was required**.

`application-local.yml` was moved off `ddl-auto=update` to `validate`, so Hibernate can no
longer silently mutate the development schema. Startup on both the populated database and a
brand-new empty database was verified, the two schemas were compared object-by-object, and
two separate failure-injection tests confirmed that drift is rejected loudly rather than
repaired.

---

## Required Report Fields

| Field | Value |
|---|---|
| **LEGACY RUNNER** | `DatabaseMigration` **DELETED** — `backend/src/main/java/com/college/placement/config/DatabaseMigration.java` |
| **DATABASEMIGRATION AUDITED** | **YES** — all 44 statements, one by one |
| **STATEMENTS AUDITED** | **44** |
| **ALREADY COVERED** | **44** (100%) — every effect present in `V1` |
| **MISSING** | **0** |
| **V3 REQUIRED** | **NO** — no gap found, so no `V3__*.sql` was created |
| **ACTIVE STARTUP DDL REMAINING** | **0** |
| **V1/V2 MODIFIED** | **NO** — byte-identical, hashes re-verified after all work |
| **MIGRATIONS APPLIED** | Authoritative DB: `[V1 (baseline), V2]`, no-op on restart. Empty DB: `[V1, V2]` applied fresh. |
| **SEEDING** | Unchanged and still separate — `DataInitializer` et al. run only after Flyway **and** Hibernate validate; skipped on the populated DB, ran once on the empty DB |
| **DATA CHANGED** | **NO** — all 10 tracked table counts and the `staff_profiles` fingerprint identical before and after |
| **BUSINESS LOGIC CHANGED** | **NO** — no entity, repository, service, controller, or seeder edits |
| **H2 EXCEPTION** | Documented in `docs/database-migrations.md` — `dev` profile only, in-memory, `create-drop`, Flyway disabled, unreferenced by build/tests/deploys |
| **STAFF_PROFILE MIGRATION OWNERSHIP** | **`V2` only** |
| **DOCUMENTED** | **YES** — `docs/database-migrations.md` |
| **BUILD** | **PASS** — `mvnw -o clean test` → `BUILD SUCCESS` (01:09) |
| **TESTS** | **PASS** — **116/116**, 0 failures, 0 errors, 0 skipped |
| **OPEN ISSUES** | 2 non-blocking, both recorded below |

---

## 1. Legacy runner audit

`DatabaseMigration` was a `@Component`-annotated `CommandLineRunner` with `@Order(0)` that
ran before every other runner on every boot, logging `Database migration completed`.

| Category | Statements | Disposition |
|---|---|---|
| `ALTER COLUMN ... TYPE` widening | 23 | A — already in V1 |
| `student_access_codes` nullability + 4 added columns | 6 | A — already in V1 |
| Secondary indexes (`idx_*`, `uq_sac_register_number`) | 11 | A — already in V1 |
| `CREATE TABLE` (`prep_modules`, `prep_topics`, `prep_questions`, `student_prep_progress`) | 4 | A — already in V1 |
| **Total** | **44** | **44 A / 0 B / 0 C / 0 D** |

The runner's work had already been baked into the live database, so `V1` — a `pg_dump` of
that same schema — necessarily reproduces all of it. Nothing the runner did was unique to
the runner.

Audit artifacts: `7d1-audit.ps1`, `7d1-audit.csv`.

### Retirement

- File deleted. It had **no references** anywhere else in the codebase.
- Post-deletion scan of `src/main/java`: **zero** `CREATE TABLE` / `ALTER TABLE` /
  `CREATE INDEX` / `DROP TABLE` / `DROP COLUMN` occurrences.
- The other runners (`DataInitializer`, `BulkSeedDataInitializer`, `PrepContentSeeder`, and
  the Mongo seeders) contain no PostgreSQL DDL.
- Verified the delete is real and not shadowed by a stale build artifact: after
  `mvn clean`, no `DatabaseMigration.class` exists anywhere under `backend/target`.

### Configuration

| Profile | `ddl-auto` | Flyway | Note |
|---|---|---|---|
| `application.yml` (default) | `validate` | enabled | env-overridable |
| `application-local.yml` | `update` → **`validate`** | enabled | **the behavioural change of this phase** |
| `application-prod.yml` | `${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}` | enabled | default `validate` retained |
| `application-dev.yml` | `create-drop` | disabled | H2 exception, see §6 |

---

## 2. V1 / V2 immutability

Neither file was edited. Verified by SHA-256 at the start and again at the end of the phase:

| File | SHA-256 (first 16) | Size | Owner of `staff_profiles` |
|---|---|---|---|
| `V1__baseline_existing_schema.sql` | `E9C0A70294F16CE0` | 42,111 B | **excluded** |
| `V2__create_staff_profiles.sql` | `D67A2D445ECE2EDF` | 1,750 B | **creates it** |

`V1` is a 39-table baseline. Its only mention of `staff_profiles` is the comment
`-- staff_profiles is deliberately absent from this file: it is owned by V2.`
`V2` contains the sole `CREATE TABLE IF NOT EXISTS staff_profiles`.

`staff_profiles` therefore has an unambiguous single owner. On pre-Flyway databases the
table already existed from the old `ddl-auto=update`, so V2's `IF NOT EXISTS` guard makes
it a no-op and the existing rows survive — which is exactly why the guard is there.

Recorded history on the authoritative database:

```
1 | BASELINE | (no checksum)          | Pre-Flyway schema baselined by Phase 7D.0 | t
2 | SQL       | -1844983327           | create staff profiles                     | t
```

---

## 3. Startup verification

### Existing populated database (`placement_portal`)

Two consecutive cold boots plus a final post-change boot, all identical in behaviour:

```
Successfully validated 3 migrations
Schema "public" is up to date. No migration necessary.
Initialized JPA EntityManagerFactory for persistence unit 'default'
Started PlacementPortalApplication
Seed data already exists. Skipping initialization.
```

**No** `Database migration step`, **no** `ALTER TABLE`, **no** `CREATE INDEX` — confirmed by
grepping the boot logs. Startup order is DataSource → Flyway → Hibernate validate →
beans → seeders.

### Brand-new empty database

`placement_clean` was created empty (0 tables) and pointed at the app:

```
Migrating schema "public" to version "1 - baseline existing schema"
Migrating schema "public" to version "2 - create staff profiles"
Successfully applied 2 migrations to schema "public", now at version v2
Initialized JPA EntityManagerFactory for persistence unit 'default'
Started PlacementPortalApplication
Initializing seed data... / Seed data initialized successfully.
```

Flyway alone built the entire schema; Hibernate then confirmed it matched the entities.
The database was dropped afterwards.

---

## 4. Comprehensive schema comparison

Authoritative vs freshly-migrated, across 8 object classes:

| Check | Authoritative | Clean | Result |
|---|---|---|---|
| Tables | 40 | 40 | **MATCH** |
| Columns (name, type, length, nullability, default) | 347 | 347 | **MATCH** |
| Primary keys | 42 | 42 | **MATCH** |
| Foreign keys | 60 | 60 | **MATCH** |
| Unique constraints | 22 | 22 | **MATCH** |
| Check constraints | 172 | 172 | *benign text diff* — see below |
| Indexes | 72 | 72 | *benign name diff* — see below |
| Identity columns | 39 | 39 | **MATCH** |

Two checks differed textually. Both were investigated rather than waved away, and both are
provably cosmetic:

1. **Check constraints** — same count, same tables, same columns. The difference is only how
   PostgreSQL serialises the same expression depending on whether Hibernate or `pg_dump`
   created it:
   - live: `role::text = ANY (ARRAY[('PO'::character varying)::text, ...])`
   - V1: `role::text = ANY ((ARRAY['PO'::character varying, ...])::text[])`

   Extracting the enforced column and value set from each constraint independently of SQL
   text gives **55 constraints on both sides, identical**. Same rules.

2. **Indexes** — all 72 index definitions are identical when compared by
   *(table, columns, uniqueness)*. The only name-level difference in the entire schema is the
   `staff_profiles` unique index: `ukiqbe2ysx5v1al3px8acr6l03a` (Hibernate-generated hash on
   the legacy table) vs `uq_staff_profiles_user` (V2's name on the fresh one). All other 72
   index names match exactly. Constraint and index *names* carry no semantics and are not
   validated by Hibernate, so this is a naming artefact of the table's origin, not drift.

**Verdict: the freshly migrated schema is equivalent to the live schema.**

---

## 5. Failure-injection tests (disposable database)

Two independent drift scenarios were induced to prove the schema is genuinely guarded.

### 5a. Hibernate structural validation

A mapped column was dropped from `placement_clean.users`:

```
org.hibernate.tool.schema.spi.SchemaManagementException:
    Schema-validation: missing column [password_hash] in table [users]
```

Startup aborted before the context was built; the seeder never ran. No automatic repair.

### 5b. Flyway checksum validation

The recorded checksum for V2 was tampered with in `flyway_schema_history`:

```
org.flywaydb.core.api.exception.FlywayValidateException:
    Validate failed: Migrations have failed validation
    Migration checksum mismatch for migration version 2
```

Startup aborted. This is the mechanism that protects the immutability of `V1`/`V2`: editing
either file makes every existing database refuse to boot.

### 5c. Honest limitation discovered

While building these tests I found that Hibernate 6.6's `validate` is a **structural**
guard, not a full definition diff:

| Drift | Caught by `validate`? |
|---|---|
| Missing table | yes |
| Missing column | yes |
| Wrong column type | yes |
| Extra unmapped column | no |
| `varchar(255)` → `varchar(100)` | **no** |
| Column made nullable | **no** |

Both width and nullability changes were applied to the disposable database and the
application started successfully. This is expected Hibernate behaviour (it compares type
*category*, not width, and does not check nullability), but it means `validate` alone cannot
be trusted to catch every kind of drift. It is now documented in
`docs/database-migrations.md`, and it is the reason §4 compares a freshly migrated database
against the live one rather than relying on `validate` alone.

---

## 6. H2 exception

`application-dev.yml` is the only profile that is not Flyway-managed:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:placement_dev;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
```

Justified because the database is **in-memory and discarded on shutdown**, holds no data of
value, and V1's DDL is PostgreSQL-specific. The profile is not referenced by the build, the
test suite, or any deployment, and it is explicitly **not** a production-equivalent migration
test. This is now stated in `docs/database-migrations.md`.

---

## 7. Seeding remains separate

Unchanged in code and in ordering. Seeders are `CommandLineRunner` beans that execute only
after Flyway has migrated and Hibernate has validated, so a broken schema can never be
masked by seeding. Behaviour observed:

- populated DB → `Seed data already exists. Skipping initialization.`
- empty DB → `Initializing seed data...` → `Seed data initialized successfully.`

No seeder source file was modified.

---

## 8. Data preservation

Ten tables snapshotted before any 7D.1 work and re-checked after the full test suite:

| Table | Before | After | |
|---|---|---|---|
| `users` | 1711 | 1711 | ok |
| `student_profiles` | 1690 | 1690 | ok |
| `messages` | 203 | 203 | ok |
| `staff_profiles` | 2 | 2 | ok |
| `contact_requests` | 119 | 119 | ok |
| `audit_logs` | 1862 | 1862 | ok |
| `departments` | 10 | 10 | ok |
| `companies` | 40 | 40 | ok |
| `placement_drives` | 75 | 75 | ok |
| `message_recipients` | 47738 | 47738 | ok |

`staff_profiles` fingerprint intact: user 1 = PO with `NULL` expertise; user 2 = PC with the
same 5-tag expertise array.

---

## 9. Documentation

`docs/database-migrations.md` updated to reflect reality:

- new **"No Java schema mutation"** section recording the deletion and the 44-statement audit
- `V1`/`V2` marked frozen, with the `staff_profiles` ownership split and the cosmetic
  constraint-naming caveat
- `local` profile: explains the `update` → `validate` change and why
- new **"What `validate` does and does not catch"** table (the §5c limitation)
- new **"Seeding is not migration"** section with the true boot order
- **removed** the `./mvnw -o flyway:info` / `flyway:validate` commands carried over from
  7D.0 — they were never valid, because `flyway-maven-plugin` is not declared in `pom.xml`
  and cannot resolve offline. Verified: the goal fails with *"Plugin … could not be
  resolved"*. Replaced with the `flyway_schema_history` query and a note explaining why the
  goals are unavailable
- `flyway repair` explicitly documented as **not** a routine procedure

---

## 10. Build and tests

```
mvnw -o clean test
...
Tests run: 116, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time:  01:09 min
```

`clean` was used deliberately: it proves the build succeeds with the runner's source deleted
and guarantees no stale `DatabaseMigration.class` from an earlier compilation can be present
on the classpath. Confirmed absent from `backend/target` afterwards.

No frontend, shared, or build-config files were touched in this phase, so no frontend build
was required.

---

## 11. Open issues

**1. Flyway/PostgreSQL support matrix (pre-existing, non-blocking).**
Flyway 10.20.1 logs that PostgreSQL 18.3 is newer than its latest officially tested version
(17). All validation and migration runs succeed. Worth monitoring for a Flyway upgrade.

**2. Unexplained transient schema state on a disposable database (resolved, noted for honesty).**
The *first* `placement_clean` database was verified healthy (347 columns matching, clean
boot, seed OK) but was later found missing `password_hash` from `users` — a column `V1`
unambiguously creates at line 804. Because `V1` contains exactly one `CREATE TABLE
public.users`, that one `password_hash` reference and no `DROP COLUMN` anywhere, this could
not have been caused by the migration path. No command issued against that database dropped
the column, and the anomaly was **not reproducible**: the database was dropped, recreated
from empty, and re-migrated, producing a schema with all 9 `users` columns present and
`placement_portal` matching it exactly. All comparison and failure-injection results in this
report come from that verified rebuild. The authoritative database was never affected.
Flagging it because an unexplained schema mutation is worth knowing about, even though it
could not be reproduced and the disposable database no longer exists.

---

## 12. Deliverables

| Path | Change |
|---|---|
| `backend/src/main/java/com/college/placement/config/DatabaseMigration.java` | **deleted** |
| `backend/src/main/resources/application-local.yml` | `ddl-auto: update` → `validate` |
| `docs/database-migrations.md` | substantially updated (§9) |
| `PHASE-7D.1-REPORT.md` | this report |
| `V1__baseline_existing_schema.sql` | **unchanged** |
| `V2__create_staff_profiles.sql` | **unchanged** |

No commit was made. `HEAD` remains `3344754`.
