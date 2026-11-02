# TalkCare OOP & Data-Model Implementation Plan

> Companion document: [`schema.md`](schema.md) — target normalized PostgreSQL schema.

## 1. Verdict: Does the project use the four pillars?

**No, not fully.** The Java side is mostly an *anemic model + god class* design. It has some encapsulation, but abstraction, inheritance, and polymorphism are effectively absent.

| Pillar | Current state | Evidence |
|---|---|---|
| **Encapsulation** | Partial | `models/User.java`, `Report.java`, `Conversation.java`, `ChatMessage.java` use private fields with getters/setters, but there is no validation or behavior in the models. Mutable state is exposed through every setter, and `DatabaseManager` reads/writes the internals directly. |
| **Abstraction** | Missing | No interfaces or abstract classes. `DatabaseManager` (≈1,700 lines) is a single concrete class that owns connection management, schema migration, authentication, password hashing, reports, chat, admin operations, metrics, and row mapping. `TalkCareServer` manually parses JSON and hard-codes every endpoint. |
| **Inheritance** | Missing | There is one flat `User` class. Client, administrator, attorney/case handler, and therapist/psychiatrist differ only by the string in `users.role`. No shared `Account`/`Professional` contract exists. |
| **Polymorphism** | Missing | Behavior is selected with `if`/`else` and string comparisons, for example `normalizeRole(...)`, `requireRole(..., "ADMIN")`, `getRoleHomeView(role)` in `static/js/app.js`, and `role.equalsIgnoreCase("PROFESSIONAL")` in `DatabaseManager`. No overridden methods or dynamic dispatch. |

**Consequences:**
- Adding a new role means editing many unrelated branches in Java, SQL, and JavaScript.
- Role-specific data (bar roll number, PRC license, clinic, department) has nowhere to live; everything is stuffed into one `users` row.
- Authorization is scattered and easy to get wrong (`/api/conversations` still falls back to user id `2` when there is no session).
- `DatabaseManager` cannot be unit-tested without a real PostgreSQL instance.

The target design below fixes this with a **sealed account hierarchy**, **repository interfaces**, and a **class-table-inheritance schema** (`users` → `clients` / `professionals` → `attorneys` / `psychiatrists` / `admins`).

---

## 2. Target package structure

```text
com.talkcare
├── App.java
├── config/
│   └── AppConfig.java                 // DB/SMTP settings, no business logic
├── db/
│   ├── ConnectionProvider.java        // interface
│   ├── PostgresConnectionProvider.java
│   └── SchemaMigrator.java            // Flyway/Liquibase, not hand-split SQL
├── domain/
│   ├── account/
│   │   ├── Account.java               // abstract base
│   │   ├── Client.java
│   │   ├── Professional.java          // abstract
│   │   ├── Attorney.java
│   │   ├── Psychiatrist.java
│   │   ├── Administrator.java
│   │   ├── AccountFactory.java        // ResultSet/row -> correct subtype
│   │   └── Role.java, Permission.java, AccountStatus.java
│   ├── report/
│   │   ├── Report.java
│   │   ├── ReportStatus.java
│   │   └── ConcernType.java
│   ├── messaging/
│   │   ├── Conversation.java
│   │   ├── Message.java
│   │   └── Attachment.java
│   ├── scheduling/
│   │   ├── Appointment.java
│   │   └── AppointmentStatus.java
│   └── verification/
│       ├── IdentityVerification.java
│       └── VerificationStatus.java
├── repository/
│   ├── AccountRepository.java         // interfaces only
│   ├── ReportRepository.java
│   ├── ConversationRepository.java
│   ├── MessageRepository.java
│   ├── AppointmentRepository.java
│   ├── VerificationRepository.java
│   ├── NotificationRepository.java
│   └── ActivityRepository.java
├── repository/jdbc/
│   ├── JdbcAccountRepository.java
│   ├── JdbcReportRepository.java
│   └── ...                            // one implementation per interface
├── service/
│   ├── AuthService.java
│   ├── ReportService.java
│   ├── ChatService.java
│   ├── AppointmentService.java
│   ├── VerificationService.java
│   └── AdminService.java
├── web/
│   ├── TalkCareServer.java            // routing only
│   ├── ApiHandler.java
│   ├── JsonCodec.java                 // replace hand-written JSON strings
│   └── SessionStore.java
└── error/
    ├── AppException.java
    ├── ValidationException.java
    ├── AuthenticationException.java
    ├── ForbiddenException.java
    ├── NotFoundException.java
    ├── ConflictException.java
    └── PersistenceException.java
```

