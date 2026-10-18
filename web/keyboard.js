// Local practice only; explicit email export opens a draft in the user's mail app.
export function historyDraftUrl(recipient, entries) {
  const address = recipient.trim();
  if (address.length > 254 || !/^[^\s@,;:?%<>\\]+@[^\s@,;:?%<>\\]+\.[^\s@,;:?%<>\\]+$/.test(address)) {
    throw new Error('Enter one valid recipient email address.');
  }
  if (!entries.length) throw new Error('Practice history is empty. Press keys in the practice area first.');
  const body = 'ClearTrail keyboard practice history (oldest first, latest 100 presses).\r\n\r\n' +
    entries.map(entry => `${entry.number}. ${entry.label}`).join('\r\n');
  return `mailto:${encodeURIComponent(address)}?subject=${encodeURIComponent('Keyboard practice history')}&body=${encodeURIComponent(body)}`;
}
export const KEY_ROWS = [
  [['Backquote', '`'], ...'1234567890'.split('').map(n => [`Digit${n}`, n]), ['Minus', '-'], ['Equal', '='], ['Backspace', 'Backspace', 2]],
  [['Tab', 'Tab', 1.5], ...'QWERTYUIOP'.split('').map(n => [`Key${n}`, n]), ['BracketLeft', '['], ['BracketRight', ']'], ['Backslash', '\\', 1.5]],
  [['CapsLock', 'Caps Lock', 1.8], ...'ASDFGHJKL'.split('').map(n => [`Key${n}`, n]), ['Semicolon', ';'], ['Quote', "'"], ['Enter', 'Enter', 2.2]],
  [['ShiftLeft', 'Shift', 2.3], ...'ZXCVBNM'.split('').map(n => [`Key${n}`, n]), ['Comma', ','], ['Period', '.'], ['Slash', '/'], ['ShiftRight', 'Shift', 2.7]],
  [['ControlLeft', 'Ctrl', 1.5], ['MetaLeft', 'Meta'], ['AltLeft', 'Alt'], ['Space', 'Space', 6], ['AltRight', 'Alt'], ['MetaRight', 'Meta'], ['ControlRight', 'Ctrl', 1.5]],
  [['Escape', 'Esc'], ['ArrowLeft', '←'], ['ArrowUp', '↑'], ['ArrowDown', '↓'], ['ArrowRight', '→'], ['Delete', 'Delete']]
];

const SYMBOLS = Object.fromEntries([
  ['Backquote', '`', '~'], ...'1234567890'.split('').map((n, i) => [`Digit${n}`, n, '!@#$%^&*()'[i]]),
  ['Minus', '-', '_'], ['Equal', '=', '+'], ['BracketLeft', '[', '{'], ['BracketRight', ']', '}'],
  ['Backslash', '\\', '|'], ['Semicolon', ';', ':'], ['Quote', "'", '"'],
  ['Comma', ',', '<'], ['Period', '.', '>'], ['Slash', '/', '?']
].map(([code, base, shifted]) => [code, [base, shifted]]));
const SPECIAL = { ShiftLeft:'Left Shift', ShiftRight:'Right Shift', ControlLeft:'Left Ctrl', ControlRight:'Right Ctrl',
  AltLeft:'Left Alt', AltRight:'Right Alt', MetaLeft:'Left Meta', MetaRight:'Right Meta',
  ArrowLeft:'← Left', ArrowRight:'→ Right', ArrowUp:'↑ Up', ArrowDown:'↓ Down', CapsLock:'Caps Lock' };
export function keycapLabel(code, fallback, shift = false, caps = false) {
  if (/^Key[A-Z]$/.test(code)) return shift !== caps ? code.slice(3) : code.slice(3).toLowerCase();
  if (SYMBOLS[code]) { const [base, upper] = SYMBOLS[code]; return shift ? `${upper} · ${base}` : `${base} · ${upper}`; }
  return SPECIAL[code] || fallback;
}
export function historyKeyLabel(event) {
  if (SPECIAL[event.code]) return SPECIAL[event.code];
  if (event.key === ' ' || event.code === 'Space') return 'Space';
  if (event.key === 'Escape') return 'Escape';
  if (event.key === 'Dead') return 'Dead key';
  return event.key && event.key !== 'Unidentified' ? event.key :
    KEY_ROWS.flat().find(([code]) => code === event.code)?.[1] || 'Unidentified key';
}

