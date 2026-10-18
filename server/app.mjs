import http from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { randomBytes, randomUUID, createHash, timingSafeEqual } from 'node:crypto';
import { readFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const webRoot = fileURLToPath(new URL('../web/', import.meta.url));
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const hash = value => createHash('sha256').update(value).digest('hex');
const secret = () => randomBytes(32).toString('base64url');
const equal = (a, b) => timingSafeEqual(Buffer.from(hash(a)), Buffer.from(hash(b)));
const fail = (status, message) => Object.assign(new Error(message), { status });
const assets = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/app.js', ['app.js', 'text/javascript; charset=utf-8']],
    ['/keyboard.js', ['keyboard.js', 'text/javascript; charset=utf-8']],
  ['/crypto.js', ['crypto.js', 'text/javascript; charset=utf-8']],
  ['/model.js', ['model.js', 'text/javascript; charset=utf-8']],
  ['/style.css', ['style.css', 'text/css; charset=utf-8']],
  ['/icon.svg', ['icon.svg', 'image/svg+xml']],
]);

async function jsonBody(req) {
  if (!req.headers['content-type']?.startsWith('application/json')) throw fail(415, 'JSON required');
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 96_000) throw fail(413, 'Request too large');
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw fail(400, 'Invalid JSON'); }
}

function base64(value, min, max) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(value)) return false;
  const bytes = Buffer.from(value, 'base64');
  return bytes.length >= min && bytes.length <= max && bytes.toString('base64') === value;
}