**Dependency rule:** `web → service → repository → db`. Domain classes never import JDBC, HTTP, or JavaFX.

---

## 3. Phase-by-phase implementation

### Phase 0 — Safety net (before refactoring)

- [ ] Add JUnit 5 + Testcontainers (PostgreSQL) to `pom.xml`.
- [ ] Write characterization tests for the current API:
  - login for all four seed roles,
  - registration creates a client,
  - report submit/list/update-status authorization,
  - conversation create, client message + auto-reply, professional reply,
  - admin pending-request actions.
- [ ] Add a Flyway (or Liquibase) migration folder. The current `executeSchema()` splits `schema.sql` on `;`, which cannot support PL/pgSQL functions/triggers. Do this before touching the schema.
- [ ] Tag the current commit/branch so the migration is reversible.

**Acceptance:** the existing app still builds and the characterization tests pass against the *current* schema.

### Phase 1 — Domain hierarchy (encapsulation + inheritance)

Create the account model. Keep fields private and make mutation intentional (`changePassword`, `rename`, `markVerified`) instead of public setters.

```java
public abstract class Account {
    private final int id;
    private final String username;
    private final String email;
    private String passwordHash;
    private AccountStatus status;

    protected Account(int id, String username, String email, String passwordHash, AccountStatus status) {
        this.id = id;
        this.username = Objects.requireNonNull(username);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.status = Objects.requireNonNull(status);
    }

    public abstract Role role();
    public abstract String dashboardView();
    public abstract Set<Permission> permissions();
    public abstract boolean canAccess(Conversation conversation);
    public abstract boolean canAccess(Report report);

    public final boolean can(Permission permission) {
        return permissions().contains(permission);
    }

    public void changePassword(String newHash) { this.passwordHash = Objects.requireNonNull(newHash); }
    public void markVerified() { this.status = AccountStatus.VERIFIED; }

    public int id() { return id; }
    public String username() { return username; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public AccountStatus status() { return status; }
}
```

```java
public final class Client extends Account {
    private final IdentityVerification verification;

    @Override public Role role() { return Role.CLIENT; }
    @Override public String dashboardView() { return "dashboard"; }
    @Override public Set<Permission> permissions() {
        return EnumSet.of(
            Permission.REPORTS_CREATE,
            Permission.REPORTS_READ_OWN,
            Permission.CONVERSATIONS_CREATE,
            Permission.CONVERSATIONS_READ_OWN,
            Permission.APPOINTMENTS_BOOK_OWN);
    }
    @Override public boolean canAccess(Conversation c) { return c.clientId() == id(); }
    @Override public boolean canAccess(Report r) { return r.clientId() == id(); }
}
```

```java
public abstract class Professional extends Account {
    private final String licenseNumber;
    private final boolean acceptingClients;

    public abstract ServiceType serviceType();
    public abstract String displayTitle();

    @Override public Set<Permission> permissions() {
        return EnumSet.of(
            Permission.CONVERSATIONS_READ_ASSIGNED,
            Permission.MESSAGES_REPLY,
            Permission.REPORTS_READ_ASSIGNED,
            Permission.APPOINTMENTS_MANAGE_ASSIGNED);
    }
    @Override public boolean canAccess(Conversation c) { return c.professionalId() == id(); }
    @Override public boolean canAccess(Report r) { return r.assignedProfessionalId() == id(); }
}
```

```java
public final class Attorney extends Professional {
    private final String barRollNo, lawFirm;
    @Override public Role role() { return Role.ATTORNEY; }
    @Override public ServiceType serviceType() { return ServiceType.JUSTICE_CARE; }
    @Override public String displayTitle() { return "Atty."; }
    @Override public String dashboardView() { return "case-handler-dashboard"; }
}

public final class Psychiatrist extends Professional {
    private final String prcLicenseNo, clinicName;
    @Override public Role role() { return Role.PSYCHIATRIST; }
    @Override public ServiceType serviceType() { return ServiceType.THOMATT; }
    @Override public String displayTitle() { return "Dr."; }
    @Override public String dashboardView() { return "professional-dashboard"; }
}
```

```java
public final class Administrator extends Account {
    private final String department;
    @Override public Role role() { return Role.ADMIN; }
    @Override public String dashboardView() { return "admin"; }
    @Override public Set<Permission> permissions() { return EnumSet.allOf(Permission.class); }
    @Override public boolean canAccess(Conversation c) { return true; }
    @Override public boolean canAccess(Report r) { return true; }
}
```

