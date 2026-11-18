#!/usr/bin/env python3
"""Create a secret-free, separate Vercel backend deployment directory."""
import argparse
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("destination", type=Path)
args = parser.parse_args()
destination = args.destination.resolve()
if destination == root or root in destination.parents:
    parser.error("Use a destination outside the repository.")
destination.mkdir(parents=True, exist_ok=False)

# Copy only tracked app files, never .env files, local uploads, or CLI credentials.
tracked = subprocess.check_output(
    ["git", "ls-files", "-z", "--", "pom.xml", "Dockerfile", ".dockerignore",
     "src", "public", "views", "contest_data"], cwd=root
).decode().split("\0")
for name in filter(None, tracked):
    source = root / name
    target = destination / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)

shutil.copy2(root / "infra/vercel/backend.json", destination / "vercel.json")
shutil.copy2(root / "infra/vercel/entrypoint.sh", destination / "vercel-entrypoint.sh")
(destination / ".vercelignore").write_text(".env*\n.vercel/\n.git/\n")
with (destination / ".dockerignore").open("a") as ignore:
    ignore.write("\n.env*\n.vercel/\n")
dockerfile = destination / "Dockerfile"
dockerfile.write_text(dockerfile.read_text().replace(
    'ENTRYPOINT ["java", "-jar", "app.jar", "--web"]',
    'COPY vercel-entrypoint.sh /app/vercel-entrypoint.sh\n'
    'ENTRYPOINT ["sh", "/app/vercel-entrypoint.sh"]'
))
print(destination)
