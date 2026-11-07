"use strict";
const $ = (s, root = document) => root.querySelector(s);
const esc = (v) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const icons = {
  radar:
    '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><path d="m12 12 7-7M12 3v9"/>',
  grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  code: '<path d="m8 6-6 6 6 6m8-12 6 6-6 6M14 3l-4 18"/>',
  flag: '<path d="M5 21V3m0 1c5-5 9 5 14 0v10c-5 5-9-5-14 0"/>',
  cup: '<path d="M7 3h10v6a5 5 0 0 1-10 0V3Zm10 2h4v3a4 4 0 0 1-4 4M7 5H3v3a4 4 0 0 0 4 4m8 9H9m3-7v7"/>',
  chart: '<path d="M4 20V10m8 10V4m8 16v-7"/>',
  history: '<path d="M3 11a9 9 0 1 1 2 7M3 4v7h7m2-5v6l4 2"/>',
  users:
    '<circle cx="9" cy="7" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3m1-17a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 4v3"/>',
  arrow: '<path d="M4 12h16m-6-6 6 6-6 6"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
};
const icon = (n) =>
  `<svg viewBox="0 0 24 24" aria-hidden="true">${icons[n] || icons.code}</svg>`;
document
  .querySelectorAll("[data-icon]")
  .forEach((e) => (e.innerHTML = icon(e.dataset.icon)));
let user = null,
  problems = [],
  contests = [],
  judge = {},
  activeProblem = null,
  activeContest = null,
  authMode = "login",
  category = "ALL",
  practiceRows = [],
  page = "overview";
