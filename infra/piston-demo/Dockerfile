# Official Piston sandbox; only raise its HTTP JSON body limit for stress tests.
FROM ghcr.io/engineer-man/piston@sha256:2f66b7456189c4d713aa986d98eccd0b6ee16d26c7ec5f21b30e942756fd127a
RUN node -e 'const fs = require("fs"); const file = "/piston_api/src/index.js"; const source = fs.readFileSync(file, "utf8"); if (!source.includes("body_parser.json()")) throw new Error("Unexpected Piston HTTP parser; review patch before building"); fs.writeFileSync(file, source.replace("body_parser.json()", "body_parser.json({ limit: \"2mb\" })"));'
# Avoid destroying stdin before Node has flushed a large hidden testcase.
RUN node -e 'const fs = require("fs"); const file = "/piston_api/src/job.js"; const source = fs.readFileSync(file, "utf8"); if (!source.includes("proc.stdin.destroy();")) throw new Error("Unexpected stdin lifecycle; review patch"); fs.writeFileSync(file, source.replace("proc.stdin.destroy();", "proc.stdin.on(\"error\", () => {});"));'
ENV PISTON_RUN_TIMEOUT=60000 PISTON_RUN_CPU_TIME=60000
