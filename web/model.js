export const APPS = Object.freeze({
  'com.whatsapp': { name: 'WhatsApp', initial: 'W', color: 'green' },
  'com.instagram.android': { name: 'Instagram', initial: 'I', color: 'pink' },
  'com.android.chrome': { name: 'Chrome', initial: 'C', color: 'blue' },
});
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
function exact(value, keys) {
  return value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).sort().join(',') === [...keys].sort().join(',');
}
const time = n => Number.isSafeInteger(n) && n >= 0;
export function validatePayload(value) {
  if (!exact(value, ['schemaVersion', 'sessionId', 'from', 'to', 'battery', 'apps']) || value.schemaVersion !== 1 || !uuid.test(value.sessionId) || !time(value.from) || !time(value.to) || value.to <= value.from || value.to - value.from > 86400_000 || !Number.isInteger(value.battery) || value.battery < -1 || value.battery > 100 || !Array.isArray(value.apps) || value.apps.length > 3) throw new Error('Invalid usage record');
  const seen = new Set();
  let count = 0;
  for (const app of value.apps) {
    if (!exact(app, ['packageName', 'intervals']) || !Object.hasOwn(APPS, app.packageName) || seen.has(app.packageName) || !Array.isArray(app.intervals)) throw new Error('Invalid application metadata');
    seen.add(app.packageName);
    let previousEnd = value.from;
    for (const interval of app.intervals) {
      if (++count > 512 || !exact(interval, ['start', 'end', 'estimated']) || !time(interval.start) || !time(interval.end) || interval.start < previousEnd || interval.end <= interval.start || interval.end > value.to || interval.estimated !== true) throw new Error('Invalid usage interval');
      previousEnd = interval.end;
    }
  }
  return value;
}
export function validateRecovery(value, origin) {
  if (!exact(value, ['version', 'deviceId', 'baseUrl', 'readToken', 'key', 'label']) || value.version !== 1 || !uuid.test(value.deviceId) || !/^[A-Za-z0-9_-]{43}$/.test(value.readToken) || !/^[A-Za-z0-9+/]{43}=$/.test(value.key) || typeof value.label !== 'string' || value.label.length > 60) throw new Error('Invalid recovery file');
  const endpoint = new URL(value.baseUrl);
  if (endpoint.origin !== origin || endpoint.pathname !== '/' || endpoint.search || endpoint.hash || endpoint.username || endpoint.password) throw new Error('Open this recovery file on its original dashboard server');
  if (endpoint.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(endpoint.hostname)) throw new Error('HTTPS is required');
  return value;
}
export function flatten(records, { from = 0, to = Infinity, packageName = '' } = {}) {
  const rows = [];
  for (const record of records) {
    for (const app of record.apps) {
      if (packageName && app.packageName !== packageName) continue;
      for (const interval of app.intervals) {
        const start = Math.max(from, interval.start), end = Math.min(to, interval.end);
        if (end > start) rows.push({ ...interval, start, end, packageName: app.packageName, sessionId: record.sessionId, duration: end - start });
      }
    }
  }
  return rows.sort((a, b) => b.start - a.start);
}
export function duration(ms) {
  const minutes = Math.floor(ms / 60_000);
  return minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : minutes > 0 ? `${minutes}m` : `${Math.floor(ms / 1000)}s`;
}
export function demoRecords(now = Date.now()) {
  const records = [];
  for (let day = 6; day >= 0; day--) {
    const date = new Date(now); date.setHours(0, 0, 0, 0); date.setDate(date.getDate() - day);
    const from = date.getTime(), to = Math.min(from + 86400_000, now);
    if (to <= from) continue;
    const apps = Object.keys(APPS).map((packageName, i) => {
      const intervals = [];
      for (let j = 0; j < 4; j++) {
        const start = from + (8 + j * 3) * 3600_000 + i * 22 * 60_000;
        const end = Math.min(start + (8 + ((day * 13 + i * 11 + j * 7) % 22)) * 60_000, to);
        if (end > start) intervals.push({ start, end, estimated: true });
      }
      return { packageName, intervals };
    });
    records.push(validatePayload({ schemaVersion: 1, sessionId: crypto.randomUUID(), from, to, battery: 81 - day * 4, apps }));
  }
  return records;
}