**Mapping:** `AccountFactory.fromRow(ResultSet)` reads `roles.code` and instantiates `Client`, `Attorney`, `Psychiatrist`, or `Administrator`.

**Acceptance:** all four seed accounts resolve to the correct subclass; no role-string branching remains in domain code.

### Phase 2 — Polymorphism (replace role `if` chains)

- [ ] Change `TalkCareServer.AuthSession` to hold `Account actor` instead of `(userId, name, role)`.
- [ ] Replace `requireRole(exchange, actor, "ADMIN")` with `actor.can(Permission.USERS_MANAGE)`.
- [ ] Replace `requireConversationAccess(...)` with `actor.canAccess(conversation)`.
- [ ] Replace `getRoleHomeView(role)` with `actor.dashboardView()` returned in the session JSON.
- [ ] Replace `normalizeRole(...)` string mapping with `Role.fromDatabaseCode(...)` plus a one-time migration.
- [ ] Serialize accounts with pattern matching:

```java
String toJson(Account a) {
    return switch (a) {
        case Attorney at    -> baseJson(a) + ",\"barRollNo\":" + q(at.barRollNo());
        case Psychiatrist ps -> baseJson(a) + ",\"prcLicenseNo\":" + q(ps.prcLicenseNo());
        case Administrator ad -> baseJson(a) + ",\"department\":" + q(ad.department());
        case Client c       -> baseJson(a);
    };
}
```

**Acceptance:** adding a fifth role requires a new subclass + migration; no edits to existing endpoint conditionals.

### Phase 3 — Repository + service extraction

Split `DatabaseManager` by responsibility:

| Current responsibility | New home |
|---|---|
| JDBC connect / create DB / config | `PostgresConnectionProvider`, `SchemaMigrator` |
| `executeSchema`, `migrateUsersSchema`, `seedRoleTestAccounts` | Flyway migrations + `SeedData` |
| `login`, `register`, password hashing/reset | `AuthService`, `PasswordHasher`, `AccountRepository` |
| `getUserById`, `updateProfile`, `updateIdVerification` | `AccountRepository`, `VerificationRepository` |
| `submitReport`, `getReports`, report status transitions | `ReportService`, `ReportRepository` |
| `getAdminPendingRequests`, `processAdminRequest`, metrics/activity | `AdminService`, `ActivityRepository` |
| conversations/messages | `ChatService`, `ConversationRepository`, `MessageRepository` |
| appointments | `AppointmentService`, `AppointmentRepository` |

Repository interfaces keep services testable with fakes; JDBC implementations own SQL and row mapping. Use transactions for multi-step operations (create conversation + welcome message, message + auto-reply, report + activity + notification).

**Acceptance:** no service class imports `java.sql.Connection`; `DatabaseManager` is deleted or reduced to a thin facade.

### Phase 4 — Schema migration (see `schema.md`)

- [ ] Apply target tables in a versioned migration.
- [ ] Backfill subtype rows:
  - existing `USER`/`CLIENT`/`PEER` → `CLIENT` + `clients` row,
  - `CASE_HANDLER`/`PROFESSIONAL` → `ATTORNEY` + `professionals` + `attorneys` rows,
  - `THERAPIST` → `PSYCHIATRIST` + `professionals` + `psychiatrists` rows,
  - `ADMIN` → `ADMIN` + `admins` row.
- [ ] Move identity fields out of `users` and `verification_requests` into `identity_verifications`.
- [ ] Replace `appointments.session_date` + `session_time` with `scheduled_start`/`scheduled_end`.
- [ ] Drop denormalized `messages.sender_name` / `sender_role` and join to `users`/`roles`.
- [ ] Keep a compatibility view (`v_users_legacy`) returning `CASE_HANDLER`/`THERAPIST`/`USER` strings while the UI is updated.
- [ ] Re-point seed accounts and passwords; keep the same test credentials documented in `README.md`.

**Acceptance:** data parity checks pass (same accounts, reports, conversations, messages, appointment dates); app works with legacy and new role codes during rollout.

### Phase 5 — Web/API cleanup

