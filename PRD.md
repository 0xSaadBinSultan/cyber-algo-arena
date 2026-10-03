# Product Requirements Document — Cyber-Algo Arena

## 1. Product objective

Cyber-Algo Arena is a Java 21 web application for running Capture The Flag (CTF) and Competitive Programming (CP) activities from one platform. It supports player accounts, teams, contests, challenge delivery, submissions, scoring, profiles, administration, persistent storage, and external contest feeds.

## 2. Technology baseline

- Java 21
- Maven
- Javalin 6
- MongoDB using the official synchronous Java driver
- BCrypt password hashing
- Piston API for sandboxed CP execution
- Docker / Docker Compose

## 3. Functional requirements

### Identity and access
- Players can register, log in, log out, and view profile data.
- Passwords are stored as BCrypt hashes.
- Administrative routes require the ADMIN role.
- The application contains no default administrator password.
- Initial administrator creation is opt-in through runtime configuration.

### Teams and contests
- Players can create and join teams.
- Team membership and contest participation are persisted.
- A player cannot participate under multiple teams in the same contest.

### CTF challenges
- Support category, difficulty, points, descriptions, attachments, hints, and flag verification.
- Raw flags should not be persisted when a cryptographic verifier can be stored instead.
- Secret comparisons should use timing-resistant comparison techniques where appropriate.

### Competitive Programming
- Accept supported language submissions.
- Execute code using an isolated judge service.
- Compare output with configured test cases.
- Persist submission status and awarded points.

### Scoring and profiles
- Track solves, wrong attempts, hints, first blood, and points.
- Maintain team standings and individual CTF/CP statistics.

### Administration
- Create and delete challenges.
- Modify challenge points.
- Inspect submissions.
- Freeze scoreboard state.
- Trigger supported synchronization tasks.

### Contest radar
- Aggregate upcoming CTF and CP event information.
- Cache upstream data and degrade gracefully when an external feed is unavailable.

## 4. Persistence requirements

MongoDB is the canonical persistent store for users, teams, challenges, contests, participation records, and submissions.

In-memory state may be used as a resilience fallback, but readiness must report MongoDB as unavailable while persistent storage cannot be reached.

## 5. Security requirements

- No hard-coded production credentials.
- Administrator bootstrap uses runtime configuration and minimum password-strength rules.
- Existing administrator passwords are never reset automatically at startup.
- Administrative endpoints enforce server-side RBAC.
- Authentication and submission endpoints are rate-limited.
- Attachment paths are normalized and constrained to the attachment directory.
- Security headers are applied by the web layer.
- Local secret/environment files are excluded from version control.

## 6. Health and operations

- `/api/health/live`: process liveness.
- `/api/health/ready`: MongoDB-backed readiness.
- `/api/health`: aggregate status.

Docker orchestration waits for MongoDB health and checks application readiness after startup.

## 7. Source and build standards

- Main sources: `src/main/java/com/cyberalgo`.
- Tests: `src/test/java/com/cyberalgo`.
- Java 21 is the build/runtime baseline.
- `./mvnw verify` is the primary local and CI quality gate.
- GitHub Actions runs build/test and security analysis.
- Dependabot maintains Maven, GitHub Actions, and Docker dependencies.

## 8. Non-functional requirements

- Avoid silent credential mutation.
- Keep integrations isolated behind service classes.
- Preserve separation between web routing, domain logic, persistence, and external services.
- Fail safely when external dependencies are unavailable.
- Do not expose secrets through logs or API responses.

## 9. Future AI extension

AI tutoring can be added after this baseline. AI requests must contain only sanitized public challenge context and user-provided code/error information. Flags, hidden test outputs, admin data, database credentials, and API keys remain server-side.
