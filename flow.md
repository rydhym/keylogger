# Flows
Setup: visible app -> disclosure -> Android Usage Access -> select apps -> explicit start.
Capture: active session -> user capture/pause/stop/sync -> query only active interval and selected apps -> derive estimated intervals -> metadata -> encrypt -> persist bounded outbox -> advance boundary after success.
Pair: operator token in dashboard -> server creates upload/read credentials -> browser creates key -> save private recovery -> transfer phone bundle -> Android validates and wraps secrets in Keystore.
Sync: user action -> capture if active -> upload encrypted envelopes -> remove each only on acknowledgement -> status. Failure retains queue.
Dashboard: import recovery -> read paginated ciphertext -> decrypt/validate locally -> render/filter/export. Lock clears credentials/history.
Delete: confirm remote purge; queued phone records can upload later. Revoke removes credentials and records. Local clear/unpair removes phone queue/pairing and stops session, without deleting remote records.
