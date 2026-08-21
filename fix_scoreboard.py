import re

with open('public/index.html', 'r') as f:
    html = f.read()

chart_replacement = """    function renderScoreboardChart(data) {
      const ctx = document.getElementById('scoreboardChart');
      if (!ctx) return;
      if (window.scoreboardChart instanceof Chart) {
          window.scoreboardChart.destroy();
      }

      const teams = data.standings || [];
      const top10 = teams.slice(0, 10);"""
html = re.sub(r'    function renderScoreboardChart\(teams\) \{\n      const ctx = document\.getElementById\(\'scoreboardChart\'\);\n      if \(\!ctx\) return;\n      if \(window\.scoreboardChart\) \{\n          window\.scoreboardChart\.destroy\(\);\n      \}\n\n      const top10 = teams\.slice\(0, 10\);', chart_replacement, html, flags=re.DOTALL)

with open('public/index.html', 'w') as f:
    f.write(html)
