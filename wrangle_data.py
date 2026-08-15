import os
import csv
import hashlib
from pathlib import Path

# Setup directories
BASE_DIR = Path("./contest_data")
TESTCASE_DIR = BASE_DIR / "testcases"
BASE_DIR.mkdir(exist_ok=True)
TESTCASE_DIR.mkdir(exist_ok=True)

def hash_flag(flag: str) -> str:
    return hashlib.sha256(flag.encode("utf-8")).hexdigest()

# 1. Generate CTF Challenges
ctf_challenges = [
    {
        "type": "CTF",
        "id": "CTF-01",
        "title": "Base64 Mystery",
        "points": 100,
        "difficulty": "EASY",
        "category": "CRYPTO",
        "flag_hash": hash_flag("flag{b4s3_64_1s_n0t_3ncrypt10n}"),
        "hint_cost": 20
    },
    {
        "type": "CTF",
        "id": "CTF-02",
        "title": "Buffer Overflow Intro",
        "points": 300,
        "difficulty": "HARD",
        "category": "PWN",
        "flag_hash": hash_flag("flag{r3t_2_w1n_succ3ss}"),
        "hint_cost": 50
    }
]

# 2. Generate CP Problems & Testcase files
cp_problems = [
    {
        "type": "CP",
        "id": "CP-01",
        "title": "Array Inversion Count",
        "points": 200,
        "difficulty": "MEDIUM",
        "time_limit": 1000,
        "mem_limit": 256,
        "testcases": [
            ("5\n2 4 1 3 5", "3"),
            ("3\n1 2 3", "0")
        ]
    }
]

# Write CP Testcases to disk
for cp in cp_problems:
    prob_dir = TESTCASE_DIR / cp["id"]
    prob_dir.mkdir(exist_ok=True)
    for idx, (inp, out) in enumerate(cp["testcases"], start=1):
        with open(prob_dir / f"input_{idx}.txt", "w") as f:
            f.write(inp)
        with open(prob_dir / f"output_{idx}.txt", "w") as f:
            f.write(out)

# 3. Write challenges.csv
csv_path = BASE_DIR / "challenges.csv"
with open(csv_path, "w", newline="", encoding="utf-8") as f:
    writer = csv.writer(f)
    writer.writerow(["TYPE", "ID", "TITLE", "POINTS", "DIFFICULTY", "PARAM1", "PARAM2", "PARAM3"])
    
    for c in ctf_challenges:
        writer.writerow([c["type"], c["id"], c["title"], c["points"], c["difficulty"], c["category"], c["flag_hash"], c["hint_cost"]])
        
    for p in cp_problems:
        writer.writerow([p["type"], p["id"], p["title"], p["points"], p["difficulty"], p["time_limit"], p["mem_limit"], str(TESTCASE_DIR / p["id"])])

print(f"Data wrangling complete. CSV and testcases generated in {BASE_DIR.resolve()}")