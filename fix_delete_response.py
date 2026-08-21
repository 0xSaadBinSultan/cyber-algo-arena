import re

with open('src/WebServer.java', 'r') as f:
    html = f.read()

html = html.replace('ctx.json(Map.of("status", "SUCCESS", "message", "Challenge deleted successfully", "id", id));', 'ctx.json(Map.of("status", "SUCCESS", "message", "Challenge deleted"));')

with open('src/WebServer.java', 'w') as f:
    f.write(html)