async function api(path, body) {
  const response = await fetch("/api" + path, {
    method: body ? "POST" : "GET",
    credentials: "same-origin",
    headers: body ? { "Content-Type": "application/json" } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  let data;
  try {
    data = await response.json();
  } catch {
    throw Error(
      "The server returned an unexpected response. Please try again.",
    );
  }
  if (response.status === 401 && user && !path.startsWith("/auth/")) {
    user = null;
    authUI();
    if (path === "/submit" || path === "/run-samples") openAuth();
    throw Error("Your session ended. Please sign in again; your solution is still in the editor.");
  }
  if (!response.ok) throw Error(data.error || data.message || "Request failed");
  return data;
}
function toast(message) {
  $("#toast").textContent = message;
  $("#toast").hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => ($("#toast").hidden = true), 4500);
}
function busy(button, text) {
  button.disabled = true;
  button.dataset.original = button.innerHTML;
  button.textContent = text;
  return () => {
    button.disabled = false;
    button.innerHTML = button.dataset.original;
  };
}
const tag = (text, kind = "") =>
  `<span class="tag ${kind}">${esc(text)}</span>`;
const fmt = (d) =>
  new Date(d).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
const empty = (title, text) =>
  `<div class="panel empty"><h3>${esc(title)}</h3><p>${esc(text)}</p></div>`;
const head = (eyebrow, title, text, action = "") =>
  `<div class="page-head"><div><span class="eyebrow">${eyebrow}</span><h1>${title}</h1><p>${text}</p></div>${action}</div>`;
function authUI() {
  $("#auth-actions").innerHTML = user
    ? '<button class="button subtle" data-action="logout">Sign out</button>'
    : '<button class="button subtle" data-action="login">Sign in</button><button class="button primary" data-action="register">Join the arena</button>';
  $("#side-avatar").textContent = user
    ? user.username.slice(0, 2).toUpperCase()
    : "CA";
  $("#side-user").innerHTML = user
    ? `${esc(user.username)}<small>${user.role === "ADMIN" ? "Club administrator" : "Arena member"}</small>`
    : "Your next chapter<small>Sign in to track progress</small>";
}
async function refresh() {
  [problems, contests] = await Promise.all([
    api("/challenges"),
    api("/contests"),
  ]);
}
async function init() {
  try {
    user = await api("/auth/me");
  } catch {}
  authUI();
  try {
    const health = await api("/health");
    if (health.database !== "UP") {
      $("#connection-notice").hidden = false;
      $("#connection-notice").textContent =
        "Practice preview: database storage is unavailable. Accounts and progress may reset when the server restarts.";
    }
    judge = await api("/judge/status");
    await refresh();
    route();
  } catch (error) {
    $("#view").innerHTML = empty(
      "The arena is taking a moment",
      error.message + " Refresh the page to retry.",
    );
  }
}
function route() {
  page = location.hash.slice(1) || "overview";
  if (
    ![
      "overview",
      "cp",
      "ctf",
      "contests",
      "radar",
      "standings",
      "submissions",
      "team",
      "profile",
    ].includes(page)
  )
    page = "overview";
  document.querySelectorAll("[data-nav]").forEach((e) => {
    e.classList.toggle("active", e.dataset.nav === page);
    if (e.dataset.nav === page) e.setAttribute("aria-current", "page");
    else e.removeAttribute("aria-current");
  });
  $("#page-label").textContent = {
    overview: "Overview",
    cp: "Problemset",
    ctf: "CTF labs",
    contests: "Contests",
    radar: "Contest Radar",
    standings: "Standings",
    submissions: "My submissions",
    team: "My team",
    profile: "My profile",
  }[page];
  ({
    overview: overview,
    cp: cpPage,
    ctf: ctfPage,
    contests: contestPage,
    radar: radarPage,
    standings: standingsPage,
    submissions: submissionsPage,
    team: teamPage,
    profile: profilePage,
  })[page]();
}
function overview() {
  const cp = problems.filter((p) => p.type === "CP" && p.judgeReady),
    ctf = problems.filter((p) => p.type === "CTF"),
    solves = problems.filter((p) => p.solved).length;
  $("#view").innerHTML =
    `<div class="page-head"><div><span class="eyebrow">YOUR SPACE TO GET BETTER</span><h1>${user ? "Welcome back, " + esc(user.username) + "." : "A good day to solve something."}</h1><p>Build your skills, find your people, and make the next breakthrough.</p></div><span class="tag gray">${new Date().toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })}</span></div><section class="hero"><div><span class="eyebrow">THINK IT THROUGH. FIGURE IT OUT.</span><h2 class="hero-title">Small challenges.<br>Real progress.</h2><p>Sharpen your algorithms or follow a digital trail. Pick a track, take your time, and learn something new.</p><div class="hero-buttons"><a href="#cp" class="button primary">Start solving ${icon("arrow")}</a><a href="#ctf" class="button subtle">Explore CTF labs</a></div></div><div class="hero-art" aria-hidden="true"><div class="orbit"></div><div class="art-code"><span>&lt;/&gt;</span></div><div class="art-badge one">flag found ✓</div><div class="art-badge two">All tests passed.</div></div></section><div class="stats">${stat("Practice problems", cp.length, "Samples + hidden tests", "code")}${stat("Security labs", ctf.length, "Follow the clues. Find the flag.", "flag")}${stat("Your solves", solves, user ? "Keep building that momentum" : "Sign in to start your journey", "check")}${stat("Club contests", contests.length, "A little friendly competition", "cup")}</div><div class="section-head"><h2>Choose your next challenge</h2><span class="muted small">Two tracks. One curious mind.</span></div><div class="tracks"><a class="track-card" href="#cp"><div class="track-top"><span class="track-icon">${icon("code")}</span>${tag("COMPETITIVE PROGRAMMING")}</div><h2>Make every test pass.</h2><p>Read the problem, write your solution, and see how it holds up against public and hidden tests.</p><div class="track-footer"><span>C++ · Java · Python</span>Open problemset ↗</div></a><a class="track-card" href="#ctf"><div class="track-top"><span class="track-icon ctf">${icon("flag")}</span>${tag("CAPTURE THE FLAG", "teal")}</div><h2>There’s always a clue.</h2><p>Decode messages, inspect evidence, and read between the lines. Your next flag is waiting.</p><div class="track-footer"><span>Crypto · Forensics · Reverse</span>Explore the labs ↗</div></a></div><div class="content-grid"><section><div class="section-head"><h2>A place to start</h2><a href="#cp">View problemset ↗</a></div><div class="panel">${[
      ...cp,
      ...ctf,
    ]
      .slice(0, 3)
      .map(
        (p, i) =>
          `<div class="problem-row"><span class="problem-number">0${i + 1}</span><div><h3>${esc(p.title)}</h3><p>${p.type === "CP" ? "CP · Input, output, and a little logic" : "CTF · " + esc(p.category.replaceAll("_", " "))}</p></div>${tag(p.solved ? "Solved" : p.difficulty.toLowerCase(), p.solved ? "" : "gray")}<button data-open="${esc(p.id)}">Try it ↗</button></div>`,
      )
      .join(
        "",
      )}</div></section><section><div class="section-head"><h2>Make it a club thing</h2></div><div class="panel panel-pad"><span class="track-icon ctf">${icon("users")}</span><h3 class="subheading">Better together.</h3><p class="muted small">Create a team, join a club contest, and see what you can figure out together.</p><a class="button subtle full" href="#contests">Find a contest ${icon("arrow")}</a></div></section></div>`;
}
function stat(label, value, note, ico) {
  return `<div class="stat"><div class="stat-top">${label}${icon(ico)}</div><strong>${value}</strong><small>${note}</small></div>`;
}
function contextBanner() {
  if (!activeContest) return "";
  return `<div class="contest-banner"><span><strong>${esc(activeContest.title)}</strong> · ${esc(activeContest.status)} · Team contest</span><button class="button subtle" data-action="leave-contest">Back to practice</button></div>`;
}
function cpPage() {
  let cps = problems.filter(
    (p) =>
      p.type === "CP" &&
      (!activeContest || activeContest.challengeIds.includes(p.id)),
  );
  $("#view").innerHTML =
    contextBanner() +
    head(
      "COMPETITIVE PROGRAMMING",
      "The problemset.",
      "One problem, one idea, one step forward. Choose something and give it a go.",
      `<span class="tag gray">${cps.filter((p) => p.judgeReady).length} judge-ready</span>`,
    ) +
    `<div class="toolbar"><input id="cp-search" type="search" aria-label="Search problems" placeholder="Search by title or problem ID"><select id="cp-difficulty" aria-label="Filter difficulty"><option value="ALL">All difficulties</option><option>EASY</option><option>MEDIUM</option><option>HARD</option></select><select id="cp-source" aria-label="Filter source"><option value="local">Arena problems</option><option value="external">External practice</option></select></div><div id="cp-results"></div><p class="muted small">Arena problems use server-side samples and hidden tests. External references open on their original platform.</p>`;
  const render = () => renderCp(cps);
  $("#cp-search").addEventListener("input", render);
  $("#cp-difficulty").addEventListener("change", render);
  $("#cp-source").addEventListener("change", render);
  render();
}
function renderCp(cps) {
  const q = $("#cp-search").value.toLowerCase(),
    difficulty = $("#cp-difficulty").value,
    external = $("#cp-source").value === "external";
  const rows = cps.filter(
    (p) =>
      (external ? p.externalReference : p.judgeReady) &&
      (difficulty === "ALL" || p.difficulty === difficulty) &&
      (p.title + " " + p.id).toLowerCase().includes(q),
  );
  $("#cp-results").innerHTML = rows.length
    ? `<div class="panel table-wrap"><table><thead><tr><th>#</th><th>Problem</th><th>Difficulty</th><th>Limits</th><th>Status</th></tr></thead><tbody>${rows.map((p, i) => `<tr><td class="muted mono">${String(i + 1).padStart(2, "0")}</td><td>${p.sourceUrl ? `<a class="problem-link" href="${esc(p.sourceUrl)}" target="_blank" rel="noopener noreferrer">${esc(p.title)} ↗</a>` : `<button class="problem-link" data-open="${esc(p.id)}">${esc(p.title)} ↗</button>`}<small>${esc(p.id)} · ${external ? "Submit on the original platform" : `${p.basePoints} points · ${p.sampleCases.length} samples · ${p.hiddenTestCount} hidden tests`}</small></td><td>${tag(p.difficulty.toLowerCase(), p.difficulty === "EASY" ? "" : "orange")}</td><td class="muted small">${p.timeLimitMs / 1000}s / ${p.memoryLimitMb} MB</td><td>${tag(p.solved ? "Solved ✓" : external ? "External" : "Unsolved", p.solved ? "" : "gray")}</td></tr>`).join("")}</tbody></table></div>`
    : empty(
        "Nothing here yet",
        "Try another filter, or choose an Arena problem.",
      );
}
const categoryColor = (cat) =>
  ({
    CRYPTO: "#c084fc",
    FORENSICS: "var(--orange)",
    REVERSE_ENGINEERING: "#4ade80",
    WEB: "var(--teal)",
    PWN: "var(--red)",
  }[cat] || "var(--teal)");

function ctfPage() {
  const labs = problems.filter(
    (p) =>
      p.type === "CTF" &&
      (!activeContest || activeContest.challengeIds.includes(p.id)),
  );
  const cats = ["ALL", ...new Set(labs.map((p) => p.category))];
  if (!cats.includes(category)) category = "ALL";
  $("#view").innerHTML =
    contextBanner() +
    head(
      "CAPTURE THE FLAG",
      "Follow your curiosity.",
      "A small mystery, a few clues, and a flag with your name on it.",
    ) +
    `<div class="category-pills">${cats.map((c) => `<button class="pill ${c === category ? "active" : ""}" data-category="${esc(c)}">${esc(c === "ALL" ? "All labs" : c.replaceAll("_", " ").toLowerCase())} <span class="muted">${c === "ALL" ? labs.length : labs.filter((p) => p.category === c).length}</span></button>`).join("")}</div><div class="ctf-grid">${labs
      .filter((p) => category === "ALL" || p.category === category)
      .map(
        (p) =>
          `<article class="lab-card"><div class="lab-meta"><span class="track-icon ctf">${icon("flag")}</span><div style="display:flex;gap:6px;align-items:center;flex-wrap:wrap">${p.firstBloodUserId ? '<span class="tag blood">🩸 First blood</span>' : ""}${tag(p.solved ? "Solved ✓" : p.difficulty.toLowerCase(), p.solved ? "green" : "gray")}</div></div><span class="eyebrow" style="color:${categoryColor(p.category)};margin-bottom:10px">${esc(p.category.replaceAll("_", " "))}</span><h3>${esc(p.title)}</h3><p>${esc({ CRYPTO: "An encoded note landed in the club chat. Can you read it?", FORENSICS: "A late-night request left a trail. Take a closer look.", REVERSE_ENGINEERING: "The checker knows the answer. Work backwards.", WEB: "An intercepted HTTP session header contains secrets. Inspect the cookie." }[p.category] || "Inspect the evidence, connect the clues, and recover the flag.")}</p><div class="lab-bottom"><span>${p.currentPoints || p.basePoints} <span class="muted">pts</span> <span class="muted small">· ${p.solveCount || 0} solves</span></span><button class="button subtle" data-open="${esc(p.id)}">Open lab ↗</button></div></article>`,
      )
      .join(
        "",
      )}</div><div class="callout" style="margin-top:25px">New to CTF? Start with “A message between lectures”. Everything you need is inside the challenge. Submit flags exactly as shown, including the braces.</div>`;
}
function contestPage() {
  activeContest = null;
  $("#view").innerHTML =
    head(
      "A LITTLE FRIENDLY COMPETITION",
      "Bring your best ideas.",
      "Club rounds with shared problems, timed submissions, and their own standings.",
      user?.role === "ADMIN"
        ? '<button class="button primary" data-action="host">+ Host a contest</button>'
        : "",
    ) +
    (contests.length
      ? contests
          .slice()
          .sort((a, b) => new Date(b.startTime) - new Date(a.startTime))
          .map(
            (c) =>
              `<article class="contest-card"><div class="contest-top"><div><span class="eyebrow">CLUB ROUND · ${c.challengeIds.length} PROBLEMS</span><h3>${esc(c.title)}</h3></div><span>${tag(c.status, c.status === "LIVE" ? "" : c.status === "UPCOMING" ? "teal" : "gray")}</span></div><p>${esc(c.description)}</p><div class="contest-info"><span>Starts ${fmt(c.startTime)}</span><span>Ends ${fmt(c.endTime)}</span><span>${c.registeredTeamsCount} teams registered</span></div><div class="contest-buttons">${c.registered ? '<span class="button subtle">Registered ✓</span>' : c.status !== "ENDED" ? `<button class="button primary" data-register="${esc(c.id)}">Register your team</button>` : ""}<button class="button ${c.registered ? "primary" : "subtle"}" data-enter="${esc(c.id)}">Open round ↗</button><button class="button subtle" data-standings="${esc(c.id)}">Standings</button></div></article>`,
          )
          .join("")
      : empty(
          "Your next round starts here",
          user?.role === "ADMIN"
            ? "Host your first contest using the button above."
            : "No club contests have been published yet. In the meantime, the practice arena is open.",
        )) +
    '<p class="muted small">Create or join a team before registering. Contest points are separate from practice progress. Problems are drawn from the public practice catalog.</p>';
}
let radarEvents = [],
  radarPlatform = "ALL",
  radarLoading = false;
const platformLinks = {
  CODEFORCES: "https://codeforces.com/contests",
  CTFTIME: "https://ctftime.org/event/list/upcoming",
  ATCODER: "https://atcoder.jp/contests/",
  CODECHEF: "https://www.codechef.com/contests",
};
const platformName = (p) =>
  ({
    CODEFORCES: "Codeforces",
    CTFTIME: "CTFtime",
    ATCODER: "AtCoder",
    CODECHEF: "CodeChef",
  })[p] || p;
function scheduleUrl(value) {
  try {
    const u = new URL(value);
    return u.protocol === "https:" &&
      /(^|\.)(codeforces\.com|ctftime\.org|atcoder\.jp|codechef\.com)$/.test(
        u.hostname,
      )
      ? u.href
      : null;
  } catch {
    return null;
  }
}
function countdown(start) {
  const minutes = Math.max(
    0,
    Math.floor((new Date(start) - Date.now()) / 60000),
  );
  return minutes >= 1440
    ? Math.floor(minutes / 1440) +
        "d " +
        Math.floor((minutes % 1440) / 60) +
        "h"
    : Math.floor(minutes / 60) + "h " + (minutes % 60) + "m";
}
async function radarPage() {
  $("#view").innerHTML =
    head(
      "LOOK BEYOND THE CLUB",
      "Your next contest is out there.",
      "Upcoming CP and CTF events from the wider community. Times are shown in your local timezone.",
      '<button class="button secondary" id="refresh-radar">Refresh schedules</button>',
    ) +
    '<div class="category-pills" id="radar-filters"></div><div id="radar-results"><div class="panel loading">Checking contest schedules…</div></div><div class="section-head"><h2>Explore the official calendars</h2></div><div class="tracks">' +
    Object.entries(platformLinks)
      .map(
        ([platform, url]) =>
          `<a class="track-card" href="${url}" target="_blank" rel="noopener noreferrer"><h3>${platformName(platform)} ↗</h3><p>${platform === "CTFTIME" ? "Security competitions and team events" : "Algorithm contests and programming rounds"}</p><span class="tag gray">Official schedule</span></a>`,
      )
      .join("") +
    '</div><p class="muted small">External schedules can change. Check the official event page before registering. Feed failures are shown as unavailable; no event dates are invented.</p>';
  $("#refresh-radar").onclick = loadRadar;
  if (radarEvents.length) renderRadar();
  else await loadRadar();
}
async function loadRadar() {
  if (radarLoading) return;
  radarLoading = true;
  const button = $("#refresh-radar"),
    done = button ? busy(button, "Checking…") : () => {};
  try {
    radarEvents = await api("/events/upcoming");
    if (page === "radar") renderRadar();
  } catch (error) {
    if (page === "radar")
      $("#radar-results").innerHTML = empty(
        "Schedules are temporarily unavailable",
        "Use an official calendar below, or try refresh in a moment.",
      );
  } finally {
    radarLoading = false;
    done();
  }
}
function renderRadar() {
  if (!$("#radar-filters")) return;
  $("#radar-filters").innerHTML = ["ALL", ...Object.keys(platformLinks)]
    .map(
      (p) =>
        `<button class="pill ${p === radarPlatform ? "active" : ""}" data-radar-platform="${p}">${p === "ALL" ? "All platforms" : platformName(p)}</button>`,
    )
    .join("");
  document.querySelectorAll("[data-radar-platform]").forEach(
    (button) =>
      (button.onclick = () => {
        radarPlatform = button.dataset.radarPlatform;
        renderRadar();
      }),
  );
  const events = radarEvents.filter((event) => {
    const start = new Date(event.startTimeIso || event.startTime).getTime();
    return (
      Number.isFinite(start) &&
      start + Number(event.durationSeconds || 0) * 1000 > Date.now() &&
      (radarPlatform === "ALL" || event.platform === radarPlatform) &&
      scheduleUrl(event.url)
    );
  });
  $("#radar-results").innerHTML = events.length
    ? '<div class="ctf-grid">' +
      events
        .map((event) => {
          const start = event.startTimeIso || event.startTime,
            live = new Date(start) <= new Date(),
            url = scheduleUrl(event.url);
          return `<article class="lab-card"><div class="lab-meta">${tag(platformName(event.platform), "teal")}${tag(live ? "Live now" : "Upcoming", live ? "" : "gray")}</div><h3>${esc(event.title)}</h3><p>Starts ${fmt(start)}<br>${Math.round(Number(event.durationSeconds || 0) / 60)} minute contest<br>${live ? "Contest in progress" : "Starts in " + countdown(start)}</p><div class="lab-bottom"><span class="muted small">${esc(event.format || "Contest")}</span><a class="button subtle" href="${esc(url)}" target="_blank" rel="noopener noreferrer">Event page ↗</a></div></article>`;
        })
        .join("") +
      "</div>"
    : empty(
        "No verified events for this filter",
        "Some providers may be unavailable. Their official calendars are always linked below.",
      );
}
async function standingsPage(contestId) {
  $("#view").innerHTML =
    head(
      "THE PEOPLE PUTTING IN THE WORK",
      "The standings.",
      "Every solve counts. Keep showing up.",
    ) +
    `<div class="toolbar"><label style="margin:0;min-width:240px">Leaderboard<select id="board-select"><option value="">Individual practice</option>${contests.map((c) => `<option value="${esc(c.id)}">${esc(c.title)} · Teams</option>`).join("")}</select></label></div><div id="board-results" class="panel"><div class="loading">Loading standings…</div></div>`;
  $("#board-select").value = contestId || "";
  $("#board-select").addEventListener("change", () =>
    loadBoard($("#board-select").value),
  );
  await loadBoard(contestId || "");
}
async function loadBoard(id) {
  try {
    const rows = await api(
      id
        ? `/contests/${encodeURIComponent(id)}/standings`
        : "/standings/practice",
    );
    if (!id) practiceRows = rows;
    $("#board-results").innerHTML = rows.length
      ? `<div class="table-wrap"><table><thead><tr><th>Rank</th><th>${id ? "Team" : "Member"}</th><th>Solves</th><th>Points</th></tr></thead><tbody>${rows.map((r) => `<tr><td class="mono">${r.rank <= 3 ? tag("#" + r.rank, r.rank === 1 ? "" : "gray") : "#" + r.rank}</td><td>${esc(r.name)}</td><td>${r.solves}</td><td class="mono" style="color:var(--accent)">${r.score}</td></tr>`).join("")}</tbody></table></div>`
      : '<div class="empty">No scores yet. There’s room for a first solve.</div>';
  } catch (e) {
    $("#board-results").innerHTML =
      `<div class="empty">${esc(e.message)}</div>`;
  }
}
async function submissionsPage() {
  if (!user) {
    $("#view").innerHTML =
      head(
        "YOUR LEARNING TRAIL",
        "Keep track of every attempt.",
        "Sign in to see your submissions and verdicts.",
      ) + '<button class="button primary" data-action="login">Sign in</button>';
    return;
  }
  $("#view").innerHTML =
    head(
      "YOUR LEARNING TRAIL",
      "Every attempt teaches you something.",
      "Public samples are for feedback. Full submissions count toward your score.",
    ) +
    '<div id="submission-results" class="panel"><p class="loading">Loading submissions…</p></div>';
  try {
    const rows = await api("/submissions/me");
    $("#submission-results").innerHTML = rows.length
      ? `<div class="table-wrap"><table><thead><tr><th>Problem</th><th>Verdict</th><th>Context</th><th>Submitted</th></tr></thead><tbody>${rows.map((s) => `<tr><td>${esc(problems.find((p) => p.id === s.challengeId)?.title || s.challengeId)}<small>${esc(s.id)}</small></td><td>${tag(s.status.replaceAll("_", " "), s.status === "ACCEPTED" ? "" : s.status === "INVALID" ? "orange" : "red")}</td><td class="muted">${esc(s.contestId === "GLOBAL" ? "Practice" : contests.find((c) => c.id === s.contestId)?.title || s.contestId)}</td><td class="muted small">${fmt(s.timestamp)}</td></tr>`).join("")}</tbody></table></div>`
      : '<div class="empty">Your first submission will show up here. Pick a problem to get started.</div>';
  } catch (e) {
    toast(e.message);
  }
}
async function profilePage() {
  if (!user) {
    $("#view").innerHTML =
      head(
        "YOUR PROGRESS",
        "Every step adds up.",
        "Sign in to see your CP and CTF progress.",
      ) + '<button class="button primary" data-action="login">Sign in</button>';
    return;
  }
  $("#view").innerHTML =
    head(
      "YOUR PROGRESS",
      esc(user.username),
      "Your practice scores, track progress, and solved challenges.",
    ) + '<div id="profile-results" class="loading">Loading your profile…</div>';
  try {
    const p = await api("/users/me/profile");
    if (page !== "profile") return;
    $("#profile-results").className = "";
    $("#profile-results").innerHTML =
      '<div class="stats">' +
      stat(
        "Practice points",
        p.personalScore,
        "One account, both tracks",
        "chart",
      ) +
      stat("CP solves", p.cpSolvesCount, p.cpScore + " CP points", "code") +
      stat("CTF solves", p.ctfSolvesCount, p.ctfScore + " CTF points", "flag") +
      stat(
        "Total solves",
        p.solvesCount,
        "Keep building your skills",
        "check",
      ) +
      '</div><div class="panel panel-pad"><h2>Your solved problems</h2>' +
      ((p.solvedChallenges || []).length
        ? '<div class="category-pills">' +
          p.solvedChallenges
            .map(
              (id) =>
                `<button class="button subtle" data-open="${esc(id)}">${esc(problems.find((item) => item.id === id)?.title || id)} ↗</button>`,
            )
            .join("") +
          "</div>"
        : '<p class="muted">Your first solve will show up here.</p>') +
      '</div><p class="muted small">Contest results stay in their own standings. Practice scores are shown above.</p>';
  } catch (error) {
    if ($("#profile-results"))
      $("#profile-results").textContent = error.message;
  }
}
async function teamPage() {
  if (!user) {
    $("#view").innerHTML =
      head(
        "BETTER TOGETHER",
        "Find your people.",
        "Sign in to create a team or join your friends.",
      ) + '<button class="button primary" data-action="login">Sign in</button>';
    return;
  }
  $("#view").innerHTML = head(
    "BETTER TOGETHER",
    "Your club, your team.",
    "Build a team for contests. You can still practice on your own.",
  );
  try {
    const info = await api("/teams/my-team");
    const t = info.team || info;
    if (t.id || t.teamId) {
      $("#view").innerHTML +=
        `<div class="panel panel-pad"><span class="eyebrow">YOUR TEAM</span><h2>${esc(t.teamName || t.name)}</h2><p class="muted">Team ID: ${esc(t.id || t.teamId)}</p>${t.passkey ? `<p>Invite passkey: <code>${esc(t.passkey)}</code></p>` : ""}<p class="muted small">Invite teammates using your team name and the team password you chose.</p><a href="#contests" class="button primary">Find a contest ↗</a></div>`;
      return;
    }
  } catch {}
  $("#view").innerHTML +=
    `<div class="two-col"><form class="panel panel-pad" id="create-team"><h2>Start a team</h2><label>Team name<input name="name" required maxlength="60" placeholder="The Debugging Society"></label><label>Team password<input type="password" name="teamPassword" required minlength="6" autocomplete="new-password"></label><button class="button primary">Create team</button></form><form class="panel panel-pad" id="join-team"><h2>Join your friends</h2><label>Team name<input name="teamName" required placeholder="Ask your team captain"></label><label>Team password<input type="password" name="teamPassword" required autocomplete="off"></label><button class="button secondary">Join team</button></form></div>`;
  $("#create-team").onsubmit = (e) => teamSubmit(e, "/teams/create");
  $("#join-team").onsubmit = (e) => teamSubmit(e, "/teams/join");
}
async function teamSubmit(e, path) {
  e.preventDefault();
  const data = Object.fromEntries(new FormData(e.target));
  if (data.name) data.teamName = data.name;
  const done = busy($("button", e.target), "Working…");
  try {
    await api(path, data);
    user = await api("/auth/me");
    authUI();
    await teamPage();
    toast("Your team is ready.");
  } catch (error) {
    toast(error.message);
  } finally {
    done();
  }
}
function markdown(text) {
  return String(text || "")
    .split(/(```[\s\S]*?```)/g)
    .map((part) => {
      if (part.startsWith("```"))
        return (
          "<pre>" +
          esc(part.replace(/^```[^\n]*\n?/, "").replace(/```$/, "")) +
          "</pre>"
        );
      let output = "",
        paragraph = [];
      const flush = () => {
        if (paragraph.length) {
          output += "<p>" + paragraph.map(esc).join("<br>") + "</p>";
          paragraph = [];
        }
      };
      for (const line of part.split("\n")) {
        const heading = line.match(/^#{1,3} (.*)$/);
        if (heading) {
          flush();
          output += "<h2>" + esc(heading[1]) + "</h2>";
        } else if (!line.trim()) flush();
        else paragraph.push(line);
      }
      flush();
      return output;
    })
    .join("");
}
const templates = {
  cpp: "#include <iostream>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n    // Your solution goes here.\n    return 0;\n}\n",
  java: "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // Your solution goes here.\n    }\n}\n",
  python:
    'import sys\n\ndef solve():\n    # Your solution goes here.\n    pass\n\nif __name__ == "__main__":\n    solve()\n',
};
function draftKey(id, lang) {
  return "arena_draft_" + id + "_" + lang;
}
async function openProblem(id) {
  try {
    const p = await api("/challenges/" + encodeURIComponent(id));
    activeProblem = p;
    $("#problem-track").textContent =
      (activeContest ? activeContest.title + " / " : "PRACTICE / ") +
      (p.type === "CP" ? "COMPETITIVE PROGRAMMING" : "CAPTURE THE FLAG");
    const catClass = p.category === "CRYPTO" ? "purple" : p.category === "REVERSE_ENGINEERING" ? "green" : p.category === "WEB" ? "teal" : p.category === "FORENSICS" ? "orange" : "gray";
    const info = `<h2 id="problem-title">${esc(p.title)}</h2><div style="display:flex;gap:9px;flex-wrap:wrap;margin-bottom:25px">${tag(p.difficulty.toLowerCase())}${tag(p.basePoints + " points", "gray")}${p.type === "CP" ? tag(p.timeLimitMs / 1000 + "s · " + p.memoryLimitMb + " MB", "gray") : tag(p.category.replaceAll("_", " "), catClass)}</div>`;
    const samples = (p.sampleCases || [])
      .map(
        (s, i) =>
          `<div class="sample-box">
             <div class="sample-header">
               <span class="subheading" style="margin:0;font-size:13px">Sample ${i + 1}</span>
             </div>
             <div class="sample-io">
               <div class="sample-block">
                 <div class="sample-label-row">
                   <span class="sample-label">Input</span>
                   <button type="button" class="copy-btn" data-copy="${esc(s.input)}">Copy</button>
                 </div>
                 <pre>${esc(s.input)}</pre>
               </div>
               <div class="sample-block">
                 <div class="sample-label-row">
                   <span class="sample-label">Expected Output</span>
                   <button type="button" class="copy-btn" data-copy="${esc(s.output)}">Copy</button>
                 </div>
                 <pre>${esc(s.output)}</pre>
               </div>
             </div>
           </div>`,
      )
      .join("");
    const aiTutorWidget = `
      <div class="ai-tutor-box">
        <div class="ai-tutor-head">
          <div style="display:flex;align-items:center;gap:8px">
            <span class="ai-sparkle">✦</span>
            <strong>Gemini AI Tutor</strong>
            <span class="tag gray small">Sanitized guidance · No spoilers</span>
          </div>
          <button type="button" class="button subtle small" id="ai-toggle-btn">Ask Tutor ▾</button>
        </div>
        <div id="ai-drawer" class="ai-drawer" hidden>
          <p class="muted small" style="margin:0 0 10px">Need a nudge? The AI Tutor guides your thinking without revealing the flag or hidden tests.</p>
          <div class="ai-action-buttons">
            <button type="button" class="button subtle small" data-ai="HINT" data-level="1">💡 Concept Nudge (L1)</button>
            <button type="button" class="button subtle small" data-ai="HINT" data-level="2">🔍 Algorithm Strategy (L2)</button>
            <button type="button" class="button subtle small" data-ai="EXPLAIN">📖 Explain Problem</button>
            ${p.type === "CP" ? '<button type="button" class="button subtle small" data-ai="DEBUG">🐛 Debug Advice</button>' : ""}
          </div>
          <div id="ai-feedback" class="ai-feedback" hidden>
            <div id="ai-feedback-text" class="ai-feedback-text"></div>
          </div>
        </div>
      </div>
    `;
    $("#problem-body").innerHTML =
      info +
      (p.type === "CP"
        ? `<div class="workspace"><div><article class="statement">${markdown(p.description)}</article>${samples}${aiTutorWidget}</div><section class="editor-panel"><div class="editor-head"><label>Language<select id="code-language"><option value="cpp">C++</option><option value="java">Java</option><option value="python">Python 3</option></select></label><span class="tag gray">${p.hiddenTestCount} hidden tests</span></div><label for="source-code" class="sample-label">Your solution</label><textarea id="source-code" aria-label="Your solution" spellcheck="false" autocapitalize="off" autocomplete="off"></textarea><div class="editor-actions"><button type="button" class="button subtle" id="run-samples">Run samples</button><button type="button" class="button primary" id="submit-code">Submit solution ${icon("arrow")}</button></div><div id="verdict" class="verdict" role="status">${judge.configured ? "Ready when you are. Run samples first, then submit against all tests." : "The execution service has not been configured by the host yet. You can read the problem and save a draft."}</div><p class="small muted">Samples don’t affect your score. Submit runs all samples and hidden tests. Java receives 2× time for JVM startup. Drafts are saved in this browser.</p></section></div>`
        : `<div class="ctf-workspace">${p.firstBloodUserId ? '<div class="first-blood-banner"><span>🩸 First Blood claimed by competitor</span></div>' : ""}<article class="statement">${markdown(p.description)}</article>${p.hasAttachment ? `<div style="margin:16px 0"><a class="button secondary" href="/api/challenges/${encodeURIComponent(p.id)}/download">Download ${esc(p.attachmentFileName)} ${p.attachmentSize ? `<span class="muted small">(${Math.round(p.attachmentSize / 1024)} KB)</span>` : ""} ↓</a></div>` : ""}<div class="subheading"><button type="button" class="button subtle" id="hint-button">Need a nudge? Unlock hint (${p.hintCost} point penalty)</button><p id="hint-text" class="muted small"></p></div>${aiTutorWidget}<form id="flag-form" style="margin-top:20px"><label>Recovered flag<input id="flag-input" placeholder="flag{your_discovery}" required autocomplete="off" spellcheck="false"></label><button class="button primary full">Submit flag ${icon("arrow")}</button></form><div id="verdict" class="verdict" role="status" style="margin-top:18px">${p.solved && !activeContest ? "You’ve already solved this lab. Nice work." : "Read the evidence. Find the flag. Take your time."}</div></div>`);
    $("#problem-dialog").showModal();

    // Copy buttons
    document.querySelectorAll(".copy-btn").forEach((btn) => {
      btn.onclick = async () => {
        const text = btn.dataset.copy;
        try {
          await navigator.clipboard.writeText(text);
          btn.textContent = "Copied! ✓";
          btn.classList.add("copied");
          setTimeout(() => {
            btn.textContent = "Copy";
            btn.classList.remove("copied");
          }, 1800);
        } catch {
          const ta = document.createElement("textarea");
          ta.value = text;
          document.body.appendChild(ta);
          ta.select();
          document.execCommand("copy");
          document.body.removeChild(ta);
          btn.textContent = "Copied! ✓";
          btn.classList.add("copied");
          setTimeout(() => {
            btn.textContent = "Copy";
            btn.classList.remove("copied");
          }, 1800);
        }
      };
    });

    // AI Tutor
    const aiToggle = $("#ai-toggle-btn");
    const aiDrawer = $("#ai-drawer");
    if (aiToggle && aiDrawer) {
      aiToggle.onclick = () => {
        aiDrawer.hidden = !aiDrawer.hidden;
        aiToggle.textContent = aiDrawer.hidden ? "Ask Tutor ▾" : "Close Tutor ▴";
      };
      aiDrawer.querySelectorAll("[data-ai]").forEach((btn) => {
        btn.onclick = async () => {
          if (!user) {
            openAuth();
            return;
          }
          const done = busy(btn, "Consulting AI…");
          const feedback = $("#ai-feedback");
          const feedbackText = $("#ai-feedback-text");
          feedback.hidden = false;
          feedbackText.textContent = "Analyzing problem and formulating guidance…";
          try {
            const body = {
              challengeId: p.id,
              mode: btn.dataset.ai,
              hintLevel: btn.dataset.level || "1",
              code: p.type === "CP" ? $("#source-code")?.value || "" : "",
              compilerError: $("#verdict")?.textContent || "",
            };
            const res = await api("/ai/tutor", body);
            feedbackText.innerHTML = markdown(res.message);
          } catch (err) {
            feedbackText.textContent = err.message;
          } finally {
            done();
          }
        };
      });
    }

    if (p.type === "CP") {
      let lang = "cpp";
      const editor = $("#source-code");
      const load = () =>
        (editor.value =
          localStorage.getItem(draftKey(id, lang)) || templates[lang]);
      load();
      $("#code-language").onchange = (e) => {
        lang = e.target.value;
        load();
      };
      editor.oninput = () =>
        localStorage.setItem(draftKey(id, lang), editor.value);
      $("#run-samples").onclick = (e) => submitSolution(e.target, true);
      $("#submit-code").onclick = (e) => submitSolution(e.currentTarget, false);
    } else {
      $("#flag-form").onsubmit = (e) => {
        e.preventDefault();
        submitSolution($("button", e.target), false);
      };
      $("#hint-button").onclick = async (e) => {
        if (!user) {
          openAuth();
          return;
        }
        const done = busy(e.currentTarget, "Opening hint…");
        try {
          const d = await api("/hints/" + encodeURIComponent(p.id), {});
          $("#hint-text").textContent = d.hint || d.hintText || d.message;
          toast("Hint unlocked");
        } catch (error) {
          toast(error.message);
        } finally {
          done();
        }
      };
    }
  } catch (e) {
    toast(e.message);
  }
}
async function submitSolution(button, samples) {
  if (!user) {
    openAuth();
    return;
  }
  const p = activeProblem,
    body = {
      challengeId: p.id,
      payload:
        p.type === "CP" ? $("#source-code").value : $("#flag-input").value,
      language: p.type === "CP" ? $("#code-language").value : "",
      contestId: activeContest?.id || "GLOBAL",
    };
  if (!body.payload.trim()) {
    toast("Add your " + (p.type === "CP" ? "solution" : "flag") + " first.");
    return;
  }
  const done = busy(button, samples ? "Running samples…" : "Judging…");
  const buttons = document.querySelectorAll("#run-samples,#submit-code");
  buttons.forEach((b) => (b.disabled = true));
  $("#verdict").className = "verdict";
  $("#verdict").textContent = samples
    ? "Running public samples…"
    : "Checking your submission…";
  try {
    const result = await api(samples ? "/run-samples" : "/submit", body);
    $("#verdict").className =
      "verdict " + (result.status === "ACCEPTED" ? "accepted" : "error");
    $("#verdict").textContent =
      (samples ? "SAMPLES: " : "") +
      result.status.replaceAll("_", " ") +
      "\n" +
      result.message;
    if (!samples && result.status === "ACCEPTED") {
      toast("Accepted. You figured it out!");
      await refresh();
      route();
    }
  } catch (e) {
    $("#verdict").className = "verdict error";
    $("#verdict").textContent = e.message;
  } finally {
    done();
    buttons.forEach((b) => (b.disabled = false));
  }
}
function openAuth(mode = "login") {
  authMode = mode;
  $("#auth-title").textContent =
    mode === "login" ? "Good to see you." : "Make yourself at home.";
  $("#auth-intro").textContent =
    mode === "login"
      ? "Sign in to save your progress and join contests."
      : "One account for algorithms, security labs, and your club.";
  $("#email-label").hidden = mode === "login";
  $("#auth-submit").textContent =
    mode === "login" ? "Sign in" : "Create account";
  $("#auth-switch").textContent =
    mode === "login"
      ? "New here? Create an account"
      : "Already a member? Sign in";
  $("#auth-error").textContent = "";
  $("[name=password]", $("#auth-form")).autocomplete =
    mode === "login" ? "current-password" : "new-password";
  if (!$("#auth-dialog").open) $("#auth-dialog").showModal();
}
$("#auth-switch").onclick = () =>
  openAuth(authMode === "login" ? "register" : "login");
$("#auth-form").onsubmit = async (e) => {
  e.preventDefault();
  const done = busy($("#auth-submit"), "Just a moment…");
  try {
    user = await api(
      "/auth/" + authMode,
      Object.fromEntries(new FormData(e.target)),
    );
    $("#auth-dialog").close();
    authUI();
    await refresh();
    route();
    toast("Welcome, " + user.username + ".");
  } catch (err) {
    $("#auth-error").textContent = err.message;
  } finally {
    done();
  }
};
function openHost() {
  if (user?.role !== "ADMIN") return;
  const form = $("#host-form");
  form.reset();
  const local = (d) =>
    new Date(d.getTime() - d.getTimezoneOffset() * 60000)
      .toISOString()
      .slice(0, 16);
  form.elements.start.value = local(new Date(Date.now() + 5 * 60000));
  form.elements.end.value = local(new Date(Date.now() + 65 * 60000));
  $("#host-problems").innerHTML = problems
    .filter((p) => p.type === "CTF" || p.judgeReady)
    .map(
      (p) =>
        `<label class="check-label"><input type="checkbox" name="problem" value="${esc(p.id)}">${esc(p.title)} <span class="muted">${p.type} · ${p.basePoints} pts</span></label>`,
    )
    .join("");
  $("#host-error").textContent = "";
  $("#host-dialog").showModal();
}
$("#host-form").onsubmit = async (e) => {
  e.preventDefault();
  const form = e.target,
    data = new FormData(form),
    done = busy(
      $("button[type=submit]", form) || $("button.primary", form),
      "Publishing…",
    );
  try {
    await api("/admin/contests", {
      title: data.get("title"),
      description: data.get("description"),
      startTime: new Date(data.get("start")).toISOString(),
      endTime: new Date(data.get("end")).toISOString(),
      challengeIds: data.getAll("problem"),
    });
    $("#host-dialog").close();
    await refresh();
    contestPage();
    toast("Your contest is published.");
  } catch (error) {
    $("#host-error").textContent = error.message;
  } finally {
    done();
  }
};
document.addEventListener("click", async (e) => {
  if (e.target.closest(".skip")) { e.preventDefault(); $("#main").focus(); return; }
  const close = e.target.closest("[data-close]");
  if (close) {
    close.closest("dialog").close();
    return;
  }
  const open = e.target.closest("[data-open]");
  if (open) {
    openProblem(open.dataset.open);
    return;
  }
  const cat = e.target.closest("[data-category]");
  if (cat) {
    category = cat.dataset.category;
    ctfPage();
    return;
  }
  const reg = e.target.closest("[data-register]");
  if (reg) {
    if (!user) {
      openAuth();
      return;
    }
    const done = busy(reg, "Registering…");
    try {
      await api(
        "/contests/" + encodeURIComponent(reg.dataset.register) + "/register",
        {},
      );
      await refresh();
      contestPage();
      toast("Your team is registered.");
    } catch (err) {
      toast(err.message);
    } finally {
      done();
    }
    return;
  }
  const enter = e.target.closest("[data-enter]");
  if (enter) {
    activeContest = contests.find((c) => c.id === enter.dataset.enter);
    location.hash = activeContest.challengeIds.some(
      (id) => problems.find((p) => p.id === id)?.type === "CP",
    )
      ? "cp"
      : "ctf";
    return;
  }
  const board = e.target.closest("[data-standings]");
  if (board) {
    location.hash = "standings";
    setTimeout(() => standingsPage(board.dataset.standings), 0);
    return;
  }
  const action = e.target.closest("[data-action]")?.dataset.action;
  if (action === "login" || action === "register") openAuth(action);
  if (action === "logout") {
    try {
      await api("/auth/logout", {});
      user = null;
      authUI();
      await refresh();
      route();
      toast("You’re signed out.");
    } catch (err) {
      toast(err.message);
    }
  }
  if (action === "host") openHost();
  if (action === "leave-contest") {
    activeContest = null;
    route();
  }
});
window.addEventListener("hashchange", route);
init();
