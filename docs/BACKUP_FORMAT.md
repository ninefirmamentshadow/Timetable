# Backup format

Timetable's backup is one JSON text file, encrypted with a passphrase. It is
written and read by the code in `app/src/main/java/com/sovereignops/timetable/backup/`
(the envelope, which knows nothing about bookings) and
`.../data/backup/` (the Timetable payload inside it). Nothing is sent anywhere:
the file is created through the system file picker and read back the same way.

## Envelope (version 2)

A JSON object with exactly these fields, no others:

| Field | Value |
| --- | --- |
| `format` | `"ops-vault-encrypted"` |
| `app` | application id; Timetable writes and accepts `"timetable"` |
| `envelope` | `2` |
| `kdf` | `"PBKDF2-SHA256"` |
| `cipher` | `"AES-256-GCM"` |
| `iterations` | PBKDF2 iteration count, integer, 100 000 to 2 000 000 (default written: 220 000) |
| `schema` | version of the payload inside, integer, at least 1 |
| `salt` | base64, 16 random bytes |
| `iv` | base64, 12 random bytes |
| `ct` | base64, AES-256-GCM ciphertext with the 16-byte tag appended |

Base64 is the standard alphabet with padding and must be canonical: decoding and
re-encoding has to reproduce the text exactly.

**Key.** PBKDF2 with HMAC-SHA-256 over the UTF-8 bytes of the passphrase, the
`salt`, and `iterations`, producing 32 bytes. Passphrases must be at least 8
characters; a passphrase with an unpaired surrogate is refused (its UTF-8
encoding would differ between implementations).

**Authentication.** The routing and KDF fields are bound to the GCM tag as
additional authenticated data. The AAD is the UTF-8 text of this compact JSON
object, fields in this order, no whitespace:

```
{"format":"ops-vault-encrypted","app":"<app>","envelope":2,"kdf":"PBKDF2-SHA256","cipher":"AES-256-GCM","iterations":<n>,"schema":<n>,"salt":"<salt as written>"}
```

So a file cannot be relabelled for another app or schema, or have its iteration
count lowered, without failing authentication.

**Reading order.** Structure and identity are checked before any key work: the
text must parse as an object, `format` must match, no unknown fields, `envelope`
2, the KDF and cipher names, `app` equal to the reader's own id (another app's
file is refused), `iterations` in range, salt and IV the right length. Only then
is a key derived. A wrong passphrase and a modified file both surface as the same
error, "Could not unlock the backup", so the message is no oracle. Nothing is
written to the database until decryption and validation have succeeded.

Files up to 32 MiB are read; anything larger is refused unread.

## Timetable payload (schema 1)

The decrypted bytes are UTF-8 JSON:

```json
{
  "exportedAt": "2026-10-21T09:30:15",
  "settings": { "travelBufferMinutes": 45, "defaultDurationMinutes": 60 },
  "bookings": [
    { "id": 1, "start": "2026-10-21T19:00", "durationMinutes": 60, "alias": "Alias A",
      "rateCents": 30000, "status": "CONFIRMED", "location": "", "screened": true,
      "depositReceived": false, "notes": "" }
  ],
  "availability": [
    { "id": 1, "day": 5, "startMinute": 1080, "endMinute": 1440 }
  ]
}
```

* `start` is a local date-time with no zone; the app never converts time zones.
* `status` is one of `INQUIRY`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`.
* `day` is the ISO weekday, 1 (Monday) to 7 (Sunday). `startMinute` and
  `endMinute` are minutes since midnight; `endMinute` 1440 means a window that
  runs to midnight.
* Unknown fields are rejected.

**Validation before restore.** Every booking and window must be one the app can
read (a known status, a positive duration, a non-negative rate, a real date,
minutes in range, ids positive and unique), text fields are bounded, and the two
settings must be in range (travel buffer 0 to 480, default duration 15 to 1440).
One bad row rejects the whole backup.

**Restore.** The caller is asked to confirm first. Bookings and availability are
then replaced in a single database transaction (all or nothing), and the two
settings are applied afterwards. A failure at any step, including a wrong
passphrase, leaves the existing data exactly as it was.

**Unreadable rows.** A stored row the app cannot read (see the "unreadable
records" banner) is left out of a backup, and the count is reported when the
backup is made. A restore replaces everything, so such rows are removed by it.

## Calendar export (`.ics`)

Separate from the backup and **not encrypted**. A standard iCalendar (RFC 5545)
file with one `VEVENT` per booking: `UID`, `DTSTAMP`, `DTSTART` and `DTEND` as
floating local times, `SUMMARY` (the alias), `LOCATION` (the location label, if
any), `STATUS` and `DESCRIPTION` (the status label). Rates, notes and the
screening and deposit flags are not exported. Text values are escaped (`\\`,
`\;`, `\,`, line breaks as `\n`) and lines are folded at 75 octets without
splitting a character. The app warns that the file is unencrypted before it is
written.
