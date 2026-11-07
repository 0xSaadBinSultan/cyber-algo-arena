# Render deployment and teacher demo

The Java backend and the current frontend are served together by Render. Use the Render URL for the presentation. A Vercel frontend must proxy `/api/*` to Render; Vercel does not run the Java backend.

## Render service

- Runtime: Docker; Dockerfile: `./Dockerfile`; context: repository root.
- Render supplies `PORT`; the app binds it automatically.
- Health check: `/api/health/ready` after MongoDB is configured.
- Configure `MONGODB_URI` as the Atlas application connection string (URL-encode any password characters).
- Configure `MONGODB_DATABASE_NAME=cyber_algo_arena`.
- Configure `ARENA_ADMIN_USERNAME=admin` and a unique `ARENA_ADMIN_PASSWORD` of at least 12 characters before the first database startup. There is no default password. Existing admin passwords are never overwritten at startup.
- In Atlas, allow only the outbound ranges shown under Render → Connect. Keep database authentication enabled. Compass is only a database client and can be closed.
- Free Render instances sleep when idle. Open the site before presenting. An always-running Render instance requires a paid service plan.
- Sessions are in memory: users sign in again after a restart. Accounts, problems, contests, and submissions persist in MongoDB.

If Atlas initially connects slowly, the app now allows 20 seconds per server-selection attempt and closes failed clients. After changing network settings, restart a process that previously entered memory fallback. `/api/health/ready` must report `READY`, not merely a successful homepage response.

## Real code judging

The application does not execute user programs in the Render web process.

Choose one execution provider:

1. Set `PISTON_URL` to an authorized/self-hosted Piston URL. Full `/execute` URLs and base URLs are accepted. Optionally set `PISTON_API_KEY` for bearer authentication.
2. Set `JUDGE0_URL=https://judge0-ce.p.rapidapi.com` and `JUDGE0_API_KEY` for an activated Judge0 CE account. A configured Judge0 URL takes priority over Piston. Self-hosted Judge0 uses `X-Auth-Token` instead of RapidAPI headers.

Judge0 CE language IDs currently used are 54 (C++), 62 (Java), and 71 (Python 3). These must be installed/enabled by the provider. Programs must read stdin and write stdout; Java entrypoint is `public class Main`.

The official public Piston service requires authorization. Do not assume a public fallback works. Without a configured provider, submissions return `INVALID` with a setup message and award no points. A provider timeout or authentication failure is an infrastructure error, not a wrong answer.

Each full Packet Budget submission makes 8 execution requests: 2 public samples and 6 hidden cases. Running only samples makes 2 requests. Consider the provider's quotas and current pricing before enabling it. Never commit judge credentials.

## Presentation walkthrough

1. Open the overview and explain the CP and CTF tracks.
2. Sign in as a player and open **Problemset → Packet Budget**. Show the statement, constraints, samples, language selector, and saved source draft.
3. With a live judge configured, run samples, then submit a correct sum using a 64-bit integer. Submit a 32-bit implementation from a second practice account to demonstrate an overflow hidden test failure.
4. Open **CTF labs → A message between lectures**. Decode the supplied Base64 evidence and submit the recovered flag. The log and reverse-engineering labs are also self-contained.
5. Create a team using a private team password. Sign in as the administrator and create a contest with selected problems and local start/end times.
6. Register the team, open the round, and submit during its live window. Show contest standings and personal submission history. Practice points stay independent of contest points. Already-solved practice problems can still be solved in a contest.
7. Restart/redeploy, sign in again, and check that MongoDB retained the account, contest, and results.

## Scope and limits

This is an original university practice platform using familiar CP/Jeopardy interaction patterns. It does not connect to Flagyard accounts or copy its challenges. It is not a full Codeforces clone: there are no rated rounds, plagiarism detection, virtual participation, or per-contest private problem archives. Hosted rounds select public practice problems; avoid reusing them for a secret exam.

The old generated catalog included incomplete CTF evidence and generic testcases attached to unrelated Codeforces titles. Those entries are no longer presented as local judge-ready problems. Verified original challenges are added idempotently without deleting existing database records. Codeforces imports are opt-in, keep authentic API metadata, and link to the original platform; offline imports no longer fabricate rounds.

For a class demonstration use one backend instance. The existing whole-state reload/write architecture is not designed for a large simultaneous contest. Judge requests are synchronous and serialize scoring operations. Sessions are not shared across instances, and an execution queue, durable session store, transactional scoring, and stronger administrative audit controls remain future work.

## Local Docker judge chosen for the teacher demo

Run `bash scripts/start-local-demo.sh` from this checkout with Docker and your local MongoDB running. The launcher uses a separate `arena_teacher_demo` database and asks you to choose its administrator password in the terminal. It starts the official Piston container on loopback port 2000, installs C++, Java and Python runtimes if missing, and starts the Java web app at `http://localhost:8085`.

The judge container is named `arena-piston-demo`. It uses Piston's required privileged container mode and is bound only to `127.0.0.1`; do not expose its package-management API to the Internet. Its runtime files live in the sibling `piston-data` directory. Use `docker stop arena-piston-demo` to stop it. The local demo has no hosting fee; it uses your PC's disk, memory, and network connection. Runtime downloads need Internet access on the first setup.

Keep Docker, MongoDB, and the Java app running while presenting CP. This local judge does **not** make CP submissions work on Render. The live Render site requires an authorized hosted judge endpoint if public CP judging is needed. CTF, contest management, and MongoDB persistence are independently available on Render.

The local Piston image raises only its HTTP JSON request limit to 2 MiB so the maximum-size hidden input can reach the sandbox. Sandbox isolation remains the official Piston implementation. Java gets twice the base time allowance to accommodate JVM startup/JIT; this is disclosed in the editor. The Piston client uses HTTP/1.1 because the official Node service rejects Java's HTTP/2 upgrade requests.

Contest Radar is available to all visitors from the main navigation, with provider filters and official calendar links. Upstream failures no longer fabricate upcoming events or relative dates. The public interface has no admin-portal links; administrators enter `/admin` in the URL directly.

The local image also fixes an upstream stdin lifecycle issue: destroying the pipe immediately after ending it truncated large test input. The demo leaves the pipe open until queued input has flushed. Source examples under `examples/packet-budget` were evaluated against the actual local sandbox: C++, Python, and Java pass all eight cases, while `overflow.cpp` passes samples and fails hidden test 5.

## Temporary Render connection for the local judge

An optional authenticated gateway can connect Render to the local judge for a live class presentation. Run `python3 scripts/run-render-demo-judge.py` to start or resume it. It binds the gateway to loopback port 2001, requires a bearer token, and exposes only `/api/v2/execute` and `/api/v2/runtimes`. The package-management routes are blocked. Cloudflare Quick Tunnel supplies the temporary HTTPS hostname.

Set Render's `PISTON_URL` and `PISTON_API_KEY` from the private `render-judge-settings.txt` written beside the checkout. Never commit that settings file or the sibling private JSON token file. The token stays in the backend; the browser never receives it. Submitted source and server-side test stdin pass through Cloudflare to this PC.

Keep the PC and Docker on during the presentation. A Quick Tunnel has no uptime guarantee, and its hostname changes when recreated. After rebooting, run the script and update Render's `PISTON_URL` if it changed. Use an independently hosted execution provider for permanent availability. Closing Compass does not affect the Atlas database.

CP practice allows repeat source submissions so members can compare languages or improve a solution. Every attempt receives a real verdict, but only the first accepted solve awards practice points. Team contest duplicate solves remain blocked.
