import test from 'node:test';
import assert from 'node:assert/strict';
import { newKey, encryptRecord, decryptRecord, fromBase64, toBase64 } from '../web/crypto.js';
import { validatePayload, validateRecovery, flatten, demoRecords } from '../web/model.js';
import { readFileSync } from 'node:fs';

const payload = () => ({ schemaVersion: 1, sessionId: crypto.randomUUID(), from: 100, to: 1000, battery: 80, apps: [{ packageName: 'com.whatsapp', intervals: [{ start: 200, end: 400, estimated: true }] }] });

test('AES-GCM round trip; ciphertext and key cannot be substituted', async () => {
  const key = newKey(), device = crypto.randomUUID(), id = crypto.randomUUID(), data = payload();
  const envelope = { id, ...await encryptRecord(key, device, id, data) };
  assert.deepEqual(await decryptRecord(key, device, envelope), data);
  await assert.rejects(decryptRecord(newKey(), device, envelope));
  await assert.rejects(decryptRecord(key, crypto.randomUUID(), envelope));
  await assert.rejects(decryptRecord(key, device, { ...envelope, id: crypto.randomUUID() }));
  const tampered = fromBase64(envelope.ciphertext); tampered[5] ^= 1;
  await assert.rejects(decryptRecord(key, device, { ...envelope, ciphertext: toBase64(tampered) }));
});
test('each encryption has a fresh nonce', async () => {
  const key = newKey(), device = crypto.randomUUID(), id = crypto.randomUUID();
  const a = await encryptRecord(key, device, id, payload()), b = await encryptRecord(key, device, id, payload());
  assert.notEqual(a.nonce, b.nonce);
  assert.equal(fromBase64(a.nonce).length, 12);
});
test('strict metadata schema rejects content and unsupported applications', () => {
  assert.deepEqual(validatePayload(payload()).battery, 80);
  for (const field of ['text', 'password', 'url', 'messages', 'notification']) assert.throws(() => validatePayload({ ...payload(), [field]: 'synthetic forbidden content' }));
  const extra = payload(); extra.apps[0].intervals[0].text = 'secret'; assert.throws(() => validatePayload(extra));
  const unknown = payload(); unknown.apps[0].packageName = 'com.bank'; assert.throws(() => validatePayload(unknown));
});
test('rejects overlapping, reversed, out-of-window and unbounded intervals', () => {
  for (const interval of [{ start: 300, end: 200, estimated: true }, { start: 50, end: 300, estimated: true }, { start: 200, end: 1100, estimated: true }]) {
    const data = payload(); data.apps[0].intervals = [interval]; assert.throws(() => validatePayload(data));
  }
  const overlapping = payload(); overlapping.apps[0].intervals.push({ start: 300, end: 500, estimated: true }); assert.throws(() => validatePayload(overlapping));
  assert.throws(() => validatePayload({ ...payload(), to: 90_000_000 }));
});
test('filters clip durations to chosen time range and selected package', () => {
  const rows = flatten([payload()], { from: 250, to: 350, packageName: 'com.whatsapp' });
  assert.equal(rows[0].duration, 100);
  assert.equal(flatten([payload()], { packageName: 'com.android.chrome' }).length, 0);
  assert.equal(flatten([payload()], { from: 500 }).length, 0);
});
test('recovery files cannot send a key or token to another origin', () => {
  const record = { version: 1, deviceId: crypto.randomUUID(), baseUrl: 'https://example.com', key: newKey(), readToken: 'a'.repeat(43), label: 'My phone' };
  assert.equal(validateRecovery(record, 'https://example.com'), record);
  assert.throws(() => validateRecovery(record, 'https://attacker.example'));
  assert.throws(() => validateRecovery({ ...record, baseUrl: 'https://example.com/secret' }, 'https://example.com'));
  assert.throws(() => validateRecovery({ ...record, baseUrl: 'http://example.com' }, 'http://example.com'));
});
test('demo records meet real metadata schema and do not include future intervals', () => {
  const now = Date.now();
  const records = demoRecords(now);
  assert.equal(records.length, 7);
  for (const data of records) { validatePayload(data); assert.ok(data.to <= now); }
});
test('Android manifest has a system-bound isolated IME, visible launcher and no capture services', () => {
  const manifest = readFileSync(new URL('../android/app/src/main/AndroidManifest.xml', import.meta.url), 'utf8');
  const permissions = [...manifest.matchAll(/<uses-permission android:name="([^"]+)"/g)].map(match => match[1]).sort();
  assert.deepEqual(permissions, ['android.permission.INTERNET', 'android.permission.PACKAGE_USAGE_STATS']);
  assert.match(manifest, /android.intent.category.LAUNCHER/);
  assert.match(manifest, /android:allowBackup="false"/);
  assert.doesNotMatch(manifest, /<receiver|AccessibilityService|BOOT_COMPLETED/);
  assert.equal([...manifest.matchAll(/<service\b/g)].length, 1);
  assert.match(manifest, /android:name="\.ClearTrailInputMethod"[^>]*android:permission="android.permission.BIND_INPUT_METHOD"[^>]*android:process=":keyboard"/);
  assert.match(manifest, /android.view.InputMethod/);
  assert.match(manifest, /android:resource="@xml\/input_method"/);
});

test('IME is limited to static UI and current-editor writes, without a history/storage/network path', () => {
  const source = readFileSync(new URL('../android/app/src/main/java/app/cleartrail/ClearTrailInputMethod.kt', import.meta.url), 'utf8');
  const imports = [...source.matchAll(/^import (.+)$/gm)].map(m => m[1].trim());
  assert.ok(imports.every(name => /^android\.(graphics|inputmethodservice|text|view|widget)\./.test(name)));
  assert.doesNotMatch(source, /SessionController|ClearTrailApplication|Outbox|Vault|SyncClient|WebView|SharedPreferences|java\.io|java\.net|android\.util\.Log|Clipboard|sendBroadcast|startService|getTextBeforeCursor|getTextAfterCursor|getSelectedText|getExtractedText|setComposingText/);
  assert.match(source, /currentInputConnection\?\.commitText/);
  assert.match(source, /FLAG_SECURE/);
  assert.match(source, /onEvaluateFullscreenMode\(\) = false/);
  const metadata = readFileSync(new URL('../android/app/src/main/res/xml/input_method.xml', import.meta.url), 'utf8');
  assert.match(metadata, /android:imeSubtypeMode="keyboard"/);
  assert.match(metadata, /android:settingsActivity="app.cleartrail.MainActivity"/);
});
