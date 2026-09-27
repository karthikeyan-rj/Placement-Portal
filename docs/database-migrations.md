# Database Migrations (Flyway)

Phase 7D.0 introduced Flyway so that **schema changes are versioned in code** and
production deployments no longer require anyone to hand-execute SQL before the
application starts. Phase 7D.1 then removed the last piece of Java-side schema mutation,
so Flyway is now the only thing that can change a PostgreSQL schema.

Division of responsibility:

| Concern | Owner |
|---|---|
| Creating / altering / dropping schema objects | **Flyway** (`backend/src/main/resources/db/migration`) |
| Checking that the schema matches the entities | **Hibernate** `spring.jpa.hibernate.ddl-auto=validate` |
| Business data | Application code / seeders — never migrations |

Hibernate never creates or modifies anything in `local`, `test`, or `prod`. It only
validates, so a missing migration fails fast at startup instead of at the first request.

### No Java schema mutation

`DatabaseMigration` — a `@Component` implementing `CommandLineRunner` that issued 44
`ALTER TABLE` / `CREATE INDEX` / `CREATE TABLE` statements on every boot — was **deleted**
in Phase 7D.1. Do not reintroduce it, or any equivalent startup DDL.

- All 44 of its effects are already represented in `V1` / `V2`, so nothing was lost.
- `src/main/java` contains **zero** `CREATE TABLE` / `ALTER TABLE` / `CREATE INDEX` /
  `DROP` statements against PostgreSQL.
- If you ever need a schema change, add a migration. Never patch the database from code.

---

## Migration files

| File | Purpose |
|---|---|
| `V1__baseline_existing_schema.sql` | Baseline of the entire pre-Flyway schema (39 tables). Lets a brand-new empty database be built by Flyway alone. |
| `V2__create_staff_profiles.sql` | Additive `staff_profiles` table for PC/PO professional profiles (Phase 7P.5). |

Naming is mandatory: `V<version>__<snake_case_description>.sql`, e.g.
`V3__add_placement_offers_index.sql`. Never edit or delete a migration that has already
been applied anywhere — `validate-on-migrate=true` checksums it and will refuse to start.
To undo a released migration, add a new, higher-versioned one.

`V1` and `V2` are **frozen**. Their checksums are recorded in `flyway_schema_history`;
editing either file makes every existing database fail validation at startup.

| Version | Owns |
|---|---|
| `V1` | 39 legacy tables. Deliberately **excludes** `staff_profiles`. |
| `V2` | `staff_profiles` only. |

> On a database created before Flyway, `staff_profiles` already exists from the old
> Hibernate `ddl-auto=update` behaviour, so V2's `CREATE TABLE IF NOT EXISTS` is a no-op
> and the existing rows are preserved. The constraint *names* on that pre-existing table
> are Hibernate-generated hashes rather than V2's `uq_staff_profiles_user` /
> `fk_staff_profiles_user`. This is cosmetic: same columns, same keys, same semantics.

---

## How each environment behaves

### Development (`local` profile, real PostgreSQL)

```bash
cd backend
./mvnw spring-boot:run
```

Flyway runs automatically at startup. On a database that predates Flyway it is
**baselined at version 1**: V1 is skipped because those objects already exist, and V2+
apply normally. Nothing is ever recreated or dropped.

`local` uses `ddl-auto=validate`. It previously used `update`, which let Hibernate quietly
mutate the schema at every boot and made drift invisible; that is exactly the behaviour
Flyway replaces, so it is no longer used anywhere against PostgreSQL.

