#!/usr/bin/env python3
"""Resume the free, authenticated Render-to-local demo connection."""
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
import urllib.request

root = Path(__file__).resolve().parent.parent
state = root.parent
secret_file = state / 'judge-gateway-private.json'
if not secret_file.exists():
    secret_file.write_text(json.dumps({'token': secrets.token_urlsafe(40)}))
    secret_file.chmod(0o600)
token = json.loads(secret_file.read_text())['token']

def probe(url, authenticated=False):
    try:
        request=urllib.request.Request(url + '/api/v2/runtimes', headers={'Authorization':'Bearer '+token} if authenticated else {})
        with urllib.request.urlopen(request,timeout=6) as response:
            return response.status == 200
    except Exception:
        return False

subprocess.run(['docker','build','-t','cyber-algo-piston-demo:local','-f','infra/piston-demo/Dockerfile','.'],cwd=root,check=True)
existing=subprocess.run(['docker','container','inspect','arena-piston-demo'],capture_output=True).returncode==0
if existing:
    subprocess.run(['docker','start','arena-piston-demo'],check=True)
else:
    data=state/'piston-data';data.mkdir(exist_ok=True)
    subprocess.run(['docker','run','-d','--name','arena-piston-demo','--privileged','-p','127.0.0.1:2000:2000','-v',str(data)+':/piston','cyber-algo-piston-demo:local'],check=True)
for _ in range(30):
    if probe('http://127.0.0.1:2000'):break
    time.sleep(1)
else:
    raise SystemExit('The local Piston service did not start. Check docker logs arena-piston-demo.')
subprocess.run([sys.executable,'scripts/install-judge-runtimes.py'],cwd=root,check=True)

if not probe('http://127.0.0.1:2001',True):
    env=os.environ.copy();env['ARENA_JUDGE_TOKEN']=token
    with (state/'judge-gateway.log').open('ab') as log:
        subprocess.Popen([sys.executable,str(root/'scripts/judge-demo-gateway.py')],env=env,stdout=log,stderr=log,start_new_session=True)
    for _ in range(10):
        if probe('http://127.0.0.1:2001',True):break
        time.sleep(1)
    else:raise SystemExit('The authenticated gateway did not start. Check judge-gateway.log.')

logfile=state/'judge-tunnel.log'
urls=re.findall(r'https://[a-z0-9-]+\.trycloudflare\.com',logfile.read_text() if logfile.exists() else '')
url=urls[-1] if urls else ''
if not url or not probe(url,True):
    binary=state/'cloudflared'
    if not binary.exists():
        with urllib.request.urlopen('https://api.github.com/repos/cloudflare/cloudflared/releases/latest',timeout=30) as response:
            release=json.load(response)
        asset=next(a for a in release['assets'] if a['name']=='cloudflared-linux-amd64')
        with urllib.request.urlopen(asset['browser_download_url'],timeout=90) as response:content=response.read()
        if asset.get('digest') and 'sha256:'+hashlib.sha256(content).hexdigest()!=asset['digest']:
            raise SystemExit('cloudflared release checksum mismatch')
        binary.write_bytes(content);binary.chmod(0o755)
    with logfile.open('wb') as log:
        subprocess.Popen([str(binary),'tunnel','--url','http://127.0.0.1:2001','--no-autoupdate'],stdout=log,stderr=log,start_new_session=True)
    for _ in range(45):
        urls=re.findall(r'https://[a-z0-9-]+\.trycloudflare\.com',logfile.read_text())
        if urls and probe(urls[-1],True):url=urls[-1];break
        time.sleep(1)
    else:raise SystemExit('Tunnel did not connect. Check judge-tunnel.log.')
settings=state/'render-judge-settings.txt'
settings.write_text('PISTON_URL='+url+'\nPISTON_API_KEY='+token+'\n')
settings.chmod(0o600)
print('Demo judge is connected through: '+url)
print('If the hostname changed, update PISTON_URL in Render and save/redeploy.')
print('The existing token and connection settings are stored privately at '+str(settings))
print('Keep this PC and Docker running. This tunnel is for the presentation, not permanent hosting.')