export function createApp({ dataDir, adminToken, publicOrigin, now = () => Date.now() }) {
  if (!adminToken || adminToken.length < 32) throw new Error('A strong operator token is required');
  if (publicOrigin && new URL(publicOrigin).protocol !== 'https:') throw new Error('Public origin must use HTTPS');
  mkdirSync(dataDir, { recursive: true });
  const db = new DatabaseSync(path.join(dataDir, 'cleartrail.sqlite'));
  db.exec(`
    PRAGMA journal_mode=WAL;
    PRAGMA foreign_keys=ON;
    CREATE TABLE IF NOT EXISTS devices (
      id TEXT PRIMARY KEY, upload_hash TEXT NOT NULL, read_hash TEXT NOT NULL, created INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS records (
      sequence INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
      id TEXT NOT NULL, nonce TEXT NOT NULL, ciphertext TEXT NOT NULL, received INTEGER NOT NULL,
      UNIQUE(device_id,id), UNIQUE(device_id,nonce)
    );
    CREATE INDEX IF NOT EXISTS records_device_sequence ON records(device_id,sequence);
    CREATE INDEX IF NOT EXISTS records_received ON records(received);
  `);
  const prune = () => db.prepare('DELETE FROM records WHERE received < ?').run(now() - 30 * 86400_000);
  prune();
  const cleanup = setInterval(prune, 3600_000);
  cleanup.unref();
  // Bound both request bursts and the in-memory rate-limit table.
  const rates = new Map();
  const server = http.createServer(async (req, res) => {
    res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
    if (publicOrigin) res.setHeader('Strict-Transport-Security', 'max-age=31536000');
    const send = (status, value) => {
      res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(value));
    };
    try {
      const host = req.headers.host || '';
      const allowedHost = publicOrigin ? new URL(publicOrigin).host : null;
      if (allowedHost ? host !== allowedHost : !/^(localhost|127\.0\.0\.1|10\.0\.2\.2)(:\d+)?$/.test(host)) {
        throw fail(403, 'Unexpected host');
      }
      const origin = publicOrigin || `http://${host}`;
      if (req.headers.origin && req.headers.origin !== origin) throw fail(403, 'Cross-origin request denied');
      const url = new URL(req.url, origin);
      const pathname = url.pathname;
      if (req.method === 'GET' && assets.has(pathname)) {
        const [file, mime] = assets.get(pathname);
        res.writeHead(200, { 'Content-Type': mime });
        return res.end(readFileSync(path.join(webRoot, file)));
      }
      if (req.method === 'GET' && pathname === '/api/health') return send(200, { status: 'ok', protocolVersion: 1 });
      if (!pathname.startsWith('/api/')) throw fail(404, 'Not found');
      const ip = req.socket.remoteAddress || 'unknown';
      const minute = Math.floor(now() / 60_000);
      let rate = rates.get(ip);
      if (!rate || rate.minute !== minute) { rate = { minute, count: 0 }; rates.set(ip, rate); }
      if (rates.size > 1024) rates.delete(rates.keys().next().value);
      if (++rate.count > 180) throw fail(429, 'Too many requests; retry in one minute');
      const authorization = req.headers.authorization || '';
      const token = authorization.startsWith('Bearer ') ? authorization.slice(7) : '';
      if (!token || token.length > 256) throw fail(401, 'Authentication required');
      if (req.method === 'POST' && pathname === '/api/devices') {
        if (!equal(token, adminToken)) throw fail(401, 'Invalid operator token');
        if (db.prepare('SELECT COUNT(*) AS n FROM devices').get().n >= 10) throw fail(409, 'Device limit reached');
        const id = randomUUID(), uploadToken = secret(), readToken = secret();
        db.prepare('INSERT INTO devices VALUES (?,?,?,?)').run(id, hash(uploadToken), hash(readToken), now());
        return send(201, { deviceId: id, uploadToken, readToken });
      }
      const match = /^\/api\/devices\/([0-9a-f-]+)(?:\/records(?:\/([0-9a-f-]+))?)?$/.exec(pathname);
      if (!match || !uuid.test(match[1])) throw fail(404, 'Not found');
      const deviceId = match[1], recordId = match[2];
      const device = db.prepare('SELECT * FROM devices WHERE id=?').get(deviceId);
      const writing = req.method === 'PUT' && recordId;
      const expected = device && (writing ? device.upload_hash : device.read_hash);
      if (!expected || !timingSafeEqual(Buffer.from(hash(token)), Buffer.from(expected))) throw fail(401, 'Invalid device credentials');
      if (writing) {
        if (!uuid.test(recordId)) throw fail(400, 'Invalid record ID');
        const body = await jsonBody(req);
        if (!body || typeof body !== 'object' || Object.keys(body).sort().join(',') !== 'ciphertext,nonce,version' || body.version !== 1 || !base64(body.nonce, 12, 12) || !base64(body.ciphertext, 16, 65_536)) {
          throw fail(400, 'Invalid encrypted envelope');
        }
        const previous = db.prepare('SELECT * FROM records WHERE device_id=? AND id=?').get(deviceId, recordId);
        if (previous) {
          if (previous.nonce !== body.nonce || previous.ciphertext !== body.ciphertext) throw fail(409, 'Record ID conflict');
          return send(200, { stored: true });
        }
        prune();
        if (db.prepare('SELECT COUNT(*) AS n FROM records WHERE device_id=?').get(deviceId).n >= 5000) throw fail(507, 'Record limit reached; delete history before syncing');
        if (db.prepare('SELECT 1 FROM records WHERE device_id=? AND nonce=?').get(deviceId, body.nonce)) throw fail(409, 'Nonce reuse rejected');
        db.prepare('INSERT INTO records(device_id,id,nonce,ciphertext,received) VALUES(?,?,?,?,?)').run(deviceId, recordId, body.nonce, body.ciphertext, now());
        return send(201, { stored: true });
      }
      if (pathname.endsWith('/records') && req.method === 'GET') {
        const after = Number(url.searchParams.get('after') || 0);
        if (!Number.isSafeInteger(after) || after < 0) throw fail(400, 'Invalid cursor');
        prune();
        const records = db.prepare('SELECT sequence,id,nonce,ciphertext,received FROM records WHERE device_id=? AND sequence>? ORDER BY sequence LIMIT 200').all(deviceId, after);
        return send(200, { records: records.map(row => ({ ...row, version: 1 })), nextCursor: records.at(-1)?.sequence ?? after });
      }
      if (pathname.endsWith('/records') && req.method === 'DELETE') {
        db.prepare('DELETE FROM records WHERE device_id=?').run(deviceId);
        return send(200, { deleted: true });
      }
      if (pathname === `/api/devices/${deviceId}` && req.method === 'DELETE') {
        db.prepare('DELETE FROM devices WHERE id=?').run(deviceId);
        return send(200, { revoked: true });
      }
      throw fail(405, 'Method not allowed');
    } catch (error) {
      // Never log request bodies, authorization headers, or plaintext records.
      send(error.status || 500, { error: error.status ? error.message : 'Storage operation failed' });
    }
  });
  server.requestTimeout = 15_000;
  server.headersTimeout = 10_000;
  return { server, close: () => new Promise(resolve => server.close(() => { clearInterval(cleanup); db.close(); resolve(); })) };
}
