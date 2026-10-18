import test from 'node:test';
import assert from 'node:assert/strict';
import { setupKeyboardPractice, historyDraftUrl, keycapLabel, historyKeyLabel } from '../web/keyboard.js';

// Small event/DOM fixture exercises the actual focus boundary and lifecycle handlers.
class Node extends EventTarget {
  children = []; classes = new Set(); hidden = false;
  classList = { add: v => this.classes.add(v), remove: v => this.classes.delete(v) };
  style = {};
  append(node) { node.parent = this; this.children.push(node); }
  replaceChildren() { this.children = []; }
  get firstElementChild() { return this.children[0]; }
  remove() { this.parent.children.splice(this.parent.children.indexOf(this), 1); }
}
function fixture() {
  const doc = new Node(), win = new Node(), nodes = new Map();
  win.location = { assign: url => { win.draft = url; } };
  doc.getElementById = id => { if (!nodes.has(id)) nodes.set(id, new Node()); return nodes.get(id); };
  doc.createElement = () => new Node(); doc.hasFocus = () => true;
  setupKeyboardPractice(doc, win);
  const area = doc.getElementById('keyboard-practice'); doc.activeElement = area;
  const emit = (target, type, values = {}) => target.dispatchEvent(Object.assign(new Event(type), values));
  const press = (code = 'KeyA', key = 'a', extras = {}) => emit(area, 'keydown', {code, key, ...extras});
  const keys = () => doc.getElementById('live-keyboard').children.flatMap(row => row.children);
  return {doc, win, area, emit, press, keys, history:doc.getElementById('key-history')};
}
test('matching keyboards, held keys, repeat suppression and ordered bounded history', () => {
  const f = fixture();
  assert.deepEqual(f.doc.getElementById('reference-keyboard').children.map(r => r.children.map(k => k.textContent)), f.doc.getElementById('live-keyboard').children.map(r => r.children.map(k => k.textContent)));
  f.press(); f.press('KeyA', 'a', {repeat:true}); f.press('KeyB', 'b');
  assert.equal(f.keys().filter(k => k.classes.has('pressed')).length, 2);
  assert.deepEqual(f.history.children.map(n => n.textContent), ['a (KeyA)', 'b (KeyB)']);
  f.emit(f.area, 'keyup', {code:'KeyA'});
  assert.equal(f.keys().filter(k => k.classes.has('pressed')).length, 1);
  for (let i = 0; i < 105; i++) { f.press('KeyA', 'a'); f.emit(f.area, 'keyup', {code:'KeyA'}); }
  assert.equal(f.history.children.length, 100);
  assert.equal(f.history.children[99].value, 107);
  f.emit(f.doc.getElementById('clear-key-history'), 'click');
  assert.equal(f.history.children.length, 0);
});
test('focus, visibility, blur and page lifecycle prevent out-of-area capture and stuck keys', () => {
  const f = fixture();
  f.doc.activeElement = new Node(); f.press(); assert.equal(f.history.children.length, 0);
  f.doc.activeElement = f.area;
  for (const [target, type] of [[f.area,'blur'], [f.win,'blur'], [f.doc,'visibilitychange']]) {
    f.press(); if (type === 'visibilitychange') f.doc.hidden = true;
    f.emit(target, type); assert.equal(f.keys().some(k => k.classes.has('pressed')), false);
    f.doc.hidden = false;
  }
  const count = f.history.children.length;
  f.doc.hidden = true; f.press(); assert.equal(f.history.children.length, count);
  f.doc.hidden = false; f.doc.hasFocus = () => false; f.press(); assert.equal(f.history.children.length, count);
  f.emit(f.win, 'pagehide'); assert.equal(f.history.children.length, 0);
});

