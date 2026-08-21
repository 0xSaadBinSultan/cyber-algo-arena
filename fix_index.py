import re

with open('public/index.html', 'r') as f:
    html = f.read()

# 1. Remove view-admin
html = re.sub(r'<!-- 6\. ADMIN CONTROL PANEL.*?</section>', '', html, flags=re.DOTALL)
html = re.sub(r'<section id="view-admin".*?</section>', '', html, flags=re.DOTALL)

# 2. Remove modal-admin-auth
html = re.sub(r'<!-- ISOLATED ADMIN LOGIN GATEWAY.*?</div>\s*</div>\s*</div>', '', html, flags=re.DOTALL)
html = re.sub(r'<div id="modal-admin-auth".*?</form>\s*</div>\s*</div>', '', html, flags=re.DOTALL)

# 3. Remove nav-admin elements from the HTML
html = re.sub(r'<button id="nav-admin".*?</button>', '', html, flags=re.DOTALL)
html = re.sub(r'<button id="nav-admin-mobile".*?</button>', '', html, flags=re.DOTALL)

# 4. Modify updateAuthUI()
update_auth_ui_replacement = """    function updateAuthUI() {
      if (currentUser) {
        $('#auth-unauthed').classList.add('hidden');
        $('#auth-authed').classList.remove('hidden');
        $('#user-display').textContent = currentUser.username;
        $('#user-avatar').textContent = currentUser.username.charAt(0).toUpperCase();
        $('#user-role-badge').textContent = currentUser.role || 'PLAYER';

        if (currentUser.teamId) {
          $('#user-team-badge').classList.remove('hidden');
          $('#user-team-badge').textContent = currentUser.teamId;
        } else {
          $('#user-team-badge').classList.add('hidden');
        }

        $('#nav-profile').classList.remove('hidden');
        $('#nav-profile-mobile').classList.remove('hidden');
      } else {
        $('#auth-unauthed').classList.remove('hidden');
        $('#auth-authed').classList.add('hidden');
        $('#nav-profile').classList.add('hidden');
        $('#nav-profile-mobile').classList.add('hidden');
      }
      
      const isPlatformAdmin = currentUser && currentUser.role === 'ADMIN';
      const platformAdminEl = document.getElementById('platform-admin-nav');
      if (platformAdminEl) {
          if (isPlatformAdmin) {
              platformAdminEl.classList.remove('hidden');
          } else {
              platformAdminEl.classList.add('hidden');
          }
      }
    }"""
html = re.sub(r'function updateAuthUI\(\) \{.*?\n    \}\n', update_auth_ui_replacement + '\n', html, flags=re.DOTALL)

# 5. Remove admin from checkUrlHash
html = re.sub(r'// URL Hash Navigation for Admin Portal.*?\}', '', html, flags=re.DOTALL)

# 6. Remove admin blocks in JS
html = re.sub(r'\} else if \(viewId === \'admin\'\) \{.*?\}', '}', html, flags=re.DOTALL)
html = re.sub(r'// --- ADMIN CONTROLLER.*?// --- DEDICATED ISOLATED ADMIN AUTH GATEWAY ---', '// --- DEDICATED ISOLATED ADMIN AUTH GATEWAY ---', html, flags=re.DOTALL)
html = re.sub(r'// --- DEDICATED ISOLATED ADMIN AUTH GATEWAY ---.*?// --- INITIALIZATION ---', '// --- INITIALIZATION ---', html, flags=re.DOTALL)

with open('public/index.html', 'w') as f:
    f.write(html)
