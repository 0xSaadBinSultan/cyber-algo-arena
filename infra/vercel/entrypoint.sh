#!/bin/sh
set -eu

# Vercel instances have no persistent disk. Keep bundled assets read-only and
# place runtime attachment/testcase writes in the instance's temporary space.
runtime_dir=$(mktemp -d /tmp/cyber-arena.XXXXXX)
cp -R /app/public "$runtime_dir/public"
cp -R /app/views "$runtime_dir/views"
cp -R /app/contest_data "$runtime_dir/contest_data"
cd "$runtime_dir"
exec java -XX:MaxRAMPercentage=65 -jar /app/app.jar --web