Check state at any time:

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history ORDER BY installed_rank;
```

### Test

`./mvnw test` starts the same Spring context, so Flyway validates and migrates before the
tests run. Migrations are already applied and therefore a no-op. The suite uses
`@Transactional` fixtures that roll back, so it leaves no rows behind — verified after a
full run: all ten tracked table counts were byte-identical before and after.

> Run tests with the application **stopped**. A running instance holding port 8080 will
> make the test context contend for the same port and database.

### Production (`prod` profile)

```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<db>
export DATABASE_USERNAME=...
export DATABASE_PASSWORD=...          # or SPRING_DATASOURCE_PASSWORD
java -jar target/placement-portal-0.0.1-SNAPSHOT.jar
```

Order of operations on boot:

1. Flyway validates `flyway_schema_history`, applies any pending migrations in a
   transaction, and commits.
2. Hibernate runs `validate` and aborts startup if the resulting schema does not match
   the entities.
3. Only once the context is up do the `CommandLineRunner` seeders run
   (`DataInitializer`, `BulkSeedDataInitializer`, `PrepContentSeeder`).

So the deployment sequence is: **build → deploy → Flyway migrates → Hibernate validates →
seeders fill reference data → app serves traffic.** No manual SQL step.

### Seeding is not migration

Seed data lives in Java (`DataInitializer` and friends), never in a `.sql` file, and it only
runs after the schema is known good — so a failed migration can never be papered over by a
seeder. Each seeder is idempotent by checking for existing rows and logging
`Seed data already exists. Skipping initialization.` on a populated database, which is what
a restart against `placement_portal` does. The MongoDB seeders are likewise unrelated to the
PostgreSQL schema.

### `dev` profile (ephemeral H2)

The `dev` profile is an **in-memory H2 scratch database** (`jdbc:h2:mem:...`) that Hibernate
builds with `create-drop` on every run and discards on shutdown. Flyway is **disabled**
there: V1 is PostgreSQL-specific and this data is not worth versioning.

This is the single documented exception to "Flyway owns the schema", and it is safe only
because the database is ephemeral and holds no data of value. It is **not** referenced by
the build, the test suite, or any deployment, and it must never be treated as a
production-equivalent migration test — a change that works under H2 `create-drop` has
proved nothing about the PostgreSQL migrations. Use `local` for anything that needs real
migrations.

---

## Adding a migration

1. Create `backend/src/main/resources/db/migration/V<n>__description.sql`.
2. Write **additive, idempotent-by-version** SQL. Prefer `CREATE TABLE`,
   `ALTER TABLE ... ADD COLUMN`, `CREATE INDEX`. Avoid `DROP`, and avoid renaming or
   retyping a populated column in the same release that changes its meaning.
3. Never put credentials, tokens, hostnames, or connection strings in a migration.
   Seed data belongs in `DataInitializer`, not in SQL.
4. Restart the app (or run the tests). Flyway applies it; Hibernate validates the result.

### Starting a brand-new database from nothing

Create an empty database and point the app at it. Flyway applies V1 then V2, Hibernate
validates, and the normal seeders populate it. Do **not** pre-create tables.

---

## Verifying without deploying

Validation is **automatic**: every startup logs
`Successfully validated N migrations` before Hibernate runs, and a checksum mismatch
aborts the boot. To confirm the recorded state without starting the app:

```sql
SELECT installed_rank, version, description, type, checksum, success
FROM flyway_schema_history ORDER BY installed_rank;
```

A healthy database shows V1 and V2 with `success = true`, and V2's checksum matching the
file on disk.

> The `flyway:info` / `flyway:validate` Maven goals are **not** available in this project:
> `flyway-maven-plugin` is not declared in `backend/pom.xml`. Do not rely on them. If CLI
> goals are ever wanted, add the plugin to `pom.xml` first — an unconfigured goal fails
> with "could not be resolved", especially offline.

---

## What `validate` does and does not catch

Hibernate's `validate` compares the mapped entities against the live schema at startup and
aborts with `SchemaManagementException` when they disagree. Confirmed behaviour:

| Drift | Caught? |
|---|---|
| Missing table | yes |
| Missing column | yes — `Schema-validation: missing column [x] in table [y]` |
| Wrong column type | yes |
| Extra column not mapped by any entity | no |
| `varchar(255)` → `varchar(100)` | **no** — type *category* only, not width |
| Column changed to nullable | **no** |

So `validate` is a structural guard, not a full definition diff. Width and nullability
drift must be caught by review or by comparing a freshly migrated database against the
live one. Flyway's checksum validation, by contrast, is exact: any edit to an applied
migration file fails the boot with `Migration checksum mismatch for migration version N`.

---

## Recovering from a failed migration

Flyway runs each migration in a transaction, so a PostgreSQL failure rolls that migration
back and leaves the history table untouched. Fix the SQL, then restart.

If a migration was applied incorrectly and must be undone, do **not** edit history. Write a
compensating migration with the next version number, and repair data separately with a
reviewed, backed-up procedure.

`flyway repair` is **not** part of any routine procedure. It rewrites checksums, which
would permanently mask the fact that an applied migration no longer matches its file. Use
it only when a migration was genuinely edited on every environment *and* the change is
known to be behaviour-preserving, and record why.
