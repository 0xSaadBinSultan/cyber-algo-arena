#!/usr/bin/env python3
"""Install the demo's three official Piston runtimes; safe to rerun."""
import json
import os
import urllib.request

base = os.environ.get('PISTON_SETUP_URL', 'http://127.0.0.1:2000').rstrip('/')
with urllib.request.urlopen(base + '/api/v2/runtimes', timeout=15) as response:
    installed = json.load(response)
for language, version, alias in [('python','3.12.0','python'),('gcc','10.2.0','c++'),('java','15.0.2','java')]:
    if any(r.get('language') == alias and r.get('version') == version for r in installed):
        print(f'{alias} {version} is installed', flush=True)
        continue
    print(f'Installing {language} {version}…', flush=True)
    request = urllib.request.Request(base + '/api/v2/packages',
        data=json.dumps({'language': language, 'version': version}).encode(),
        headers={'Content-Type':'application/json'}, method='POST')
    with urllib.request.urlopen(request, timeout=900) as response:
        print(response.read().decode(), flush=True)
