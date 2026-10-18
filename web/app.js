import { newKey, encryptRecord, decryptRecord } from './crypto.js';
import { APPS, validatePayload, validateRecovery, flatten, duration, demoRecords } from './model.js';
import { setupKeyboardPractice } from './keyboard.js';

const $ = id => document.getElementById(id);
let recovery = null, records = [], demo = false, generation = 0, visibleRows = 12;
let lastReceived = null;
const setStatus = (message = '', error = false) => { $('status').textContent = message; $('status').classList.toggle('error', error); };
const formatDate = n => new Date(n).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
function element(tag, className, content) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (content !== undefined) node.textContent = content;
  return node;
}
async function api(route, token, method = 'GET', body) {
  const response = await fetch(route, { method, headers: { Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(15_000), cache: 'no-store', credentials: 'omit', redirect: 'error' });
  const value = await response.json();
  if (!response.ok) throw new Error(value.error || 'Request failed');
  return value;
}
function range() {
  const start = new Date(); start.setHours(0, 0, 0, 0); start.setDate(start.getDate() - Number($('range-filter').value) + 1);
  return { from: start.getTime(), to: Date.now(), packageName: $('app-filter').value };
}
function currentRows() { return flatten(records, range()); }
function render() {
  const unlocked = recovery !== null;
  $('mode-pill').textContent = demo ? 'DEMO' : unlocked ? 'PAIRED' : 'LOCKED';
  $('demo-banner').hidden = !demo;
  $('empty-state').hidden = demo || unlocked;
  $('device-label').textContent = demo ? 'Sample Android phone' : unlocked ? recovery.label : 'No phone connected';
  $('sync-label').textContent = demo ? ' · Synthetic data' : lastReceived ? ` · Last upload ${formatDate(lastReceived)}` : unlocked ? ' · Waiting for first upload' : ' · Waiting for pairing';
  for (const id of ['refresh-button', 'delete-button', 'revoke-button']) $(id).disabled = !unlocked || demo;
  $('lock-button').disabled = !unlocked && !demo;
  const rows = currentRows();
  $('export-button').disabled = rows.length === 0;
  $('total-time').textContent = unlocked || demo ? duration(rows.reduce((sum, row) => sum + row.duration, 0)) : '—';
  $('session-count').textContent = unlocked || demo ? String(rows.length) : '—';
  $('app-count').textContent = unlocked || demo ? String(new Set(rows.map(row => row.packageName)).size) : '—';
  const newest = [...records].sort((a, b) => b.to - a.to)[0];
  $('battery').textContent = newest && newest.battery >= 0 ? `${newest.battery}%` : '—';
  $('battery-caption').textContent = newest ? `Recorded ${formatDate(newest.to)}` : 'At latest captured record';
  renderDaily(rows);
  renderApps(rows);
  renderTimeline(rows);
}
function renderDaily(rows) {
  const chart = $('daily-chart'); chart.replaceChildren();
  const count = Number($('range-filter').value);
  // For 30 days, aggregate into five-day bins; labels refer to bin start dates.
  const step = count === 30 ? 5 : 1;
  const start = new Date(range().from), buckets = [];
  for (let i = 0; i < count; i += step) {
    const date = new Date(start); date.setDate(start.getDate() + i);
    const end = new Date(date); end.setDate(end.getDate() + step);
    const total = rows.reduce((sum, row) => sum + Math.max(0, Math.min(row.end, end.getTime()) - Math.max(row.start, date.getTime())), 0);
    buckets.push({ date, total });
  }
  const max = Math.max(60_000, ...buckets.map(bucket => bucket.total));
  for (const { date, total } of buckets) {
    const column = element('div', 'bar-column');
    const bar = element('div', 'bar'); bar.style.height = `${Math.max(1, total / max * 135)}px`;
    bar.title = `${date.toLocaleDateString()}: ${duration(total)}${step > 1 ? ' over five days' : ''}`;
    column.append(element('span', 'bar-value', total ? duration(total) : '—'), bar, element('span', 'bar-label', date.toLocaleDateString([], count > 7 ? { month: 'short', day: 'numeric' } : { weekday: 'short' })));
    chart.append(column);
  }
}
function renderApps(rows) {
  const container = $('app-breakdown'); container.replaceChildren();
  const total = rows.reduce((sum, row) => sum + row.duration, 0);
  const apps = Object.entries(APPS).filter(([pkg]) => !$('app-filter').value || $('app-filter').value === pkg).map(([pkg, app]) => ({ ...app, total: rows.filter(row => row.packageName === pkg).reduce((sum, row) => sum + row.duration, 0) })).sort((a, b) => b.total - a.total);
  for (const app of apps) {
    const item = element('div', 'app-item'), row = element('div', 'app-row');
    const percentage = total ? Math.round(app.total / total * 100) : 0;
    row.append(element('span', `app-icon ${app.color}`, app.initial), element('span', 'app-name', app.name), element('span', '', duration(app.total)), element('small', '', `${percentage}%`));
    const track = element('div', 'track'), fill = element('div', 'track-fill'); fill.style.width = `${percentage}%`; track.append(fill); item.append(row, track); container.append(item);
  }
}
function renderTimeline(rows) {
  $('row-count').textContent = `${rows.length} intervals`;
  const body = $('timeline-body'); body.replaceChildren();
  if (!rows.length) {
    const row = element('tr'), cell = element('td', 'empty-cell', recovery ? 'No activity in this range. Capture and sync from your phone.' : 'Your activity timeline will appear here.'); cell.colSpan = 4; row.append(cell); body.append(row);
  }
  for (const row of rows.slice(0, visibleRows)) {
    const app = APPS[row.packageName], tr = element('tr'), name = element('td');
    name.append(element('span', `app-icon ${app.color}`, app.initial), document.createTextNode(app.name));
    const type = element('td'); type.append(element('span', 'type-chip', 'Estimated foreground'));
    tr.append(name, element('td', '', formatDate(row.start)), element('td', '', duration(row.duration)), type); body.append(tr);
  }
  $('more-button').hidden = rows.length <= visibleRows;
}
async function refresh() {
  if (!recovery || demo) return;
  const active = recovery, epoch = ++generation;
  $('refresh-button').disabled = true;
  setStatus('Downloading encrypted records…');
  const loaded = []; let cursor = 0, received = null;
  try {
    for (let page = 0; page < 26; page++) {
      const batch = await api(`/api/devices/${active.deviceId}/records?after=${cursor}`, active.readToken);
      if (epoch !== generation) return;
      for (const envelope of batch.records) {
        const payload = validatePayload(await decryptRecord(active.key, active.deviceId, envelope));
        loaded.push(payload); received = Math.max(received || 0, envelope.received);
      }
      if (batch.records.length < 200) break;
      if (batch.nextCursor <= cursor) throw new Error('Invalid server pagination');
      cursor = batch.nextCursor;
    }
    if (epoch !== generation) return;
    records = loaded; lastReceived = received;
    setStatus(loaded.length ? `${loaded.length} encrypted records opened in this browser.` : 'Paired. Waiting for your phone’s first encrypted upload.');
  } catch (error) {
    if (epoch !== generation) return;
    records = []; lastReceived = null;
    setStatus(`Could not open records: ${error.message}. No unverified data was displayed.`, true);
  } finally { if (epoch === generation) render(); }
}
function lock() {
  generation++; recovery = null; records = []; demo = false; lastReceived = null;
  $('phone-bundle').value = ''; $('operator-token').value = ''; $('recovery-input').value = ''; $('pair-output').hidden = true; $('pair-status').textContent = '';
  $('connect-dialog').close(); setStatus('Dashboard locked. Import your recovery file to reopen it.'); render();
}
function download(name, data, type) {
  const url = URL.createObjectURL(new Blob([data], { type }));
  const a = element('a'); a.href = url; a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
async function runDemo() {
  const epoch = ++generation;
  setStatus('Preparing encrypted synthetic sample…');
  const key = newKey(), deviceId = crypto.randomUUID();
  const samples = [];
  for (const record of demoRecords()) {
    const id = crypto.randomUUID();
    samples.push(validatePayload(await decryptRecord(key, deviceId, { id, ...await encryptRecord(key, deviceId, id, record) })));
  }
  if (epoch !== generation) return;
  recovery = null; records = samples; demo = true; lastReceived = null;
  setStatus('Sample records encrypted and decrypted locally. Nothing was uploaded.'); render();
}
function openPairing() {
  if (recovery && !confirm('Save your recovery file before creating another pairing. Continue?')) return;
  $('connect-dialog').showModal();
}
$('pair-form').addEventListener('submit', async event => {
  event.preventDefault(); $('pair-button').disabled = true; $('pair-status').textContent = '';
  const epoch = generation;
  try {
    const key = newKey();
    const paired = await api('/api/devices', $('operator-token').value.trim(), 'POST');
    if (epoch !== generation) return;
    generation++; demo = false; records = []; lastReceived = null;
    recovery = { version: 1, deviceId: paired.deviceId, baseUrl: location.origin, readToken: paired.readToken, key, label: $('phone-name').value.trim() || 'My Android phone' };
    const phone = { version: 1, deviceId: paired.deviceId, baseUrl: location.origin, uploadToken: paired.uploadToken, key };
    $('phone-bundle').value = JSON.stringify(phone);
    $('operator-token').value = ''; $('pair-output').hidden = false;
    setStatus('Pairing created. Save recovery and transfer the phone setup bundle.'); render();
  } catch (error) { $('pair-status').textContent = error.message; }
  finally { $('pair-button').disabled = false; }
});
$('save-recovery').addEventListener('click', () => { if (recovery) download(`cleartrail-recovery-${recovery.deviceId}.json`, JSON.stringify(recovery, null, 2), 'application/json'); });
$('recovery-input').addEventListener('change', async event => {
  const file = event.target.files[0]; if (!file) return;
  try {
    if (file.size > 4096) throw new Error('Recovery file is too large');
    const parsed = validateRecovery(JSON.parse(await file.text()), location.origin);
    generation++; recovery = parsed; demo = false; records = []; lastReceived = null;
    $('phone-bundle').value = ''; $('operator-token').value = ''; $('pair-output').hidden = true;
    $('connect-dialog').close(); render(); await refresh();
  } catch (error) { $('pair-status').textContent = error.message; }
  event.target.value = '';
});
for (const id of ['connect-nav', 'connect-button']) $(id).addEventListener('click', openPairing);
$('close-dialog').addEventListener('click', () => $('connect-dialog').close());
$('demo-button').addEventListener('click', () => runDemo().catch(error => setStatus(error.message, true)));
$('exit-demo').addEventListener('click', lock);
$('lock-button').addEventListener('click', lock);
$('refresh-button').addEventListener('click', refresh);
for (const id of ['app-filter', 'range-filter']) $(id).addEventListener('change', () => { visibleRows = 12; render(); });
$('more-button').addEventListener('click', () => { visibleRows += 50; renderTimeline(currentRows()); });
$('overview-nav').addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));
$('timeline-nav').addEventListener('click', () => $('timeline').scrollIntoView({ behavior: 'smooth' }));
$('privacy-button').addEventListener('click', () => $('privacy-dialog').showModal());
$('close-privacy').addEventListener('click', () => $('privacy-dialog').close());
$('export-button').addEventListener('click', () => {
  const csv = ['application,start_utc,end_utc,duration_seconds,estimated,session_id', ...currentRows().map(row => [APPS[row.packageName].name, new Date(row.start).toISOString(), new Date(row.end).toISOString(), Math.floor(row.duration / 1000), true, row.sessionId].join(','))].join('\r\n');
  download(demo ? 'cleartrail-SAMPLE.csv' : 'cleartrail-usage.csv', csv, 'text/csv');
});
$('delete-button').addEventListener('click', async () => {
  if (!recovery || !confirm('Delete all history on this server? Pause the phone and clear its outbox first to prevent queued records from uploading later.')) return;
  const active = recovery, epoch = ++generation;
  try { await api(`/api/devices/${active.deviceId}/records`, active.readToken, 'DELETE'); if (epoch !== generation) return; records = []; lastReceived = null; render(); setStatus('Remote history deleted. Pairing remains active.'); }
  catch (error) { if (epoch === generation) { setStatus(error.message, true); render(); } }
});
$('revoke-button').addEventListener('click', async () => {
  if (!recovery || !confirm('Revoke this phone and delete all of its remote records? Existing pairing and recovery credentials will stop working.')) return;
  const active = recovery, epoch = ++generation;
  try { await api(`/api/devices/${active.deviceId}`, active.readToken, 'DELETE'); if (epoch !== generation) return; lock(); setStatus('Phone revoked and remote history deleted. Clear local data on the phone separately.'); }
  catch (error) { if (epoch === generation) { setStatus(error.message, true); render(); } }
});
setupKeyboardPractice(document, window);
render();
