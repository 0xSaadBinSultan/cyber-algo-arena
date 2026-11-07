#!/usr/bin/env python3
"""Authenticated loopback gateway. Exposes execution only; never package management."""
import hmac
import http.server
import json
import os
import threading
import urllib.error
import urllib.request

TOKEN = os.environ.get('ARENA_JUDGE_TOKEN', '')
if len(TOKEN) < 32:
    raise SystemExit('Set a random ARENA_JUDGE_TOKEN of at least 32 characters')
SLOTS = threading.BoundedSemaphore(2)
MAX_BODY = 2 * 1024 * 1024

class Gateway(http.server.BaseHTTPRequestHandler):
    def reply(self, status, body):
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.forward('GET')

    def do_POST(self):
        self.forward('POST')

    def forward(self, method):
        allowed = (method, self.path) in [('GET','/api/v2/runtimes'),('POST','/api/v2/execute')]
        if not allowed:
            self.reply(404, b'{"message":"Not found"}')
            return
        auth = self.headers.get('Authorization', '')
        if not hmac.compare_digest(auth, 'Bearer ' + TOKEN):
            self.reply(401, b'{"message":"Authentication required"}')
            return
        try:
            length = int(self.headers.get('Content-Length', '0'))
        except ValueError:
            self.reply(400, b'{"message":"Invalid content length"}')
            return
        if length < 0 or length > MAX_BODY:
            self.reply(413, b'{"message":"Request too large"}')
            return
        if not SLOTS.acquire(blocking=False):
            self.reply(429, b'{"message":"Demo judge is busy"}')
            return
        try:
            self.connection.settimeout(10)
            body = self.rfile.read(length) if method == 'POST' else None
            request = urllib.request.Request('http://127.0.0.1:2000' + self.path,
                data=body, method=method, headers={'Content-Type':'application/json'})
            try:
                with urllib.request.urlopen(request, timeout=75) as response:
                    self.reply(response.status, response.read(MAX_BODY))
            except urllib.error.HTTPError as error:
                self.reply(error.code, error.read(MAX_BODY))
            except Exception:
                self.reply(503, b'{"message":"Local judge is unavailable"}')
        finally:
            SLOTS.release()

    def log_message(self, format, *args):
        # Default logs contain method/path/status only. Never log credentials or request bodies.
        super().log_message(format, *args)

if __name__ == '__main__':
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 2001), Gateway)
    print('Authenticated judge gateway ready on 127.0.0.1:2001', flush=True)
    server.serve_forever()