test('email draft requires an explicit submit and includes only displayed practice history', () => {
  const f = fixture();
  const recipient = f.doc.getElementById('key-history-recipient');
  recipient.value = 'student@example.com';
  f.doc.activeElement = recipient; f.press('KeyX', 'x');
  f.doc.activeElement = f.area; f.press(); f.press('KeyB', 'b');
  assert.equal(f.win.draft, undefined);
  f.emit(f.doc.getElementById('key-history-email-form'), 'submit');
  const url = new URL(f.win.draft);
  assert.equal(decodeURIComponent(url.pathname), 'student@example.com');
  assert.equal(url.searchParams.get('subject'), 'Keyboard practice history');
  assert.equal(url.searchParams.get('body'), 'ClearTrail keyboard practice history (oldest first, latest 100 presses).\r\n\r\n1. a (KeyA)\r\n2. b (KeyB)');
  assert.equal(f.doc.getElementById('send-key-history').disabled, false);
  f.emit(f.doc.getElementById('clear-key-history'), 'click');
  assert.equal(f.doc.getElementById('send-key-history').disabled, true);
  f.win.draft = undefined;
  f.emit(f.doc.getElementById('key-history-email-form'), 'submit');
  assert.equal(f.win.draft, undefined);
  assert.match(f.doc.getElementById('key-history-email-status').textContent, /empty/);
});

test('draft encoding prevents recipient/header injection and preserves literal key text', () => {
  for (const recipient of ['', 'bad', 'a@b.com\r\nBcc:c@d.com', 'a@b.com?bcc=c@d.com', 'a@b.com,c@d.com', 'a@b.com%0ABcc']) {
    assert.throws(() => historyDraftUrl(recipient, [{number:1, label:'a'}]), /recipient/);
  }
  const url = new URL(historyDraftUrl(' student+test@example.com ', [{number:99, label:'&?+#% (KeyA)'}]));
  assert.equal(decodeURIComponent(url.pathname), 'student+test@example.com');
  assert.equal([...url.searchParams].length, 2);
  assert.match(url.searchParams.get('body'), /99\. &\?\+#% \(KeyA\)$/);
});

test('live keycaps follow Shift/Caps Lock and reset on focus loss without changing the reference', () => {
  const f = fixture();
  const label = text => f.keys().find(k => k.textContent === text);
  assert.ok(label('a')); assert.ok(label('1 · !'));
  f.press('ShiftLeft', 'Shift', {shiftKey:true, getModifierState:() => false});
  assert.ok(label('A')); assert.ok(label('! · 1'));
  f.press('Digit1', '!', {shiftKey:true, getModifierState:() => false});
  assert.match(f.history.children.at(-1).textContent, /^! \(Digit1\)$/);
  f.emit(f.area, 'keyup', {code:'ShiftLeft', shiftKey:false, getModifierState:() => false});
  assert.ok(label('a')); assert.ok(label('1 · !'));
  f.press('CapsLock', 'CapsLock', {shiftKey:false, getModifierState:() => true});
  assert.ok(label('A')); assert.ok(label('1 · !'));
  assert.ok(label('Caps Lock').classes.has('modifier-active'));
  f.press('ShiftRight', 'Shift', {shiftKey:true, getModifierState:() => true});
  assert.ok(label('a')); assert.ok(label('! · 1'));
  assert.ok(f.doc.getElementById('reference-keyboard').children.flatMap(r => r.children).some(k => k.textContent === 'a'));
  f.emit(f.area, 'blur');
  assert.ok(label('a')); assert.ok(label('1 · !'));
  assert.equal(label('Caps Lock').classes.has('modifier-active'), false);
});

test('US punctuation and readable special-key labels cover the existing keyboard', () => {
  for (const [code, low, high] of [['Digit0','0',')'], ['Quote', "'", '"'], ['Backslash','\\','|'], ['Slash','/','?'], ['BracketLeft','[','{'], ['Equal','=','+']]) {
    assert.equal(keycapLabel(code, ''), `${low} · ${high}`);
    assert.equal(keycapLabel(code, '', true), `${high} · ${low}`);
  }
  assert.equal(historyKeyLabel({code:'ArrowLeft', key:'ArrowLeft'}), '← Left');
  assert.equal(historyKeyLabel({code:'ShiftRight', key:'Shift'}), 'Right Shift');
  assert.equal(historyKeyLabel({code:'Space', key:' '}), 'Space');
  assert.equal(historyKeyLabel({code:'KeyA', key:'Unidentified'}), 'A');
});
