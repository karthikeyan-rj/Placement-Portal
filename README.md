````markdown
# Placement Management Portal

A full-stack college placement management and communication system built with **Spring Boot, React, PostgreSQL, and Supabase**.

## Features

- Role-based access for PO, PC, PR, and Students
- Student profile management
- Department management
- Placement Coordinator and Representative management
- Company and placement drive management
- Eligibility tracking
- Interview and placement history
- Role-based messaging
- Message acknowledgement and read tracking
- Reports and audit logs

## Tech Stack

### Backend
- Java 21
- Spring Boot
- Spring Security
- JWT
- Spring Data JPA
- Maven

### Frontend
- React
- Vite
- TypeScript
- Tailwind CSS
- Axios
- React Router

### Database
- PostgreSQL
- MongoDB (messaging store)
- Supabase

## Architecture

```text
React
  ↓
Spring Boot REST API
  ↓
Spring Security + JWT
  ↓
Spring Data JPA
  ↓
Supabase PostgreSQL
````

## Run the Project

### Backend

```bash
cd backend
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## Environment Variables

```env
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=
JWT_SECRET=
MONGODB_URI=mongodb://localhost:27017
MONGODB_DATABASE=placement_portal
APP_MESSAGING_STORAGE=mongo   # mongo (default) | postgres
APP_MONGODB_ENABLED=true
APP_MONGODB_REQUIRED=false    # true = fail startup if mongo is down/collections missing

# One-off maintenance tools (all default OFF, read-only unless noted)
MIGRATE_MESSAGES_TO_MONGO=false         # backfill PG -> Mongo (insert-missing only)
VALIDATE_MESSAGES_MIGRATION=false       # compare PG vs Mongo (read-only)
MONGO_PERF_PROBE=false                  # perf probe (read-only)
MIGRATE_MESSAGES_MONGO_TO_POSTGRES=false # reverse migration tool
MONGO_ROLLBACK_DRY_RUN=true             # run reverse migration without writing

VITE_API_BASE_URL=http://localhost:8080/api
```

Do not commit real credentials or `.env` files.

## Message storage

Messaging data is stored in **MongoDB** (authoritative). PostgreSQL keeps only the
certified historical snapshot and is used solely as rollback storage; normal traffic
never dual-writes. `APP_MESSAGING_STORAGE` selects the runtime store
(`mongo` default; `postgres` is the explicit, fully-flagged fallback and is logged
as `[MESSAGING] activeStorage=...` at startup).

At startup the app verifies Mongo reachability and the four required collections
(`messages`, `message_recipients`, `clarification_threads`, `clarification_entries`)
plus the on-demand `mongo_sequences`. Set `APP_MONGODB_REQUIRED=true` in production so a
down database aborts startup instead of failing lazily at request time.

## Roles

```text
Placement Officer
      ↓
Placement Coordinator
      ↓
Placement Representative
      ↓
Student
```

Public registration creates only **Student** accounts.

## Purpose

The project provides a centralized platform for managing placement activities, communication, student data, companies, drives, and placement progress.

```
```
