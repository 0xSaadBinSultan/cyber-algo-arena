import re

with open('public/admin.html', 'r') as f:
    html = f.read()

# Replace the two massive forms with a single button
replacement_header = """      <!-- Add Challenges Header / Button -->
      <div class="flex justify-between items-center bg-ctf-card border border-ctf-border rounded-xl p-5 mb-6">
        <h4 class="font-bold text-white text-sm flex items-center gap-2">
          <i class="fa-solid fa-cube text-ctf-blue"></i> Challenge Management
        </h4>
        <button onclick="document.getElementById('modal-admin-create-chal').classList.remove('hidden')" class="px-4 py-2 bg-ctf-green hover:bg-ctf-greenHover text-white font-semibold rounded text-sm transition-colors flex items-center gap-2">
          <i class="fa-solid fa-plus"></i> Create New Challenge
        </button>
      </div>"""

html = re.sub(r'      <!-- Add Challenges Forms -->.*?</div>\s*</div>\n\n      <!-- Submission Audit Logs -->', replacement_header + '\n\n      <!-- Submission Audit Logs -->', html, flags=re.DOTALL)

# Add the modal right before </body>
modal_html = """
    <!-- MODAL: ADMIN CREATE CHALLENGE -->
    <div id="modal-admin-create-chal" class="fixed inset-0 z-50 flex items-center justify-center hidden">
      <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" onclick="this.parentElement.classList.add('hidden')"></div>
      <div class="relative bg-ctf-card border border-ctf-border rounded-xl shadow-2xl w-full max-w-2xl overflow-hidden animate-fade-in-up my-4 flex flex-col max-h-[90vh]">
        <div class="flex items-center justify-between p-4 border-b border-ctf-border bg-[#161b22]">
          <h3 class="text-lg font-bold text-white"><i class="fa-solid fa-hammer text-ctf-blue mr-2"></i>Create New Challenge</h3>
          <button onclick="document.getElementById('modal-admin-create-chal').classList.add('hidden')" class="text-ctf-muted hover:text-white transition-colors">
            <i class="fa-solid fa-xmark text-xl"></i>
          </button>
        </div>
        
        <div class="p-5 overflow-y-auto">
          <form id="form-admin-create-chal" class="space-y-4">
            <div>
              <label class="text-[11px] font-medium text-ctf-muted block mb-1">Challenge Type</label>
              <select id="create-chal-type" onchange="toggleChalType(this.value)" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white font-bold">
                <option value="CTF">CTF Challenge (Flag-based)</option>
                <option value="CP">CP Problem (Algorithm/Testcases)</option>
              </select>
            </div>
            
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="text-[11px] font-medium text-ctf-muted block mb-1">ID (e.g. CTF-01)</label>
                <input type="text" id="create-chal-id" required class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white font-mono">
              </div>
              <div>
                <label class="text-[11px] font-medium text-ctf-muted block mb-1">Base Points</label>
                <input type="number" id="create-chal-pts" required value="100" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white font-mono">
              </div>
            </div>
            
            <div>
              <label class="text-[11px] font-medium text-ctf-muted block mb-1">Title</label>
              <input type="text" id="create-chal-title" required class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white">
            </div>
            
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="text-[11px] font-medium text-ctf-muted block mb-1">Difficulty</label>
                <select id="create-chal-diff" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white">
                  <option value="EASY">EASY</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="HARD">HARD</option>
                  <option value="EXPERT">EXPERT</option>
                </select>
              </div>
              <div>
                <label class="text-[11px] font-medium text-ctf-muted block mb-1">Category</label>
                <select id="create-chal-cat" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white">
                  <option value="CRYPTO">Cryptography</option>
                  <option value="WEB">Web Exploitation</option>
                  <option value="PWN">Binary Exploitation</option>
                  <option value="FORENSICS">Forensics</option>
                  <option value="SYSTEMS_SECURITY">Systems Security</option>
                  <option value="GENERAL_SKILLS">General Skills</option>
                  <option value="OSINT">OSINT</option>
                  <option value="MISC">Misc</option>
                  <option value="DYNAMIC_PROGRAMMING">Dynamic Programming (CP)</option>
                  <option value="GRAPHS">Graphs (CP)</option>
                  <option value="MATH">Math (CP)</option>
                  <option value="STRINGS">Strings (CP)</option>
                </select>
              </div>
            </div>
            
            <div>
              <label class="text-[11px] font-medium text-ctf-muted block mb-1">Rich Markdown Description</label>
              <textarea id="create-chal-desc" rows="4" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white font-mono" placeholder="## Scenario..."></textarea>
            </div>
            
            <!-- CTF Specific -->
            <div id="ctf-specific-fields" class="space-y-4 border-t border-ctf-border pt-4">
              <div>
                <label class="text-[11px] font-medium text-ctf-muted block mb-1">Raw Flag (Hashed on Server)</label>
                <input type="text" id="create-chal-flag" placeholder="flag{...}" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white font-mono">
              </div>
              <div>
                <label class="text-[11px] font-medium text-ctf-muted block mb-1">Attachment File Name (Optional)</label>
                <input type="text" id="create-chal-attach" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white font-mono">
              </div>
            </div>
            
            <!-- CP Specific -->
            <div id="cp-specific-fields" class="space-y-4 border-t border-ctf-border pt-4 hidden">
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <label class="text-[11px] font-medium text-ctf-muted block mb-1">Time Limit (ms)</label>
                  <input type="number" id="create-chal-time" value="2000" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white font-mono">
                </div>
                <div>
                  <label class="text-[11px] font-medium text-ctf-muted block mb-1">Memory Limit (MB)</label>
                  <input type="number" id="create-chal-mem" value="256" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white font-mono">
                </div>
              </div>
              <div>
                <label class="text-[11px] font-medium text-ctf-muted block mb-1">Sample Input (Testcase 1)</label>
                <textarea id="create-chal-samplein" rows="2" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white font-mono"></textarea>
              </div>
              <div>
                <label class="text-[11px] font-medium text-ctf-muted block mb-1">Sample Expected Output (Testcase 1)</label>
                <textarea id="create-chal-sampleout" rows="2" class="w-full bg-[#0d1117] border border-ctf-border rounded p-2 text-sm text-white font-mono"></textarea>
              </div>
            </div>
            
            <button type="submit" class="w-full py-2.5 bg-ctf-blue hover:bg-blue-600 text-white font-semibold rounded-md text-sm transition-colors mt-4">
              Submit & Deploy Challenge
            </button>
          </form>
        </div>
      </div>
    </div>
"""

