import re

with open('src/WebServer.java', 'r') as f:
    code = f.read()

code = re.sub(r'sampleIn \+ "\\n"(\n)?"\);', r'sampleIn + "\\n");', code)
code = re.sub(r'sampleOut \+ "\\n"(\n)?"\);', r'sampleOut + "\\n");', code)

with open('src/WebServer.java', 'w') as f:
    f.write(code)
