import re

with open('public/index.html', 'r') as f:
    html = f.read()

team_hub_replacement = """          $('#team-hub-content').innerHTML = `
            <div class="grid grid-cols-2 gap-3 text-xs font-mono">
              <div class="p-2.5 bg-[#12161c] border border-ctf-border rounded"><span class="text-ctf-muted block text-[10px]">TEAM ID</span><span class="text-white">${escapeHtml(t.id)}</span></div>
              <div class="p-2.5 bg-[#12161c] border border-ctf-border rounded"><span class="text-ctf-muted block text-[10px]">CAPTAIN</span><span class="text-ctf-purple">${escapeHtml(t.captainUserId || 'Admin')}</span></div>
            </div>
            ${currentUser && currentUser.id === t.captainUserId ? '<div id="team-captain-controls" class="mt-4"><button id="team-manage-btn" class="px-3 py-1.5 bg-ctf-purple/20 text-ctf-purple border border-ctf-purple/50 rounded text-xs font-bold">Manage Team</button></div>' : ''}
          `;"""

html = re.sub(r'\$\(\'#team-hub-content\'\)\.innerHTML = `\n.*?</div>\n\s*`;', team_hub_replacement, html, flags=re.DOTALL)

with open('public/index.html', 'w') as f:
    f.write(html)
