const encoder = new TextEncoder();
export function toBase64(bytes) {
  let text = '';
  for (const byte of new Uint8Array(bytes)) text += String.fromCharCode(byte);
  return btoa(text);
}
export function fromBase64(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(value)) throw new Error('Invalid base64');
  const result = Uint8Array.from(atob(value), c => c.charCodeAt(0));
  if (toBase64(result) !== value) throw new Error('Invalid base64 encoding');
  return result;
}
export function newKey() { return toBase64(crypto.getRandomValues(new Uint8Array(32))); }
async function importKey(key) {
  const bytes = fromBase64(key);
  if (bytes.length !== 32) throw new Error('A 256-bit data key is required');
  return crypto.subtle.importKey('raw', bytes, 'AES-GCM', false, ['encrypt', 'decrypt']);
}
const aad = (deviceId, id) => encoder.encode(`cleartrail:v1:${deviceId}:${id}`);
export async function encryptRecord(key, deviceId, id, payload) {
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce, additionalData: aad(deviceId, id), tagLength: 128 }, await importKey(key), encoder.encode(JSON.stringify(payload)));
  if (ciphertext.byteLength > 65_536) throw new Error('Record exceeds size limit');
  return { version: 1, nonce: toBase64(nonce), ciphertext: toBase64(ciphertext) };
}
export async function decryptRecord(key, deviceId, record) {
  if (record.version !== 1) throw new Error('Unsupported protocol version');
  const nonce = fromBase64(record.nonce);
  if (nonce.length !== 12) throw new Error('Invalid nonce');
  const bytes = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce, additionalData: aad(deviceId, record.id), tagLength: 128 }, await importKey(key), fromBase64(record.ciphertext));
  return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
}
