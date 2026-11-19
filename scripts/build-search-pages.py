#!/usr/bin/env python3
"""Build public learning pages and their sitemap; never include private arena data."""
import html
import json
from pathlib import Path

PUBLIC = Path(__file__).resolve().parents[1] / "public"
BASE = "https://cyber-algo-arena.vercel.app"
HOME_MODIFIED = "2026-11-19"

PAGES = [
    {
        "path": "cp-and-ctf",
        "modified": "2026-11-19",
        "title": "CP and CTF Together: A Merged Platform | Cyber Algo Arena",
        "heading": "CP and CTF together in one platform.",
        "description": "Bring CP and CTF together in a merged practice platform. Cyber Algo Arena combines coding problems, CTF labs, shared progress, and mixed contests.",
        "eyebrow": "ONE ARENA · TWO WAYS TO THINK",
        "answer": "Cyber Algo Arena is a merged CP and CTF platform for competitive programming and capture the flag practice. Solve judged coding problems and security challenges with one account, then bring both tracks into a club contest.",
        "body": '''<section class="answer-box"><h2>Can I merge CP and CTF into one platform?</h2><p>Yes. Cyber Algo Arena brings CP and CTF together through a shared account, a practice workspace, and combined progress tracking. You can switch from an algorithm problem to a security lab without creating another account.</p><p>The two tracks keep their own submission rules: CP source code runs against public samples and hidden testcases, while CTF answers are checked as flags. A contest can include challenges from both tracks.</p></section>
<section><h2>What does CPCTF mean here?</h2><p>CPCTF is shorthand on this page for competitive programming and capture the flag together. It describes a combined practice workflow: build algorithmic skills, investigate security puzzles, and follow your progress in one place.</p><p>For example, start with Packet Budget to practice input handling and integer sums, then investigate the access log in The midnight request. Both solves contribute to the same member profile.</p></section>
<section><h2>What is the difference between CP and CTF?</h2>
<p>Competitive programming asks you to build a correct, efficient algorithm for a defined input and output. Capture the flag asks you to investigate a security puzzle and recover a flag: a short answer that proves you solved it. Both reward careful reading, experimentation, and persistence.</p>
<div class="table-scroll"><table><caption>Two tracks, compared</caption><thead><tr><th scope="col">Question</th><th scope="col">Competitive programming</th><th scope="col">Capture the flag</th></tr></thead><tbody>
<tr><th scope="row">What do I submit?</th><td>Source code in C++, Java, or Python.</td><td>A flag recovered from the challenge.</td></tr>
<tr><th scope="row">How is it checked?</th><td>A judge runs public samples and hidden tests.</td><td>The server checks the submitted flag.</td></tr>
<tr><th scope="row">What will I practice?</th><td>Algorithms, data structures, input handling, and complexity.</td><td>Cryptography, forensics, reverse engineering, and web puzzles.</td></tr>
<tr><th scope="row">A first challenge here</th><td>Packet Budget: sum a batch of packet sizes.</td><td>The midnight request: inspect a provided access log.</td></tr>
</tbody></table></div></section>
<section><h2>Why bring CP and CTF together?</h2><p>Algorithm practice builds precise reasoning about code. Security puzzles build the habit of questioning how a system behaves. Switching between them gives you different ways to approach a difficult problem, with one profile for your progress.</p>
<p>In this arena, CP and CTF are separate practice tracks with a shared account. A CP solution is still evaluated as code; a CTF answer is still evaluated as a flag. Combining the workspace does not mix those judging rules.</p></section>
<section><h2>How to start</h2><ol><li>Read the <a href="/competitive-programming">competitive programming guide</a> and try Packet Budget.</li><li>Open the <a href="/capture-the-flag">CTF beginner guide</a> and choose a security category.</li><li>Create an account in the arena to submit answers and save progress.</li><li>For a club round, create a team and join a published contest.</li></ol></section>
<section class="answer-box"><h2>Can one platform host CP and CTF contests?</h2><p>Yes. Cyber Algo Arena lets administrators publish contests using CP problems, CTF challenges, or both. Contest participants use teams and a contest scoreboard; personal practice scores are tracked separately.</p></section>
<section><h2>Is this connected to Codeforces or LeetCode?</h2><p>Cyber Algo Arena is an independent project. Its built-in practice problems and security labs run here. External references, when listed, open on their original platforms and do not claim to include those platforms’ private testcases.</p></section>''',
        "cta": "Choose a practice track", "href": "/#overview",
    },
    {
        "path": "competitive-programming",
        "title": "Competitive Programming Practice & Online Judge | Cyber Algo Arena",
        "heading": "Learn competitive programming, one problem at a time.",
        "description": "Start competitive programming with C++, Java, or Python. Practice original problems with public samples, hidden tests, and online judge verdicts in Cyber Algo Arena.",
        "eyebrow": "THE CP FIELD GUIDE",
        "answer": "Competitive programming (CP) is the practice of solving algorithmic problems with code under time and memory limits. Cyber Algo Arena lets you submit C++, Java, and Python solutions and evaluate them against public samples and hidden testcases.",
        "body": '''<section><h2>How does the online judge work?</h2><p>Read the input format, write a program that reads standard input, and print the required result to standard output. The judge compiles or runs your source code for each testcase and compares the result with the expected output.</p><p>A correct sample is a useful first check. Hidden tests also cover cases such as zero values, large numbers, unusual whitespace, and larger inputs. Passing the samples alone does not mean a solution is correct.</p></section>
<section><h2>Choose your first problem</h2><div class="learn-grid"><a class="learn-card" href="/problems/packet-budget"><span class="eyebrow">EASY · INPUT AND OUTPUT</span><h3>Packet Budget</h3><p>Sum packet sizes and learn why a result may need a 64-bit integer.</p><span>Read the problem ↗</span></a><a class="learn-card" href="/problems/signal-surge"><span class="eyebrow">MEDIUM · MAXIMUM SUBARRAY</span><h3>Signal Surge</h3><p>Find the best non-empty contiguous interval in a signal stream.</p><span>Read the problem ↗</span></a></div></section>
<section><h2>What do the verdicts mean?</h2><dl><dt>Accepted</dt><dd>The solution passed every configured testcase.</dd><dt>Wrong Answer</dt><dd>The program ran, but at least one result did not match the expected answer.</dd><dt>Compilation Error</dt><dd>The compiler could not build the submitted source.</dd><dt>Runtime Error</dt><dd>The program stopped unexpectedly, for example after an invalid array access.</dd><dt>Time or Memory Limit Exceeded</dt><dd>The solution exceeded a resource limit. Revisit the algorithm and the input constraints.</dd></dl></section>
<section class="answer-box"><h2>Which languages can I use?</h2><p>The arena’s CP judge supports C++, Java, and Python. Choose the language in the problem workspace before submitting. Use standard input and output; Java submissions should use the Main class.</p></section>
<section><h2>A practical learning loop</h2><ol><li>Work through the samples by hand.</li><li>Estimate the largest input and choose an algorithm that fits.</li><li>Test boundary cases, including the smallest valid input.</li><li>Run the public samples, then submit for hidden-test evaluation.</li><li>Read the verdict and improve the solution.</li></ol><p>Want a different kind of puzzle? Explore <a href="/capture-the-flag">capture the flag</a>, or read how <a href="/cp-and-ctf">CP and CTF fit into one workspace</a>.</p></section>''',
        "cta": "Open the CP problemset", "href": "/#cp",
    },
    {
        "path": "capture-the-flag",
        "title": "CTF Practice for Beginners: Crypto, Forensics & Web | Cyber Algo Arena",
        "heading": "Follow the clues. Find your first flag.",
        "description": "Learn capture the flag with beginner CTF labs in cryptography, digital forensics, reverse engineering, and web security. Practice alongside CP in Cyber Algo Arena.",
        "eyebrow": "THE CTF FIELD GUIDE",
        "answer": "Capture the flag (CTF) is a security learning format built around challenges. You inspect the provided evidence or code, solve a puzzle, and submit a flag. Cyber Algo Arena offers cryptography, forensics, reverse engineering, and web challenges alongside competitive programming.",
        "body": '''<section><h2>What is a flag?</h2><p>A flag is the answer a challenge asks you to recover. Its exact format is specified by the challenge. Unlike a CP problem, you usually submit that answer directly rather than submitting a program for hidden-test execution.</p><p>Read the challenge description before using tools. A filename, a repeated character, a suspicious log entry, or a small detail in source code can be the starting clue.</p></section>
<section><h2>Four ways to start here</h2><div class="learn-grid">
<article class="learn-card"><span class="eyebrow">CRYPTOGRAPHY</span><h3>A message between lectures</h3><p>Investigate an encoded message. Look for patterns and consider whether a simple substitution could explain them.</p></article>
<article class="learn-card"><span class="eyebrow">DIGITAL FORENSICS</span><h3>The midnight request</h3><p>Download the provided access log. Compare ordinary requests with the unusual activity and follow the evidence.</p></article>
<article class="learn-card"><span class="eyebrow">REVERSE ENGINEERING</span><h3>Read the checker</h3><p>Study the supplied checker and work backward from the conditions it tests.</p></article>
<article class="learn-card"><span class="eyebrow">WEB SECURITY</span><h3>The Session Cookie</h3><p>Examine the challenge’s cookie example and reason about the information it represents.</p></article></div></section>
<section><h2>Do I need advanced tools?</h2><p>Start with careful observation and a text editor. A short Python script can help transform an encoded string or summarize a log. The right tool follows from your hypothesis, rather than the other way around.</p><p>These are self-contained learning challenges using the material provided in the arena. The web exercise does not give permission to test unrelated websites.</p></section>
<section class="answer-box"><h2>Can I learn CTF and competitive programming together?</h2><p>Yes. Your arena account records solves across both tracks. Practice <a href="/competitive-programming">algorithms and code submissions</a> when you want precise input/output tasks, then switch to CTF labs for investigative puzzles. The <a href="/cp-and-ctf">CP and CTF comparison</a> explains the different judging rules.</p></section>
<section><h2>When you get stuck</h2><p>Write down what you know, what you tried, and what result you expected. Ask for one small hint in the CTF workspace, then test that idea. The tutor can provide progressive guidance; a labeled built-in study guide remains available when the AI provider cannot respond.</p></section>''',
        "cta": "Explore the CTF labs", "href": "/#ctf",
    },
    {
        "path": "problems/packet-budget",
        "title": "Packet Budget — Sum of Packet Sizes Practice Problem | Cyber Algo Arena",
        "heading": "Packet Budget",
        "description": "Practice standard input, output, and 64-bit sums with Packet Budget. Read the statement and samples, then submit C++, Java, or Python to the online judge.",
        "eyebrow": "CP PRACTICE · EASY · ARENA-SUM",
        "answer": "Given n packet sizes, print their total size. Packet Budget is a beginner competitive programming problem about reading input, summing numbers, and choosing an integer type large enough for the answer.",
        "body": '''<section><h2>Problem statement</h2><p>Your university network records the size of every packet in a batch. Calculate the total number of bytes in the batch.</p><h2>Input</h2><p>The first integer is n, the number of packets (1 ≤ n ≤ 200000). The next n integers are packet sizes aᵢ (0 ≤ aᵢ ≤ 1000000000). Integers may be separated by any whitespace.</p><h2>Output</h2><p>Print one integer: the sum of all packet sizes.</p></section>
<section><h2>Sample 1</h2><div class="sample-grid"><div><h3>Input</h3><pre><code>5
12 8 5 10 15</code></pre></div><div><h3>Output</h3><pre><code>50</code></pre></div></div><p>The total is 12 + 8 + 5 + 10 + 15 = 50 bytes.</p><h2>Sample 2</h2><div class="sample-grid"><div><h3>Input</h3><pre><code>3
0 0 7</code></pre></div><div><h3>Output</h3><pre><code>7</code></pre></div></div></section>
<section class="answer-box"><h2>What should I watch out for?</h2><p>The answer can exceed 2147483647. Use a 64-bit integer such as long long in C++ or long in Java. Python integers can represent the total. Aim for O(n) time and use standard input and output.</p></section>
<section><h2>Submit and evaluate</h2><p>The built-in problem has two public samples and six hidden tests, a 2-second time limit, and a 256 MB memory limit. Open Packet Budget in the problemset, select C++, Java, or Python, and sign in to submit. Hidden inputs and outputs are not published on this page.</p><p>Next: <a href="/problems/signal-surge">Signal Surge</a>, or return to the <a href="/competitive-programming">CP practice guide</a>.</p></section>''',
        "cta": "Solve Packet Budget in the arena", "href": "/#cp",
    },
    {
        "path": "problems/signal-surge",
        "title": "Signal Surge — Maximum Subarray Practice Problem | Cyber Algo Arena",
        "heading": "Signal Surge",
        "description": "Practice the maximum subarray problem with Signal Surge. Read constraints and samples, handle negative values, and submit C++, Java, or Python for judged evaluation.",
        "eyebrow": "CP PRACTICE · MEDIUM · ARENA-MAXSUB",
        "answer": "Given a sequence of signal changes, find the largest possible sum of a non-empty contiguous subarray. Signal Surge is a maximum subarray problem: the chosen values must be consecutive, and at least one value must be selected.",
        "body": '''<section><h2>Problem statement</h2><p>A satellite transceiver receives a continuous telemetry stream of signal delta measurements. A power surge occurs over the contiguous transmission interval that maximizes the cumulative sum of signal deltas. Find the maximum possible sum of a non-empty contiguous subarray.</p><h2>Input</h2><p>The first line contains n, the number of measurements (1 ≤ n ≤ 100000). The second line contains n integers aᵢ (−10⁹ ≤ aᵢ ≤ 10⁹).</p><h2>Output</h2><p>Print a single integer: the maximum sum among all non-empty contiguous subarrays. Use a 64-bit integer in C++ or Java.</p></section>
<section><h2>Sample 1</h2><div class="sample-grid"><div><h3>Input</h3><pre><code>9
-2 1 -3 4 -1 2 1 -5 4</code></pre></div><div><h3>Output</h3><pre><code>6</code></pre></div></div><p>The interval 4, −1, 2, 1 has sum 6.</p><h2>Sample 2</h2><div class="sample-grid"><div><h3>Input</h3><pre><code>5
5 4 -1 7 8</code></pre></div><div><h3>Output</h3><pre><code>23</code></pre></div></div><p>Here, choosing the entire sequence gives the largest sum.</p></section>
<section class="answer-box"><h2>Can the answer be negative?</h2><p>Yes. The subarray must be non-empty. If every value is negative, selecting no values and returning zero is incorrect. Think about the best interval ending at each position and how to track the best one seen so far.</p></section>
<section><h2>Submit and evaluate</h2><p>The built-in problem has two public samples and six hidden tests, a 2-second time limit, and a 256 MB memory limit. Open Signal Surge in the problemset and submit in C++, Java, or Python. The judge evaluates the solution against every configured testcase.</p><p>For an easier first problem, try <a href="/problems/packet-budget">Packet Budget</a>. The <a href="/competitive-programming">CP guide</a> explains judge verdicts.</p></section>''',
        "cta": "Solve Signal Surge in the arena", "href": "/#cp",
    },
]


