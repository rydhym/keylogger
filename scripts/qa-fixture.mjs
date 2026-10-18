// Explicitly synthetic local integration fixture; never prints keys or tokens.
import { readFileSync, writeFileSync, unlinkSync } from 'node:fs';
import { newKey, encryptRecord } from '../web/crypto.js';
import { demoRecords } from '../web/model.js';
const origin = 'http://127.0.0.1:4310';
const file = '.local/qa-recovery.json';
if (process.argv.includes('--cleanup')) {
  const recovery = JSON.parse(readFileSync(file, 'utf8'));
  const response = await fetch(`${origin}/api/devices/${recovery.deviceId}`, { method: 'DELETE', headers: { Authorization: `Bearer ${recovery.readToken}` } });
  if (!response.ok) throw new Error('Fixture cleanup failed');
  unlinkSync(file); console.log('Synthetic fixture revoked and recovery removed.');
} else {
  const admin = readFileSync('.local/operator-token.txt', 'utf8').trim();
  const response = await fetch(`${origin}/api/devices`, { method: 'POST', headers: { Authorization: `Bearer ${admin}` } });
  if (!response.ok) throw new Error('Fixture registration failed');
  const paired = await response.json(), key = newKey();
  for (const payload of demoRecords()) {
    const id = crypto.randomUUID();
    const envelope = await encryptRecord(key, paired.deviceId, id, payload);
    const upload = await fetch(`${origin}/api/devices/${paired.deviceId}/records/${id}`, { method: 'PUT', headers: { Authorization: `Bearer ${paired.uploadToken}`, 'Content-Type': 'application/json' }, body: JSON.stringify(envelope) });
    if (!upload.ok) throw new Error('Fixture upload failed');
  }
  writeFileSync(file, JSON.stringify({ version: 1, deviceId: paired.deviceId, baseUrl: origin, readToken: paired.readToken, key, label: 'QA synthetic device' }), { mode: 0o600 });
  console.log('Synthetic encrypted fixture uploaded; recovery saved under .local for browser QA.');
}
