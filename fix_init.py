import re

with open('public/index.html', 'r') as f:
    html = f.read()

# Make onsubmits safe
subs = [
    ('form-auth-login', 'loginSubmit'),
    ('form-auth-register', 'registerSubmit'),
    ('form-submit-flag', 'submitFlagHandler'),
    ('form-join-team', 'joinTeamSubmit'),
    ('form-create-team', 'createTeamSubmit')
]

for form_id, _ in subs:
    pattern = rf"\$\('#{form_id}'\)\.onsubmit = (async \(e\) => {{)"
    replacement = rf"const form_{form_id.replace('-', '_')} = $('#{form_id}');\n    if (form_{form_id.replace('-', '_')}) form_{form_id.replace('-', '_')}.onsubmit = \1"
    html = re.sub(pattern, replacement, html)

# Make sure other .onclick are safe
html = re.sub(r"\$\('#btn-request-hint'\)\.onclick = \((.*?)\) => {", r"const btn_hint = $('#btn-request-hint');\n        if (btn_hint) btn_hint.onclick = (\1) => {", html)

# Export window functions and wrap DOMContentLoaded
window_exports = """
    // Expose globals for inline HTML event handlers
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
    
    // Ensure escape key closes modals securely
    window.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            closeModal();
            closeAuthModal();
        }
    });

    document.addEventListener('DOMContentLoaded', () => {
        try {
            checkAuthSession();
            fetchChallenges();
            // Optional components:
            if (typeof fetchTeamInfo === 'function') fetchTeamInfo();
            if (typeof fetchLeaderboard === 'function') fetchLeaderboard();
        } catch (err) {
            console.error('Fatal initialization error caught safely:', err);
        }
    });
"""

# Replace the end
html = re.sub(r'    window\.addEventListener\(\'DOMContentLoaded\', \(\) => \{.*?\}\);\n', window_exports, html, flags=re.DOTALL)

with open('public/index.html', 'w') as f:
    f.write(html)
