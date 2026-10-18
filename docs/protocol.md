# Encrypted protocol v1

Same-origin HTTPS API. Localhost HTTP is for development only. No cookies/CORS. Authorization header: Bearer credential. Device and record IDs are lowercase UUIDs.

| Request | Credential | Result |
| --- | --- | --- |
| POST /api/devices | Operator | New device ID, random upload token and random read token |
| PUT /api/devices/:deviceId/records/:recordId | Upload | Store envelope, or acknowledge identical retry |
| GET /api/devices/:deviceId/records?after=:sequence | Read | Up to 200 envelopes and nextCursor |
| DELETE /api/devices/:deviceId/records | Read | Purge history, preserve pairing |
| DELETE /api/devices/:deviceId | Read | Revoke device and delete history |

Envelope fields are exactly `version` (1), `nonce` (canonical Base64, 12 bytes), `ciphertext` (canonical Base64, 16–65536 bytes including GCM tag). Device ID and record ID stay in URL and authenticated additional data:

```text
cleartrail:v1:<deviceId>:<recordId>
```

Cipher: AES-256-GCM, 128-bit tag appended to ciphertext. Fresh random nonce for each new encryption. Exact retries reuse the stored encrypted envelope and record ID. Nonce reuse across different records is rejected server-side. HTTP 409 indicates a conflict, never overwrite.

Decrypted payload fields are exactly:

```json
{
  "schemaVersion": 1,
  "sessionId": "00000000-0000-4000-8000-000000000001",
  "from": 1000,
  "to": 2000,
  "battery": 80,
  "apps": [
    {
      "packageName": "com.whatsapp",
      "intervals": [{"start": 1100, "end": 1900, "estimated": true}]
    }
  ]
}
```

Timestamps are epoch milliseconds. Capture window <=24 hours. Battery -1 means unavailable; otherwise 0–100. At most three known packages and 512 nonoverlapping sorted intervals across them. Unknown fields and content-bearing fields are rejected by the browser. The phone constructs only this schema. The server cannot inspect encrypted payloads; it validates envelopes only.

The read API adds `id`, `sequence`, `received`, and `version` to each envelope. `received` is server arrival time, not device capture time. Pagination is by monotonically increasing server sequence, not client timestamps.

Key/credential bundles are documented in web/model.js and Android Storage.kt. Exported dashboard recovery files are intentionally sensitive. There is no server-side key recovery.
