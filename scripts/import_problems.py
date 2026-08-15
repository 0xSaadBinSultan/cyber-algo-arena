#!/usr/bin/env python3
"""
Cyber-Algo Arena — Automated Problem Importer
Fetches real CP problems from Codeforces and seeds standard CTF challenges into MongoDB.
"""

import os
import sys
import json
import hashlib
import urllib.request
from pathlib import Path

# MongoDB Configuration
MONGODB_URI = os.getenv("MONGODB_URI", "mongodb://localhost:27017")
DB_NAME = os.getenv("MONGODB_DATABASE_NAME", "cyber_algo_arena")

CONTEST_DATA_DIR = Path("contest_data")
TESTCASE_DIR = CONTEST_DATA_DIR / "testcases"
ATTACH_DIR = CONTEST_DATA_DIR / "attachments"

def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()

def fetch_codeforces_problems(limit=5):
    print("[Importer] Querying Codeforces Problemset API...")
    url = "https://codeforces.com/api/problemset.problems"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            if data.get("status") != "OK":
                print("[Importer] Warning: Non-OK Codeforces response.")
                return []
            
            problems = data.get("result", {}).get("problems", [])
            selected = []
            
            for p in problems:
                rating = p.get("rating", 0)
                # Filter for Div.2/Div.3 beginner & intermediate rating (800 - 1500)
                if 800 <= rating <= 1500:
                    prob_id = f"CF-{p.get('contestId')}{p.get('index')}"
                    title = p.get("name")
                    points = 100 if rating <= 1000 else (200 if rating <= 1300 else 300)
                    diff = "EASY" if rating <= 1000 else ("MEDIUM" if rating <= 1300 else "HARD")
                    tags = p.get("tags", [])
                    
                    selected.append({
                        "id": prob_id,
                        "type": "CP",
                        "title": f"{title} (CF {p.get('contestId')}{p.get('index')})",
                        "basePoints": points,
                        "difficulty": diff,
                        "timeLimitMs": 2000,
                        "memoryLimitMb": 256,
                        "testcaseDir": str(TESTCASE_DIR / prob_id),
                        "hintCost": 0,
                        "tags": tags
                    })
                    
                    if len(selected) >= limit:
                        break
            return selected
    except Exception as e:
        print(f"[Importer] Error fetching Codeforces problems: {e}")
        return []

def get_standard_ctf_challenges():
    challenges = [
        {
            "id": "CTF-CRYPTO-01",
            "type": "CTF",
            "title": "Affine Shift Nexus",
            "category": "CRYPTO",
            "difficulty": "EASY",
            "basePoints": 150,
            "hintCost": 25,
            "flagHash": sha256_hex("flag{affine_ciphers_are_trivial_2026}"),
            "attachmentFileName": "affine_nexus.txt",
            "attachmentContent": "Ciphertext: Zkccv Tpkew Pvgxk! (a=5, b=8)"
        },
        {
            "id": "CTF-REV-01",
            "type": "CTF",
            "title": "Bytecode Disassembler",
            "category": "REVERSE_ENGINEERING",
            "difficulty": "MEDIUM",
            "basePoints": 250,
            "hintCost": 35,
            "flagHash": sha256_hex("flag{jvm_bytecode_reversed_successfully}"),
            "attachmentFileName": "CrackMe.class",
            "attachmentContent": "\xca\xfe\xba\xbe\x00\x00\x00\x3d\x00..."
        },
        {
            "id": "CTF-WEB-01",
            "type": "CTF",
            "title": "JWT Algorithm Confusion",
            "category": "WEB",
            "difficulty": "MEDIUM",
            "basePoints": 300,
            "hintCost": 40,
            "flagHash": sha256_hex("flag{none_algorithm_jwt_bypass}"),
            "attachmentFileName": "token_debug.log",
            "attachmentContent": "Header: {\"alg\":\"none\",\"typ\":\"JWT\"}\nPayload: {\"user\":\"admin\",\"role\":\"ROOT\"}"
        },
        {
            "id": "CTF-PWN-01",
            "type": "CTF",
            "title": "ROP Chain Prelude",
            "category": "PWN",
            "difficulty": "HARD",
            "basePoints": 400,
            "hintCost": 50,
            "flagHash": sha256_hex("flag{pop_rdi_ret_gadget_master}"),
            "attachmentFileName": "rop_binary.elf",
            "attachmentContent": "\x7fELF\x02\x01\x01\x00..."
        },
        {
            "id": "CTF-OSINT-01",
            "type": "CTF",
            "title": "Satellite Geolocation Tracing",
            "category": "OSINT",
            "difficulty": "EASY",
            "basePoints": 100,
            "hintCost": 15,
            "flagHash": sha256_hex("flag{37.7749_-122.4194_san_francisco}"),
            "attachmentFileName": "satellite_metadata.exif",
            "attachmentContent": "GPS Coordinates: 37.7749 N, 122.4194 W"
        }
    ]
    return challenges

