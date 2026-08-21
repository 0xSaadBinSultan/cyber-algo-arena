import re

with open('public/admin.html', 'r') as f:
    html = f.read()

replacement = """      <!-- Add Challenges Header / Button -->
      <div class="flex justify-between items-center bg-ctf-card border border-ctf-border rounded-xl p-5 mb-6">
        <h4 class="font-bold text-white text-sm flex items-center gap-2">
          <i class="fa-solid fa-cube text-ctf-blue"></i> Challenge Management
        </h4>
        <div class="flex items-center gap-3">
          <button onclick="adminToggleFreeze()" class="px-4 py-2 bg-ctf-purple/20 hover:bg-ctf-purple/30 border border-ctf-purple/50 text-ctf-purple font-semibold rounded text-sm transition-colors flex items-center gap-2">
            <i class="fa-solid fa-snowflake"></i> Toggle Freeze Scoreboard
          </button>
          <button onclick="document.getElementById('modal-admin-create-chal').classList.remove('hidden')" class="px-4 py-2 bg-ctf-green hover:bg-ctf-greenHover text-white font-semibold rounded text-sm transition-colors flex items-center gap-2">
            <i class="fa-solid fa-plus"></i> Create New Challenge
          </button>
        </div>
      </div>"""

html = re.sub(r'      <!-- Add Challenges Header / Button -->.*?</div>\s*</div>', replacement, html, flags=re.DOTALL)

js_freeze = """
    async function adminToggleFreeze() {
      try {
        const res = await fetch('/api/admin/scoreboard/freeze', { method: 'POST' });
        const data = await res.json();
        if (res.ok) {
          showToast(`Scoreboard freeze toggled: ${data.scoreboardFrozen ? 'FROZEN' : 'ACTIVE'}`, 'success');
        } else {
          showToast(data.error || 'Failed to toggle freeze', 'error');
        }
      } catch (err) {
        showToast('Error toggling freeze', 'error');
      }
    }
    
    function toggleChalType(type) {"""

html = html.replace('function toggleChalType(type) {', js_freeze)

with open('public/admin.html', 'w') as f:
    f.write(html)
