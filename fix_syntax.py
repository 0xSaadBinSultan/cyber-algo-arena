import re

with open('public/index.html', 'r') as f:
    html = f.read()

html = re.sub(r'    // --- DEDICATED ISOLATED ADMIN AUTH GATEWAY ---.*?window\.addEventListener\(\'hashchange\', checkUrlHash\);', '', html, flags=re.DOTALL)

with open('public/index.html', 'w') as f:
    f.write(html)
