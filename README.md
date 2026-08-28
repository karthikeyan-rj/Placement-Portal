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

VITE_API_BASE_URL=http://localhost:8080/api
```

Do not commit real credentials or `.env` files.

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
