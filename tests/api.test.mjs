import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, readdirSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { createApp } from '../server/app.mjs';
import { newKey, encryptRecord, decryptRecord } from '../web/crypto.js';

const operator = 'operator-test-only-'.repeat(4);
async function fixture(t, options = {}) {
  const dataDir = mkdtempSync(path.join(tmpdir(), 'cleartrail-test-'));
  let clock = Date.now();
  let app = createApp({ dataDir, adminToken: operator, now: () => clock, ...options });
  await new Promise(resolve => app.server.listen(0, '127.0.0.1', resolve));
  let origin = `http://127.0.0.1:${app.server.address().port}`;
  t.after(async () => { await app.close(); rmSync(dataDir, { recursive: true, force: true }); });
  const request = async (route, token, method = 'GET', body, headers = {}) => {
    const response = await fetch(origin + route, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), 'Content-Type': 'application/json', ...headers }, body: body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body) });
    const text = await response.text();
    return { status: response.status, body: response.headers.get('content-type')?.includes('json') ? JSON.parse(text) : text, headers: response.headers };
  };
  const device = (await request('/api/devices', operator, 'POST')).body;
  return { app, dataDir, request, device, clock: n => { clock += n; }, restart: async () => {
    await app.close();
    app = createApp({ dataDir, adminToken: operator, now: () => clock, ...options });
    await new Promise(resolve => app.server.listen(0, '127.0.0.1', resolve));
    origin = `http://127.0.0.1:${app.server.address().port}`;
  } };
}
async function encrypted(device, key = newKey()) {
  const id = crypto.randomUUID();
  const payload = { schemaVersion: 1, sessionId: crypto.randomUUID(), from: 100, to: 200, battery: 88, apps: [{ packageName: 'com.whatsapp', intervals: [{ start: 110, end: 150, estimated: true }] }] };
  return { id, key, payload, envelope: await encryptRecord(key, device.deviceId, id, payload) };
}
const route = device => `/api/devices/${device.deviceId}/records`;

