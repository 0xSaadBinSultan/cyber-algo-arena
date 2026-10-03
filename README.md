# Cyber-Algo Arena

Cyber-Algo Arena is a Java 21 web platform that combines Capture The Flag (CTF) challenges and Competitive Programming (CP) problems in one competition system. It includes authentication, teams, contests, scoring, profiles, an admin portal, MongoDB persistence, cloud code execution through Piston, and live contest feeds.

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

## Gemini AI Tutor

The challenge modal includes a server-side Gemini tutor with progressive Hint 1/2/3 and Explain modes. The browser never receives the Gemini API key, CTF flag hashes, or hidden testcase data. Configure `GEMINI_API_KEY` and optionally `GEMINI_MODEL` on the server.

## Configuration

| Variable | Purpose | Default |
|---|---|---|
| `PORT` | HTTP port | `8080` |
| `MONGODB_URI` | MongoDB connection URI | local discovery fallback |
| `MONGODB_DATABASE_NAME` | MongoDB database name | `cyber_algo_arena` |
| `ARENA_ADMIN_USERNAME` | Bootstrap admin username | `admin` |
| `ARENA_ADMIN_PASSWORD` | Bootstrap admin password | none |

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
