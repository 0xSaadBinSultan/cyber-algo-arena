import re

with open('public/index.html', 'r') as f:
    html = f.read()

# Add bindEvent at the start of the script
bind_event_script = """  <script>
    const bindEvent = (id, event, handler) => {
        const el = document.getElementById(id);
        if (el) el.addEventListener(event, handler);
    };
"""
html = html.replace('  <script>', bind_event_script)

# Replace form assignments with named functions
# 1. Login
html = re.sub(r'const form_form_auth_login = \$\(\'#form-auth-login\'\);\n    if \(form_form_auth_login\) form_form_auth_login\.onsubmit = async \(e\) => \{', r'async function login(e) {', html)
# 2. Register
html = re.sub(r'const form_form_auth_register = \$\(\'#form-auth-register\'\);\n    if \(form_form_auth_register\) form_form_auth_register\.onsubmit = async \(e\) => \{', r'async function register(e) {', html)
# 3. Submit Flag
html = re.sub(r'const form_form_submit_flag = \$\(\'#form-submit-flag\'\);\n    if \(form_form_submit_flag\) form_form_submit_flag\.onsubmit = async \(e\) => \{', r'async function submitFlag(e) {', html)
# 4. Join Team
html = re.sub(r'const form_form_join_team = \$\(\'#form-join-team\'\);\n    if \(form_form_join_team\) form_form_join_team\.onsubmit = async \(e\) => \{', r'async function joinTeam(e) {', html)
# 5. Create Team
html = re.sub(r'const form_form_create_team = \$\(\'#form-create-team\'\);\n    if \(form_form_create_team\) form_form_create_team\.onsubmit = async \(e\) => \{', r'async function createTeam(e) {', html)

# Handle runCode (placeholder if it doesn't exist)
if 'async function runCode' not in html:
    html = html.replace('    // Expose globals', "    async function runCode(e) { e.preventDefault(); console.log('Run code executed'); }\n    // Expose globals")

# Handle filterArena (alias for switchChallengeTrack or similar if it doesn't exist)
if 'function filterArena' not in html:
    html = html.replace('    // Expose globals', "    function filterArena(track) { switchChallengeTrack(track); }\n    // Expose globals")
    
# Handle copyPasskey
if 'function copyPasskey' not in html:
    html = html.replace('    // Expose globals', "    function copyPasskey() { const pk = document.getElementById('team-passkey-display'); if(pk) { navigator.clipboard.writeText(pk.textContent); showToast('Passkey copied', 'success'); } }\n    // Expose globals")
    
# switchTab alias
if 'function switchTab' not in html:
    html = html.replace('    // Expose globals', "    function switchTab(tabId) { showView(tabId); }\n    // Expose globals")

# Update window exports
new_exports = """    // Expose globals for inline HTML event handlers
    window.switchTab = switchTab;
    window.openChallengeModal = openChallengeModal;
    window.closeModal = closeModal;
    window.submitFlag = submitFlag;
    window.runCode = runCode;
    window.filterCategory = filterCategory;
    window.filterArena = filterArena;
    window.login = login;
    window.register = register;
    window.logout = logout;
    window.createTeam = createTeam;
    window.joinTeam = joinTeam;
    window.copyPasskey = copyPasskey;
    window.showView = showView;
    window.openAuthModal = openAuthModal;
    window.closeAuthModal = closeAuthModal;
"""
html = re.sub(r'    // Expose globals for inline HTML event handlers.*?window\.fetchRadarEvents = fetchRadarEvents;', new_exports, html, flags=re.DOTALL)

# Add bindEvents in DOMContentLoaded
init_block = """    document.addEventListener('DOMContentLoaded', () => {
        try {
            bindEvent('form-auth-login', 'submit', login);
            bindEvent('form-auth-register', 'submit', register);
            bindEvent('form-submit-flag', 'submit', submitFlag);
            bindEvent('form-join-team', 'submit', joinTeam);
            bindEvent('form-create-team', 'submit', createTeam);
            
            checkAuthSession();
            fetchChallenges();
            // Optional components:
            if (typeof fetchTeamInfo === 'function') fetchTeamInfo();
            if (typeof fetchLeaderboard === 'function') fetchLeaderboard();
        } catch (err) {
            console.error('Fatal initialization error caught safely:', err);
        }
    });"""
html = re.sub(r'    document\.addEventListener\(\'DOMContentLoaded\', \(\) => \{.*?\}\);', init_block, html, flags=re.DOTALL)

# Fix parseSimpleMarkdown styling
html = html.replace('<code class="font-mono bg-black/40 p-3 rounded block overflow-x-auto my-2 border border-ctf-border">', '<code class="font-mono bg-black/50 p-3 rounded block my-2 text-sm">')


with open('public/index.html', 'w') as f:
    f.write(html)
