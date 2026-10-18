import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { newKey, encryptRecord, decryptRecord } from '../web/crypto.js';

test('Web Crypto and JDK AES-GCM interoperate in both directions', async t => {
  const root = fileURLToPath(new URL('../', import.meta.url));
  const local = path.join(root, '.tools', 'jdk17');
  const javaHome = process.env.JAVA_HOME || (existsSync(local) ? path.join(local, readdirSync(local).find(name => name.startsWith('jdk-')) || '') : '');
  const java = javaHome ? path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  if (spawnSync(java, ['-version']).status !== 0) { t.skip('JDK not available'); return; }
  const key = newKey(), deviceId = crypto.randomUUID(), id = crypto.randomUUID();
  const payload = { schemaVersion: 1, sessionId: crypto.randomUUID(), from: 100, to: 200, battery: 75, apps: [] };
  const envelope = await encryptRecord(key, deviceId, id, payload);
  const result = spawnSync(java, [path.join(root, 'tests', 'CryptoInterop.java')], {
    input: [key, `cleartrail:v1:${deviceId}:${id}`, envelope.nonce, envelope.ciphertext, ''].join('\n'), encoding: 'utf8', timeout: 30_000,
  });
  assert.equal(result.status, 0, result.stderr);
  const [plaintext, nonce, ciphertext] = result.stdout.trim().split(/\r?\n/);
  assert.deepEqual(JSON.parse(Buffer.from(plaintext, 'base64').toString()), payload);
  assert.deepEqual(await decryptRecord(key, deviceId, { id, version: 1, nonce, ciphertext }), payload);
});
