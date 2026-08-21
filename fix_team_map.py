import re

with open('public/index.html', 'r') as f:
    html = f.read()

html = html.replace('${escapeHtml(t.solveCount || 0)}', '${escapeHtml(t.solves || 0)}')
html = html.replace('${escapeHtml(t.totalScore)}', '${escapeHtml(t.score || 0)}')

with open('public/index.html', 'w') as f:
    f.write(html)
