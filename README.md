# Cyber-Algo Arena

Cyber-Algo Arena is a Java 21 club-practice platform that combines Capture The Flag (CTF) challenges and Competitive Programming (CP) problems under one member profile. It includes authentication, teams, contests, scoring, profiles, an admin portal, MongoDB persistence, cloud code execution through Piston, and live contest feeds.

## Architecture

```text
Browser / SPA
     |
     v
Javalin REST API
     |
     +-- ContestEngine
     |    +-- CTF challenge evaluation
     |    +-- CP judging orchestration
     |    +-- scoring / leaderboard / profiles
     |
     +-- MongoRepository --> MongoDB
     +-- PistonJudgeEngine --> Piston API
     +-- ContestRadarService --> external contest feeds
```

Java sources now use the standard Maven package layout:

```text
src/
├── main/java/com/cyberalgo/
└── test/java/com/cyberalgo/
```

## Requirements

- Java JDK 21+
- Docker + Docker Compose for containerized development
- Maven is optional because the Maven Wrapper is included

## Secure first startup

There is **no built-in administrator password**.

Before the first startup, set a unique administrator password of at least 12 characters:

```bash
export ARENA_ADMIN_USERNAME=admin
export ARENA_ADMIN_PASSWORD='replace-with-a-unique-strong-password'
```

The bootstrap credential is used only if the configured administrator account does not already exist. Startup does **not** reset an existing administrator password.

Never commit real credentials to the repository.

## Docker quickstart

```bash
git clone https://github.com/0xSaadBinSultan/cyber-algo-arena.git
cd cyber-algo-arena

export ARENA_ADMIN_PASSWORD='replace-with-a-unique-strong-password'
docker compose up --build -d
```

Open `http://localhost:8080`.

The development Compose file binds MongoDB to localhost rather than all interfaces.

## Local development

```bash
export MONGODB_URI=mongodb://localhost:27017
export MONGODB_DATABASE_NAME=cyber_algo_arena
export ARENA_ADMIN_PASSWORD='replace-with-a-unique-strong-password'

./mvnw clean verify
java -jar target/cyber-algo-arena-1.0.0.jar
```

Run the lifecycle demo suite:

```bash
java -cp target/cyber-algo-arena-1.0.0.jar com.cyberalgo.App --demo
```

## Health endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/health/live` | Process liveness. Returns 200 while the application is running. |
| `GET /api/health/ready` | MongoDB readiness. Returns 200 when MongoDB responds, otherwise 503. |
| `GET /api/health` | Aggregate human-readable health status. |

## Main REST API

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Create a player account |
| POST | `/api/auth/login` | Authenticate |
| POST | `/api/auth/logout` | End the session |
| GET | `/api/auth/me` | Current user |
| GET | `/api/challenges` | List challenges |
| GET | `/api/challenges/{id}` | Challenge details |
| POST | `/api/hints/{challengeId}` | Unlock a hint |
| POST | `/api/submit` | Submit a flag or CP solution |
| GET | `/api/leaderboard` | Competition standings |
| POST | `/api/teams/create` | Create a team |
| POST | `/api/teams/join` | Join a team |
| GET | `/api/contests` | List contests |
| GET | `/api/events/upcoming` | Upcoming contest events |
| GET | `/api/admin/submissions` | Admin submission audit |
| POST | `/api/admin/challenges` | Create a challenge |
| DELETE | `/api/admin/challenges/{id}` | Delete a challenge |

Administrative routes require an authenticated user with the `ADMIN` role.

## Security controls

- BCrypt password hashing
- No hard-coded administrator password or password aliases
- Runtime-only administrator bootstrap
- Existing admin credentials are never silently overwritten
- Rate limiting for sensitive endpoints
- Server-side admin RBAC
- Timing-resistant comparisons where secret verification is required
- Path traversal protection for attachment downloads
- CSP and defensive response headers
- Mongo-aware readiness checks
- Local secret files excluded from version control

## Club practice model

The platform is designed for a university or community club that publishes new CP and CTF practice problems each week.

- Members use one account/profile for both tracks.
- Personal totals, CP score/solves, CTF score/solves, categories, and solved-problem history are tracked together.
- Team membership is optional for practice. Team scoreboards remain available for group competitions.
- Administrators publish CP/CTF problems from `/admin`.
- CP problems require at least one public sample and one hidden testcase.
- Hidden CP testcase inputs/outputs are persisted server-side and are never included in public challenge responses.

## CP judge

CP source submissions support C++, Java, and Python. The Piston-compatible judge enforces configured wall/CPU time and memory limits and returns distinct verdicts for Accepted, Wrong Answer, Time Limit Exceeded, Memory Limit Exceeded, Runtime Error, and Compilation Error.

For a real public deployment, configure `PISTON_URL` to an authorized or self-hosted Piston-compatible execution service. Do not rely on an unauthenticated public endpoint.

## Gemini AI Tutor

The challenge modal includes a server-side Gemini tutor with progressive Hint 1/2/3 and Explain modes. The browser never receives the Gemini API key, CTF flag hashes, or hidden testcase data. Configure `GEMINI_API_KEY` and optionally `GEMINI_MODEL` on the server.

