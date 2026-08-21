import re

with open('public/admin.html', 'r') as f:
    html = f.read()

html = re.sub(
    r'confirm\(`⚠️ Are you sure you want to delete \'\$\{title\}\' \(\$\{challengeId\}\)\?\\n\\nThis will remove all associated submissions and attachment files permanently\.`\)',
    r'confirm(`Delete challenge \'${title}\'? This action is irreversible.`)',
    html
)

with open('public/admin.html', 'w') as f:
    f.write(html)
