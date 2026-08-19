// Production server: serves static frontend + proxies API to gateway
import { createServer } from 'http';
import { readFile, existsSync } from 'fs';
import { join, extname } from 'path';

const PORT = 3000;
const DIST = './dist';
const API_TARGET = 'http://localhost:8080';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

function serveStatic(res, path) {
  const file = join(DIST, path === '/' ? 'index.html' : path);
  if (!existsSync(file)) {
    // SPA fallback
    readFile(join(DIST, 'index.html'), (err, data) => {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(data);
    });
    return;
  }
  const ext = extname(file);
  readFile(file, (err, data) => {
    if (err) { res.writeHead(500); res.end('Error'); return; }
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
    res.end(data);
  });
}

createServer((req, res) => {
  // API proxy
  if (req.url.startsWith('/api/')) {
    fetch(API_TARGET + req.url, {
      method: req.method,
      headers: { 'Content-Type': 'application/json' },
      ...(req.method !== 'GET' && req.method !== 'HEAD' ? { body: req } : {}),
    }).then(r => r.text()).then(body => {
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*' });
      res.end(body);
    }).catch(() => {
      res.writeHead(502);
      res.end('API unavailable');
    });
    return;
  }
  serveStatic(res, req.url.split('?')[0]);
}).listen(PORT, () => console.log(`Server: http://localhost:${PORT}`));
