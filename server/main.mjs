import { randomBytes } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { createApp } from './app.mjs';

const dataDir = path.resolve(process.env.CLEARTRAIL_DATA_DIR || '.local');
mkdirSync(dataDir, { recursive: true });
const tokenFile = path.join(dataDir, 'operator-token.txt');
if (!process.env.CLEARTRAIL_ADMIN_TOKEN && !existsSync(tokenFile)) {
  writeFileSync(tokenFile, randomBytes(32).toString('base64url'), { mode: 0o600, flag: 'wx' });
}
const adminToken = process.env.CLEARTRAIL_ADMIN_TOKEN || readFileSync(tokenFile, 'utf8').trim();
const port = Number(process.env.PORT || 4310);
const publicOrigin = process.env.CLEARTRAIL_PUBLIC_ORIGIN;
const app = createApp({ dataDir, adminToken, publicOrigin });
app.server.listen(port, '127.0.0.1', () => {
  console.log(`ClearTrail: http://127.0.0.1:${port}`);
  console.log(`Pairing requires the local operator token from ${tokenFile}. Keep it private.`);
});
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => { await app.close(); process.exit(0); });