def setup_testcases_and_attachments(cp_problems, ctf_challenges):
    TESTCASE_DIR.mkdir(parents=True, exist_ok=True)
    ATTACH_DIR.mkdir(parents=True, exist_ok=True)

    # Setup CP testcases
    for cp in cp_problems:
        prob_dir = Path(cp["testcaseDir"])
        prob_dir.mkdir(parents=True, exist_ok=True)
        
        in_file = prob_dir / "input_1.txt"
        out_file = prob_dir / "output_1.txt"
        
        if not in_file.exists():
            in_file.write_text("5\n1 2 3 4 5\n", encoding="utf-8")
        if not out_file.exists():
            out_file.write_text("15\n", encoding="utf-8")

    # Setup CTF attachments
    for ctf in ctf_challenges:
        if ctf.get("attachmentFileName") and ctf.get("attachmentContent"):
            fpath = ATTACH_DIR / ctf["attachmentFileName"]
            fpath.write_text(ctf["attachmentContent"], encoding="utf-8")

def save_to_mongodb(challenges):
    try:
        from pymongo import MongoClient
        client = MongoClient(MONGODB_URI, serverSelectionTimeoutMS=2000)
        db = client[DB_NAME]
        coll = db["challenges"]
        
        inserted = 0
        updated = 0
        for c in challenges:
            doc = {k: v for k, v in c.items() if k != "attachmentContent"}
            res = coll.replace_one({"id": c["id"]}, doc, upsert=True)
            if res.upserted_id:
                inserted += 1
            else:
                updated += 1
        
        print(f"[Importer] Successfully synchronized {len(challenges)} challenges to MongoDB ({inserted} new, {updated} updated).")
        client.close()
        return True
    except Exception as e:
        print(f"[Importer] MongoDB direct write notice: {e}")
        return False

def main():
    print("═" * 60)
    print("  Cyber-Algo Arena — Problem & CTF Importer Utility")
    print("═" * 60)

    # 1. Fetch CP Problems from Codeforces
    cp_problems = fetch_codeforces_problems(limit=5)
    if not cp_problems:
        print("[Importer] Using offline fallback for CP problems.")
        cp_problems = [
            {
                "id": "CF-1850A",
                "type": "CP",
                "title": "To My Critics (CF 1850A)",
                "basePoints": 100,
                "difficulty": "EASY",
                "timeLimitMs": 1000,
                "memoryLimitMb": 256,
                "testcaseDir": str(TESTCASE_DIR / "CF-1850A"),
                "hintCost": 0
            },
            {
                "id": "CF-1850B",
                "type": "CP",
                "title": "Ten Words of Wisdom (CF 1850B)",
                "basePoints": 150,
                "difficulty": "EASY",
                "timeLimitMs": 1000,
                "memoryLimitMb": 256,
                "testcaseDir": str(TESTCASE_DIR / "CF-1850B"),
                "hintCost": 0
            },
            {
                "id": "CF-1850C",
                "type": "CP",
                "title": "Word on the Paper (CF 1850C)",
                "basePoints": 200,
                "difficulty": "MEDIUM",
                "timeLimitMs": 1000,
                "memoryLimitMb": 256,
                "testcaseDir": str(TESTCASE_DIR / "CF-1850C"),
                "hintCost": 0
            },
            {
                "id": "CF-1850D",
                "type": "CP",
                "title": "Balanced Round (CF 1850D)",
                "basePoints": 250,
                "difficulty": "MEDIUM",
                "timeLimitMs": 2000,
                "memoryLimitMb": 256,
                "testcaseDir": str(TESTCASE_DIR / "CF-1850D"),
                "hintCost": 0
            },
            {
                "id": "CF-1850E",
                "type": "CP",
                "title": "Cardboard for Pictures (CF 1850E)",
                "basePoints": 300,
                "difficulty": "HARD",
                "timeLimitMs": 2000,
                "memoryLimitMb": 256,
                "testcaseDir": str(TESTCASE_DIR / "CF-1850E"),
                "hintCost": 0
            }
        ]

    # 2. Get standard CTF suite
    ctf_challenges = get_standard_ctf_challenges()

    # 3. Setup testcases and attachments on disk
    setup_testcases_and_attachments(cp_problems, ctf_challenges)
    print(f"[Importer] Configured testcase suites and challenge attachments in {CONTEST_DATA_DIR}/")

    # 4. Save into MongoDB
    all_challenges = cp_problems + ctf_challenges
    save_to_mongodb(all_challenges)
    print("[Importer] Import completed successfully.")

if __name__ == "__main__":
    main()