## Configuration

| Variable | Purpose | Default |
|---|---|---|
| `PORT` | HTTP port | `8080` |
| `MONGODB_URI` | MongoDB connection URI | local discovery fallback |
| `MONGO_URI`, `MONGO_URL`, `MONGODB_URL` | Accepted MongoDB URI aliases | optional |
| `MONGODB_DATABASE_NAME` | MongoDB database name | `cyber_algo_arena` |
| `ARENA_ADMIN_USERNAME` | Bootstrap admin username | `admin` |
| `ARENA_ADMIN_PASSWORD` | Bootstrap admin password | none |
| `PISTON_URL` | Authorized/self-hosted Piston-compatible judge base URL | public fallback (not recommended for production) |

## Repository layout

```text
cyber-algo-arena/
├── .github/
├── contest_data/
├── public/
├── scripts/
├── src/
│   ├── main/java/com/cyberalgo/
│   └── test/java/com/cyberalgo/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── PRD.md
└── README.md
```

Temporary root-level patch scripts from earlier development are no longer part of the active tree. Their history remains available through Git.

## Production MongoDB deployment

`MONGODB_URI` is authoritative. When set, no other URI, localhost endpoint, Docker
hostname, or memory store is tried. URI aliases are intentionally unsupported.
`MONGODB_DATABASE_NAME` selects the database and defaults to `cyber_algo_arena`
only when absent; a configured empty value is rejected.

Set `APP_ENV=production` in cloud deployments. Render is also detected automatically.
Production requires a reachable MongoDB replica set (for example MongoDB Atlas)
or sharded cluster, with permission to read/write the selected database and manage
its indexes. This allows Accepted submissions and their profile/team/challenge
scores to commit together in a transaction. A standalone MongoDB server is
supported for local development only. Production rejects loopback addresses and
missing connection configuration. Configure network access and TLS through your
MongoDB provider and store credentials only in Render's secret environment settings.

The client uses a 2-second server-selection/connect timeout, a 3-second socket
read timeout, and a 5-second overall operation timeout. Driver retries are disabled;
transaction conflicts receive at most three attempts. Failed operations return a
sanitized error, never a success backed only by memory. Mongo driver diagnostics
are disabled because they can include connection settings. Application logs emit
`Connected to persistent MongoDB` only after ping and index initialization succeed.

Persistence mapping:

| Collection | Persisted data |
|---|---|
| `users` | Accounts, profiles, team membership, per-track/category scores and solved IDs |
| `teams` | Membership, password hashes and team scores |
| `challenges` | CP/CTF definitions, weekly problems, samples, hidden tests and solve statistics |
| `submissions` | All verdicts, unique submission IDs and awarded points |
| `contests` | Contest settings, registration and scoreboard freeze state |
| `contest_participations` | Unique contest/user participation records |

Legacy CP testcase files are migrated into challenge documents on initialization.
Public APIs include samples only. Hidden judge stdout/stderr and compiler diagnostics
are never returned to clients. Hidden tests are passed only to your authorized
Piston judge; configure `PISTON_URL` accordingly. Mongo URI and credential values
are removed from outbound Gemini prompt text as an additional safeguard.

Submission indexes enforce unique submission IDs, not unique user/problem or
team/problem attempts. Old unique solve indexes are removed by key definition.
Accepted solves are checked in application logic inside a transaction; WA, TLE,
MLE, RE and CE remain retryable. Readiness is 503 during database outages, while
liveness stays 200. Initialization retries when a health/request check reaches
MongoDB again; the application does not accept operations against an empty memory
store during an outage.

`render.yaml` defines the Docker service and `/api/health/ready` health check. For an
existing manually created Render service, set that health-check path in its settings;
adding a Blueprint file alone does not change an existing service. Set `MONGODB_URI`
securely before deploying. The Docker image runs `mvn clean verify` during its build.
The Compose configuration explicitly selects development mode and keeps database
storage in a named volume.

After configuring the real environment, run the operational persistence check:

```bash
java -jar target/cyber-algo-arena-1.0.0.jar --verify-persistence
```

It requires `MONGODB_URI` in the environment, pings MongoDB, creates a uniquely named
temporary CP record, reads it, closes and recreates the client/repository, verifies
its hidden tests, and deletes the record in a `finally` block. If connectivity fails
during cleanup, it reports cleanup failure; inspect only records whose IDs start
with `persistence-check-` before retrying. No URI or credentials are printed.

Run all unit tests with `mvn clean verify` (or `./mvnw clean verify`). To include real
MongoDB integration tests, set `MONGODB_TEST_URI` securely to a **test replica set**
and run the same command. Those tests use randomly named `arena_it_*` databases
and delete them afterward; they never select the application database. CI starts
an isolated replica set and runs this profile automatically. Coverage includes
restart persistence, HTTP health transitions, admin-created weekly tests, retry
verdicts, legacy index migration, hidden diagnostic suppression, transaction
rollback, and competing Accepted submissions across application instances.

Do not run `--demo` against application data: the legacy demo resets its dedicated
test database and refuses production mode or a conflicting database environment.