def page_document(page):
    escape = html.escape
    url = BASE + "/" + page["path"]
    data = {
        "@context": "https://schema.org",
        "@graph": [
            {"@type": "WebPage", "@id": url + "#page", "url": url,
             "name": page["title"], "description": page["description"],
             "inLanguage": "en", "isPartOf": {"@id": BASE + "/#website"},
             "breadcrumb": {"@id": url + "#breadcrumbs"}},
            {"@type": "BreadcrumbList", "@id": url + "#breadcrumbs", "itemListElement": [
                {"@type": "ListItem", "position": 1, "name": "Cyber Algo Arena", "item": BASE + "/"},
                {"@type": "ListItem", "position": 2, "name": page["heading"], "item": url}]},
        ],
    }
    return f'''<!doctype html>
<html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{escape(page['title'])}</title>
<meta name="description" content="{escape(page['description'])}">
<meta name="robots" content="index,follow,max-image-preview:large">
<link rel="canonical" href="{url}"><link rel="icon" href="/favicon.svg" type="image/svg+xml">
<meta property="og:type" content="website"><meta property="og:site_name" content="Cyber Algo Arena">
<meta property="og:title" content="{escape(page['title'])}"><meta property="og:description" content="{escape(page['description'])}">
<meta property="og:url" content="{url}"><meta property="og:image" content="{BASE}/assets/social-card.png">
<meta property="og:image:alt" content="Cyber Algo Arena — Competitive programming and capture the flag, together">
<meta property="og:image:width" content="1200"><meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image"><meta name="twitter:title" content="{escape(page['title'])}">
<meta name="twitter:description" content="{escape(page['description'])}"><meta name="twitter:image" content="{BASE}/assets/social-card.png">
<link rel="stylesheet" href="/search.css">
<script type="application/ld+json">{json.dumps(data, ensure_ascii=False).replace('<', '&lt;')}</script>
</head><body class="learn-page">
<a class="skip" href="#main">Skip to content</a>
<header class="learn-header"><a class="learn-brand" href="/"><span aria-hidden="true">&lt;/&gt;</span> cyber algo <small>ARENA</small></a>
<nav aria-label="Learning guides"><a href="/competitive-programming">CP guide</a><a href="/capture-the-flag">CTF guide</a><a href="/cp-and-ctf">CP + CTF</a><a class="learn-button" href="/#cp">Open arena ↗</a></nav></header>
<main id="main" class="learn-main"><nav class="breadcrumbs" aria-label="Breadcrumb"><a href="/">Cyber Algo Arena</a><span aria-hidden="true">/</span><span>{escape(page['eyebrow'].split(' · ')[0].title())}</span></nav>
<article><header class="learn-hero"><p class="eyebrow">{escape(page['eyebrow'])}</p><h1>{escape(page['heading'])}</h1><p class="lead">{escape(page['answer'])}</p><a class="learn-button" href="{page['href']}">{escape(page['cta'])} ↗</a></header>
<div class="learn-body">{page['body']}</div></article>
<aside class="learn-next"><p class="eyebrow">PUT IT INTO PRACTICE</p><h2>The next challenge is waiting.</h2><p>Choose your track, try an idea, and learn from the result.</p><a class="learn-button" href="{page['href']}">{escape(page['cta'])} ↗</a></aside>
</main><footer class="learn-footer"><a href="/">Cyber Algo Arena</a><span>One arena. Two ways to grow.</span><a href="https://github.com/0xSaadBinSultan/cyber-algo-arena" rel="noopener">Project on GitHub ↗</a></footer>
</body></html>'''


def main():
    for page in PAGES:
        target = PUBLIC / (page["path"] + ".html")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(page_document(page), encoding="utf-8")
    urls = [(BASE + "/", HOME_MODIFIED)] + [
        (BASE + "/" + page["path"], page.get("modified")) for page in PAGES]
    # Editorial modification dates stay fixed across rebuilds.
    sitemap = '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
    sitemap += "\n".join(
        f"  <url><loc>{url}</loc>" + (f"<lastmod>{modified}</lastmod>" if modified else "") + "</url>"
        for url, modified in urls)
    (PUBLIC / "sitemap.xml").write_text(sitemap + "\n</urlset>\n", encoding="utf-8")
    print(f"Built {len(PAGES)} public pages and sitemap.xml")


if __name__ == "__main__":
    main()
