import re

with open('public/admin.html', 'r') as f:
    html = f.read()

subs = [
    ('form-auth-login', 'loginSubmit'),
    ('form-auth-register', 'registerSubmit'),
    ('form-submit-flag', 'submitFlagHandler'),
    ('form-join-team', 'joinTeamSubmit'),
    ('form-create-team', 'createTeamSubmit'),
    ('form-admin-create-chal', 'adminCreateChalSubmit')
]

for form_id, _ in subs:
    pattern = rf"\$\('#{form_id}'\)\.onsubmit = (async \(e\) => {{)"
    replacement = rf"const form_{form_id.replace('-', '_')} = $('#{form_id}');\n    if (form_{form_id.replace('-', '_')}) form_{form_id.replace('-', '_')}.onsubmit = \1"
    html = re.sub(pattern, replacement, html)

html = re.sub(r"\$\('#btn-request-hint'\)\.onclick = \((.*?)\) => {", r"const btn_hint = $('#btn-request-hint');\n        if (btn_hint) btn_hint.onclick = (\1) => {", html)

window_exports = """
    window.showView = showView;
    window.openAuthModal = openAuthModal;
    window.closeAuthModal = closeAuthModal;
    window.logout = logout;
    window.switchChallengeTrack = switchChallengeTrack;
    window.filterCategory = filterCategory;
    window.openChallengeModal = openChallengeModal;
    window.closeModal = closeModal;
    window.switchModalTab = switchModalTab;
    window.filterRadar = filterRadar;
    window.fetchLeaderboard = fetchLeaderboard;
    window.fetchProfile = fetchProfile;
    window.fetchRadarEvents = fetchRadarEvents;
    window.adminToggleFreeze = adminToggleFreeze;
    window.openAdminAuthModal = openAdminAuthModal;
    window.closeAdminAuthModal = closeAdminAuthModal;
    window.adminDeleteChallenge = adminDeleteChallenge;
    window.adminSyncAtCoder = adminSyncAtCoder;
    window.adminSyncCodeChef = adminSyncCodeChef;
    window.adminSyncCodeforces = adminSyncCodeforces;
    window.adminSyncSecurity = adminSyncSecurity;

    window.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            closeModal();
            closeAuthModal();
            if (typeof closeAdminAuthModal === 'function') closeAdminAuthModal();
        }
    });

    document.addEventListener('DOMContentLoaded', () => {
        try {
            if (typeof checkAuthSession === 'function') checkAuthSession();
            if (typeof fetchChallenges === 'function') fetchChallenges();
            if (typeof fetchTeamInfo === 'function') fetchTeamInfo();
            if (typeof fetchLeaderboard === 'function') fetchLeaderboard();
        } catch (err) {
            console.error('Fatal initialization error caught safely:', err);
        }
    });
"""

html = re.sub(r'    window\.addEventListener\(\'DOMContentLoaded\', \(\) => \{.*?\}\);\n', window_exports, html, flags=re.DOTALL)

with open('public/admin.html', 'w') as f:
    f.write(html)