html = html.replace('</body>', modal_html + '\n</body>')

# Remove old JS for the forms and replace with the unified one
unified_js = """
    function toggleChalType(type) {
      if (type === 'CTF') {
        document.getElementById('ctf-specific-fields').classList.remove('hidden');
        document.getElementById('cp-specific-fields').classList.add('hidden');
      } else {
        document.getElementById('ctf-specific-fields').classList.add('hidden');
        document.getElementById('cp-specific-fields').classList.remove('hidden');
      }
    }

    $('#form-admin-create-chal').onsubmit = async (e) => {
      e.preventDefault();
      const type = $('#create-chal-type').value;
      const payload = {
        type: type,
        id: $('#create-chal-id').value,
        title: $('#create-chal-title').value,
        description: $('#create-chal-desc').value,
        basePoints: parseInt($('#create-chal-pts').value),
        difficulty: $('#create-chal-diff').value,
        category: $('#create-chal-cat').value
      };
      
      if (type === 'CTF') {
        payload.rawFlag = $('#create-chal-flag').value;
        payload.attachmentFileName = $('#create-chal-attach').value;
      } else {
        payload.timeLimitMs = parseInt($('#create-chal-time').value || 2000);
        payload.memoryLimitMb = parseInt($('#create-chal-mem').value || 256);
        payload.sampleInput = $('#create-chal-samplein').value;
        payload.sampleOutput = $('#create-chal-sampleout').value;
      }
      
      try {
        const res = await fetch('/api/admin/challenges', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (res.ok) {
          showToast(`Challenge ${data.id || payload.title} created successfully!`, 'success');
          document.getElementById('modal-admin-create-chal').classList.add('hidden');
          $('#form-admin-create-chal').reset();
          toggleChalType('CTF');
          fetchAdminData();
        } else {
          showToast(data.error || 'Challenge creation failed', 'error');
        }
      } catch (err) {
        showToast('Challenge creation failed', 'error');
      }
    };
"""

html = re.sub(r'    \$\(\'#form-admin-add-ctf\'\)\.onsubmit = async \(e\) => \{.*?\n    \};\n\n    \$\(\'#form-admin-add-cp\'\)\.onsubmit = async \(e\) => \{.*?\n    \};\n', unified_js, html, flags=re.DOTALL)

with open('public/admin.html', 'w') as f:
    f.write(html)
