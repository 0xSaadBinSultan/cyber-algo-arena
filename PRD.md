# Product Requirements Document (PRD): Cyber-Algo Arena

## 1. Objective
A self-contained Java OOP terminal/desktop system that concurrently manages Capture The Flag (CTF) security challenges and Competitive Programming (CP) algorithmic problems with persistent CSV storage.

## 2. Core Functional Requirements
- **Challenge Polymorphism:** Support two distinct challenge types under an abstract base class `Challenge`:
  - `CTFChallenge`: Evaluates raw string flags using SHA-256 hash comparison. Supports hint deduction.
  - `CPProblem`: Evaluates testcases (Input vs Expected Output). Supports time penalty calculations.
- **Scoring Engine:**
  - Real-time score calculation considering attempt penalties and hint deductions.
  - Tie-breaking logic: Higher score first; if tied, earliest last-solve timestamp wins.
- **Data Persistence:**
  - No SQL/Cloud DB. All states must persist into CSV files via standard Java File I/O (`BufferedReader`/`BufferedWriter`).
- **Security & Validation:**
  - Raw flags must never be stored in plain text (SHA-256 hex encoding only).

## 3. CSV File Contract Schema
### `challenges.csv`
`TYPE,ID,TITLE,BASE_POINTS,DIFFICULTY,EXTRA_PARAM_1,EXTRA_PARAM_2,EXTRA_PARAM_3`
- If `TYPE == "CTF"`: `CTF,ID,TITLE,POINTS,DIFF,CATEGORY,FLAG_HASH,HINT_COST`
- If `TYPE == "CP"`: `CP,ID,TITLE,POINTS,DIFF,TIME_LIMIT_MS,MEMORY_LIMIT_MB,TESTCASE_DIR`

### `submissions.csv`
`SUBMISSION_ID,TEAM_ID,CHALLENGE_ID,TIMESTAMP,STATUS,POINTS_AWARDED`

## 4. Exception Handling Strategy
- `InvalidSubmissionException`: Thrown on empty inputs or malformed flags.
- `CorruptedFileException`: Thrown when a CSV row is missing required tokens.
- `ChallengeNotFoundException`: Thrown when submitting against a non-existent ID.