- [ ] Replace `parseJson`/manual string building with a small JSON library (Jackson or Gson).
- [ ] Remove unauthenticated fallbacks (`actor == null ? 2 : ...`, default ids) from report, conversation, message, and profile endpoints.
- [ ] Add a route table or handler registry instead of one giant `if/else` chain.
- [ ] Return stable error codes (`VALIDATION_ERROR`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`).
- [ ] Update `static/js/app.js` to consume `dashboardView` and permission flags from `/api/auth/login` instead of hard-coded role checks; replace `getRoleHomeView` with the server-provided route.

### Phase 6 — Frontend alignment

- [ ] Keep the existing HTML views (`view-professional-dashboard`, `view-case-handler-dashboard`, `view-admin`) but drive visibility from permissions.
- [ ] Rename UI labels consistently: **Attorney / Case Handler** (JusticeCare) and **Psychiatrist / Therapist** (ThoMatt).
- [ ] Show role-specific fields from the new profile tables (bar roll no. for attorneys, PRC license/clinic for psychiatrists).
- [ ] Add an admin user-management screen backed by `users`, `roles`, and subtype tables.

---

## 4. How Admin, Attorney, and Psychiatrist are handled

| Concern | Admin | Attorney (Case Handler) | Psychiatrist (Therapist) |
|---|---|---|---|
| Java class | `Administrator` | `Attorney extends Professional` | `Psychiatrist extends Professional` |
| Canonical role code | `ADMIN` | `ATTORNEY` | `PSYCHIATRIST` |
| Legacy code (migration) | `ADMIN` | `CASE_HANDLER`, `PROFESSIONAL` | `THERAPIST`, `PROFESSIONAL` |
| Service | — | `JUSTICE_CARE` | `THOMATT` |
| Shared table | `users`, `user_profiles` | `users`, `user_profiles`, `professionals` | `users`, `user_profiles`, `professionals` |
| Specialized table | `admins` | `attorneys`, `attorney_practice_areas` | `psychiatrists`, `psychiatrist_specializations` |
| Key attributes | department, job title | bar roll no., IBP no., law firm, jurisdiction | PRC license no., license expiry, clinic, medication management |
| Permissions | all | read assigned conversations/reports, reply, manage assigned appointments | same, scoped to mental-health cases |
| Dashboard view | `admin` | `case-handler-dashboard` | `professional-dashboard` |
| DB guard | trigger: `users.role = ADMIN` | trigger: role `ATTORNEY` + `professionals.professional_type = ATTORNEY` | trigger: role `PSYCHIATRIST` + `professionals.professional_type = PSYCHIATRIST` |

Authorization is **permission-based**, not role-name-based, so `Administrator` simply has every permission while the two professional subclasses share the professional permission set but differ in `serviceType()` and `canAccess(...)`.

---

## 5. Migration sequence and risk controls

1. **Add** new tables/columns and triggers; do not drop anything.
2. **Backfill** users, profile rows, professionals, attorneys, psychiatrists, admins, and identity verifications.
3. **Dual-read** in code (legacy role string OR new role table); write only to the new model.
4. **Verify** with SQL parity checks (`schema.md` §7) and the characterization tests.
5. **Switch** the API/UI to new role codes and permissions.
6. **Drop** legacy columns/tables in a later release.

**Risks and mitigations**

| Risk | Mitigation |
|---|---|
| `executeSchema()` splits on `;` and breaks PL/pgSQL | Adopt Flyway before adding triggers |
| Existing test accounts stop logging in | Keep credentials, backfill hashes unchanged |
| UI depends on `CASE_HANDLER`/`THERAPIST` strings | Compatibility view + server-provided `dashboardView` |
| Duplicate conversations after unique index | De-duplicate/open-close rows in migration before adding index |
| Report history loses reporter contact | Optional `report_snapshots` (documented intentional denormalization) |
| Password reset / sessions break | Keep `AuthService` API stable while moving implementation |

---

## 6. Definition of done

- [ ] No domain/service class compares role strings; authorization uses `Account.can(permission)` / `canAccess(...)`.
- [ ] `DatabaseManager` is gone or < 100 lines; each repository has an interface and a JDBC implementation.
- [ ] All four roles are created through factories and persisted across `users` + subtype tables.
- [ ] `schema.md` DDL applies cleanly from an empty database and from the current database.
- [ ] Existing test accounts and documented flows still work.
- [ ] New tests cover: role/subtype creation, permission denial, conversation scope, appointment double-booking, verification approval, report status transitions.
- [ ] README documents the role rename (`CASE_HANDLER → ATTORNEY`, `THERAPIST → PSYCHIATRIST`, `USER → CLIENT`) and the migration command.

## 7. Explicit non-goals

- Rewriting the JavaFX/WebView shell.
- Replacing the embedded `com.sun.net.httpserver` server with Spring Boot.
- Splitting the monolith into microservices.
- Changing the visual design/wireframe fidelity.

These can follow once the domain and schema are stable.
