# Separate Vercel backend

The root `vercel.json` deploys the existing static frontend. Keep that file in
place. The backend uses Vercel's Container Runtime with its own project and
configuration in `infra/vercel/backend.json`.

Live frontend: https://cyber-algo-arena.vercel.app

Live backend: https://cyber-algo-arena-backend.vercel.app

The backend project is deployed through the CLI, separately from the frontend's
Git deployment. After changing Java code, run the backend release procedure below.

Prepare a new directory outside the repository:

```sh
python3 infra/vercel/prepare-backend.py ../arena-backend-release
cd ../arena-backend-release
npx vercel@latest link --project cyber-algo-arena-backend
npx vercel@latest deploy --prod
```

The preparation command copies tracked application files only. It excludes local
environment files, credentials, Git metadata, and untracked uploads. Every release
must use a new destination. It adds an entrypoint that copies bundled assets to
`/tmp` so runtime writes work with a read-only container filesystem.

Configure production environment variables on the backend project before the final
deployment:

| Variable | Value |
| --- | --- |
| `PORT` | `8080` |
| `MONGODB_DATABASE_NAME` | `cyber_algo_arena` |
| `MONGODB_URI` | Existing Atlas connection string, stored as a secret |
| `PISTON_URL` and any judge credentials | Existing authorized execution service |
| `JUDGE0_URL` and optional `JUDGE0_API_KEY` | Alternative judge; the current deployment uses the existing Judge0 URL |
| `GEMINI_API_KEY` | Existing server-side key, stored as a secret |
| `GEMINI_MODEL` | Same value as the current working backend, when configured |

Existing Atlas administrator accounts remain usable without setting
`ARENA_ADMIN_PASSWORD` again. That variable bootstraps a new administrator; it does
not change an existing administrator's password.

Vercel Hobby uses dynamic outgoing addresses. Atlas connectivity may require an
explicitly approved broader IP access list. Do not silently enable access from
anywhere or put database credentials in source control.

Before frontend cutover, verify backend `/api/health/ready` reports `READY` and
database `UP`; then test login, challenge attachments, accepted and wrong-answer CP
submissions, and administrator access. Change the frontend's three Render rewrite
destinations only after these checks pass. Retain the previous configuration for
rollback.

## Runtime limits

- Production containers scale down after five idle minutes. The next request can
  have a cold start. Hobby usage limits still apply.
- Uploaded attachment files live only for one container instance. The bundled
  demo attachments are restored each startup. New permanent attachments require
  external object storage before relying on them in production.
- Sessions and rate limits are currently held in memory. Restarting or scaling
  can require signing in again. This application's in-memory contest state is
  intended for a small demonstration; concurrent instances require further
  database coordination for reliable live competition scoring.
- The judge remains an external service. Moving the API container does not host
  an execution sandbox, and a configured judge URL alone does not prove judging
  works.

Official runtime documentation: https://vercel.com/docs/functions/runtimes/container

## Migration verification

On November 18, 2026, both Vercel projects were deployed on Hobby. The backend
reported database `UP` against the existing Atlas database. Existing member login,
profile data, CTF attachment download, and administrator access restrictions passed.
Packet Budget passed all eight tests in Python, C++, and Java; Signal Surge passed
all eight in Python. Deliberately incorrect solutions were rejected on hidden
tests for both problems. A Python submission also passed through the frontend's
API proxy after cutover. These checks establish demonstration readiness at the
time of deployment, rather than an uptime guarantee for the external judge.
