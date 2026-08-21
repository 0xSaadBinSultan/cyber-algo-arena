import re

with open('src/WebServer.java', 'r') as f:
    code = f.read()

code = code.replace('ctx.json(response);\n    }\n\n    // ═══════════════════════════════════════════\n    // ADMIN HANDLERS', 'ctx.contentType("application/json");\n        ctx.json(response);\n    }\n\n    // ═══════════════════════════════════════════\n    // ADMIN HANDLERS')

with open('src/WebServer.java', 'w') as f:
    f.write(code)