test('authorized encrypted roundtrip and ciphertext-only storage', async t => {
  const { request, device, dataDir } = await fixture(t), record = await encrypted(device);
  assert.equal((await request(`${route(device)}/${record.id}`, device.uploadToken, 'PUT', record.envelope)).status, 201);
  const response = await request(route(device), device.readToken);
  assert.equal(response.status, 200);
  assert.deepEqual(await decryptRecord(record.key, device.deviceId, response.body.records[0]), record.payload);
  for (const file of readdirSync(dataDir)) {
    const bytes = readFileSync(path.join(dataDir, file));
    for (const marker of ['com.whatsapp', device.uploadToken, device.readToken, record.key]) assert.equal(bytes.includes(Buffer.from(marker)), false, `${marker.slice(0, 4)} must not be stored in plaintext`);
  }
});
test('authentication, upload/read role separation and device isolation', async t => {
  const { request, device } = await fixture(t), record = await encrypted(device);
  assert.equal((await request('/api/devices', '', 'POST')).status, 401);
  assert.equal((await request(route(device), device.uploadToken)).status, 401);
  assert.equal((await request(`${route(device)}/${record.id}`, device.readToken, 'PUT', record.envelope)).status, 401);
  assert.equal((await request(route(device), device.uploadToken, 'DELETE')).status, 401);
  const second = (await request('/api/devices', operator, 'POST')).body;
  assert.equal((await request(route(second), device.readToken)).status, 401);
  assert.equal((await request(route(device), 'wrong')).status, 401);
});
test('idempotency, conflict handling and nonce uniqueness', async t => {
  const { request, device } = await fixture(t), a = await encrypted(device), b = await encrypted(device);
  const put = (id, data) => request(`${route(device)}/${id}`, device.uploadToken, 'PUT', data);
  assert.equal((await put(a.id, a.envelope)).status, 201);
  assert.equal((await put(a.id, a.envelope)).status, 200);
  assert.equal((await put(a.id, b.envelope)).status, 409);
  assert.equal((await put(b.id, a.envelope)).status, 409);
  assert.equal((await request(route(device), device.readToken)).body.records.length, 1);
});
test('rejects malformed, plaintext, oversized and unknown envelope fields', async t => {
  const { request, device } = await fixture(t), record = await encrypted(device);
  const put = body => request(`${route(device)}/${record.id}`, device.uploadToken, 'PUT', body);
  assert.equal((await put({ ...record.envelope, text: 'secret' })).status, 400);
  assert.equal((await put({ ...record.envelope, nonce: 'bad' })).status, 400);
  assert.equal((await put(null)).status, 400);
  assert.equal((await put('{')).status, 400);
  assert.equal((await put({ ciphertext: 'x'.repeat(100_000) })).status, 413);
});
test('purge and revoke remove history; revoked credentials cannot upload', async t => {
  const { request, device } = await fixture(t), a = await encrypted(device), b = await encrypted(device);
  await request(`${route(device)}/${a.id}`, device.uploadToken, 'PUT', a.envelope);
  assert.equal((await request(route(device), device.readToken, 'DELETE')).status, 200);
  assert.equal((await request(route(device), device.readToken)).body.records.length, 0);
  await request(`${route(device)}/${b.id}`, device.uploadToken, 'PUT', b.envelope);
  assert.equal((await request(`/api/devices/${device.deviceId}`, device.readToken, 'DELETE')).status, 200);
  assert.equal((await request(route(device), device.readToken)).status, 401);
  assert.equal((await request(`${route(device)}/${a.id}`, device.uploadToken, 'PUT', a.envelope)).status, 401);
});
test('30-day retention and cursor validation', async t => {
  const { request, device, clock } = await fixture(t), record = await encrypted(device);
  await request(`${route(device)}/${record.id}`, device.uploadToken, 'PUT', record.envelope);
  assert.equal((await request(route(device) + '?after=-1', device.readToken)).status, 400);
  clock(31 * 86400_000);
  assert.equal((await request(route(device), device.readToken)).body.records.length, 0);
});
test('strict origin, host, static route and security headers', async t => {
  const { request, app } = await fixture(t);
  const page = await request('/');
  assert.equal(page.status, 200);
  assert.match(page.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  assert.equal(page.headers.get('cache-control'), 'no-store');
  assert.equal((await request('/api/devices', operator, 'POST', undefined, { Origin: 'https://attacker.example' })).status, 403);
  const hostStatus = await new Promise((resolve, reject) => {
    http.get({ hostname: '127.0.0.1', port: app.server.address().port, path: '/api/health', headers: { Host: 'attacker.example' } }, response => { response.resume(); resolve(response.statusCode); }).on('error', reject);
  });
  assert.equal(hostStatus, 403);
  assert.equal((await request('/.local/operator-token.txt')).status, 404);
});
test('server fails closed with weak operator configuration or plaintext public origin', () => {
  assert.throws(() => createApp({ dataDir: '.', adminToken: 'short' }));
  assert.throws(() => createApp({ dataDir: '.', adminToken: operator, publicOrigin: 'http://example.com' }));
});

test('pagination returns every record once and survives server restart', async t => {
  const { request, device, clock, restart } = await fixture(t);
  const key = newKey();
  for (let i = 0; i < 205; i++) {
    if (i % 70 === 0) clock(60_000);
    const record = await encrypted(device, key);
    assert.equal((await request(`${route(device)}/${record.id}`, device.uploadToken, 'PUT', record.envelope)).status, 201);
  }
  await restart();
  const first = (await request(route(device), device.readToken)).body;
  assert.equal(first.records.length, 200);
  const second = (await request(`${route(device)}?after=${first.nextCursor}`, device.readToken)).body;
  assert.equal(second.records.length, 5);
  assert.equal(new Set([...first.records, ...second.records].map(row => row.id)).size, 205);
});

test('rate limits excessive requests and bounds device registration', async t => {
  const { request, device, clock } = await fixture(t);
  for (let i = 0; i < 9; i++) assert.equal((await request('/api/devices', operator, 'POST')).status, 201);
  assert.equal((await request('/api/devices', operator, 'POST')).status, 409);
  let limited = false;
  for (let i = 0; i < 181; i++) if ((await request(route(device), 'wrong')).status === 429) { limited = true; break; }
  assert.ok(limited);
  clock(60_000);
  assert.equal((await request(route(device), device.readToken)).status, 200);
});

test('storage capacity rejects new records but preserves idempotent retry', async t => {
  const { request, device, dataDir } = await fixture(t), record = await encrypted(device);
  await request(`${route(device)}/${record.id}`, device.uploadToken, 'PUT', record.envelope);
  const seed = new DatabaseSync(path.join(dataDir, 'cleartrail.sqlite'));
  try {
    seed.exec('BEGIN');
    const insert = seed.prepare('INSERT INTO records(device_id,id,nonce,ciphertext,received) VALUES(?,?,?,?,?)');
    for (let i = 1; i < 5000; i++) {
      const nonce = Buffer.alloc(12); nonce.writeUInt32BE(i, 8);
      insert.run(device.deviceId, crypto.randomUUID(), nonce.toString('base64'), record.envelope.ciphertext, Date.now());
    }
    seed.exec('COMMIT');
  } finally { seed.close(); }
  assert.equal((await request(`${route(device)}/${record.id}`, device.uploadToken, 'PUT', record.envelope)).status, 200);
  const extra = await encrypted(device);
  assert.equal((await request(`${route(device)}/${extra.id}`, device.uploadToken, 'PUT', extra.envelope)).status, 507);
});