export function setupKeyboardPractice(doc, win) {
  const area = doc.getElementById('keyboard-practice');
  const history = doc.getElementById('key-history');
  const empty = doc.getElementById('key-history-empty');
  const status = doc.getElementById('keyboard-state');
  const send = doc.getElementById('send-key-history');
  const recipient = doc.getElementById('key-history-recipient');
  const emailStatus = doc.getElementById('key-history-email-status');
  const liveKeys = new Map(), held = new Set();
  let shift = false, caps = false;
  let sequence = 0;
  for (const id of ['reference-keyboard', 'live-keyboard']) {
    const keyboard = doc.getElementById(id);
    for (const keys of KEY_ROWS) {
      const row = doc.createElement('div'); row.className = 'keyboard-row';
      for (const [code, label, width = 1] of keys) {
        const key = doc.createElement('span'); key.className = 'keyboard-key';
        key.textContent = keycapLabel(code, label); key.style.flex = String(width); row.append(key);
        if (id === 'live-keyboard') liveKeys.set(code, key);
      }
      keyboard.append(row);
    }
  }
  const updateLabels = event => {
    shift = event?.shiftKey ?? (held.has('ShiftLeft') || held.has('ShiftRight'));
    if (typeof event?.getModifierState === 'function') caps = event.getModifierState('CapsLock');
    for (const [code, label] of KEY_ROWS.flat()) {
      const key = liveKeys.get(code);
      key.textContent = keycapLabel(code, label, shift, caps);
      if (code === 'CapsLock' && caps) key.classList.add('modifier-active');
      else key.classList.remove('modifier-active');
    }
  };
  const reset = () => {
    held.clear();
    shift = false; caps = false; updateLabels();
    for (const key of liveKeys.values()) key.classList.remove('pressed');
    status.textContent = 'Paused. Focus this area to begin.';
  };
  area.addEventListener('focus', () => { status.textContent = 'Listening here. Tab leaves this area.'; });
  area.addEventListener('pointerdown', () => area.focus());
  area.addEventListener('keydown', event => {
    if (doc.activeElement !== area || !doc.hasFocus() || doc.hidden || event.isComposing) return;
    // Keep Tab navigation and browser/system shortcuts intact.
    const key = liveKeys.get(event.code);
    if (key) key.classList.add('pressed');
    const wasHeld = held.has(event.code);
    if (event.code === 'CapsLock' && !event.repeat && !wasHeld && typeof event.getModifierState !== 'function') caps = !caps;
    if (!event.repeat) held.add(event.code);
    updateLabels(event);
    if (event.repeat || wasHeld) return;
    const item = doc.createElement('li');
    const label = historyKeyLabel(event);
    item.textContent = `${label} (${event.code || 'unidentified'})`;
    item.value = ++sequence;
    history.append(item);
    if (history.children.length > 100) history.firstElementChild.remove();
    empty.hidden = true;
    send.disabled = false;
    emailStatus.textContent = '';
  });
  area.addEventListener('keyup', event => {
    held.delete(event.code); liveKeys.get(event.code)?.classList.remove('pressed');
    updateLabels(event);
  });
  area.addEventListener('blur', reset);
  win.addEventListener('blur', reset);
  doc.addEventListener('visibilitychange', () => { if (doc.hidden) reset(); });
  win.addEventListener('pagehide', () => { reset(); clear(); recipient.value = ''; });
  function clear() { history.replaceChildren(); sequence = 0; empty.hidden = false; send.disabled = true; emailStatus.textContent = ''; }
  doc.getElementById('clear-key-history').addEventListener('click', clear);
  doc.getElementById('key-history-email-form').addEventListener('submit', event => {
    event.preventDefault();
    try {
      const entries = Array.from(history.children, item => ({number: item.value, label: item.textContent}));
      const url = historyDraftUrl(recipient.value, entries);
      win.location.assign(url);
      emailStatus.textContent = 'Email draft requested. Review and send in your email app. Delivery is not confirmed here.';
    } catch (error) { emailStatus.textContent = error.message; }
  });
}
