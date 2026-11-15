# Sync

Cross-device sync with no account. A **12-word sync phrase** is the only credential; the server
(Convex) stores end-to-end-encrypted records it cannot read.

**Implementation status (current):** built and verified end to end on a phone and a tablet
emulator (join by words, settings and chapter state syncing, "Continue from <device>" prompt and
Jump in both directions across chapters and layouts). Code: Convex backend (`backend/convex/`),
`data/sync/` (phrase codec, keys/encryption, HLC, ledger snapshotter/applier, engine, Convex
transport, repository), `feature/sync/` (Settings → Sync, words + QR sheet, scanner, join sheet),
and the reader's portable-locator capture + offer prompt. Remaining: the production Convex
deployment (release builds ship with sync unavailable until its URL is set), and QR scanning has
only been exercised in code, not with a real camera. The Auth0 email/password login that preceded
this design was removed.

## Builds and versions

- **Deployments:** debug builds use the Convex dev deployment, release (and benchmark) the
  production one, via `BuildConfig.SYNC_CONVEX_URL` in `app/build.gradle.kts`. An empty URL shows
  "Sync isn't available in this build".
- **Protocol:** `backend/convex/sync.ts` has `PROTOCOL` / `MIN_CLIENT_PROTOCOL`; the app has
  `SyncProtocol.VERSION` / `MIN_SERVER` (`data/sync/SyncTransport.kt`). Every call carries the
  app's version; the server rejects older apps with `client_outdated`, and the app checks
  `sync:info` before syncing. Raising `MIN_CLIENT_PROTOCOL` makes older apps show "Update Yomu to
  keep syncing" (no link, by design) while keeping their changes locally; a server older than the
  app shows "Sync is temporarily unavailable".
- **Connection:** the app talks to Convex's native client (`MobileConvexClient`) directly. That
  client never recovers once its socket dies with the network, so the transport rebuilds it when
  Android reports the network back (or a call times out while disconnected) and the engine
  resubscribes. Calls time out after 15 s so offline actions report an error instead of spinning.
- **16 KB pages:** JNA (pulled in by Convex) is pinned to 5.19.1 because 5.14's x86_64 library
  isn't 16 KB-aligned.

## Pairing

- **Turn on sync** generates 128 random bits and shows them as 12 words from the standard BIP39
  English list (`assets/bip39_english.txt`, SHA-256
  `2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda`) plus a QR code
  (`yomu-sync:v1:<words>`, drawn with ZXing).
- **Join** on another device by scanning the QR (in-app CameraX + bundled ML Kit scanner, camera
  permission asked on open; no Play services needed) or typing the words. The BIP39 checksum rejects
  most typos offline; words autocomplete from their first four letters. Joining checks that the
  group exists (`sync:exists`) so a mistyped phrase never silently creates an empty group.
- The phrase is the account: whoever has it can read and write the data, and there is no
  recovery if every device and the phrase are lost. The app can show the words again from
  Settings → Sync. On-device, the entropy is stored wrapped by an Android Keystore key.

## Keys

HKDF-SHA256 over the 16-byte entropy (salt `yomu-sync`), one label per purpose:

| Derived | Label | Use |
|---|---|---|
| token | `yomu-sync/token/v1` | Sent with every Convex call. The server keeps only its SHA-256, which addresses the group. |
| encryption key | `yomu-sync/encrypt/v1` | AES-256-GCM for record contents. Never leaves the device. |
| naming key | `yomu-sync/name/v1` | HMAC of each logical record name → server key, so the server can't see which books exist. |

Each blob is `nonce ‖ ciphertext+tag` (base64url) with the server key as associated data, so the
server can't swap blobs between records. The plaintext carries the logical name and payload.

## Server (Convex)

`backend/convex/schema.ts`: `groups` (token hash, per-group `seq`) and `records` (group, server key,
blob or `null` for a deletion, HLC string, `seq`). Functions in `backend/convex/sync.ts`:

- `create` / `exists` – make or check a group.
- `push` – stores each record unless the server already has a higher HLC for it
  (last-writer-wins without decrypting), assigning the next `seq`.
- `pull` – records after a cursor, oldest first. Devices subscribe to it with their cursor, so
  changes from other devices arrive live over the Convex Android client.
- `destroy` – deletes the group's data in batches ("Delete synced data").

Setup: in `backend/`, run `npm install` then `npx convex dev` (logs in, creates the dev deployment,
and pushes functions on save); `npx convex deploy` creates/updates production, whose URL goes into
the release `SYNC_CONVEX_URL`.

## What syncs

Everything except the EPUB files and device-specific preferences. Books are matched across
devices by the EPUB's SHA-256; records for a book this device doesn't have are kept and applied
when that file is imported.

| Logical record | Contents |
|---|---|
| `meta/<sha>` | User-edited title, subtitle, author, description (written only on edit, so a fresh import can't overwrite an edit). Cover images are files and don't sync. |
| `progress/<sha>` | Portable locator, book/total progression, current chapter, started/finished/last-opened times, and the device label for the prompt. |
| `chapters/<sha>` | Read-chapter set and per-chapter progress. |
| `bookmark/<id>`, `highlight/<id>` | One record each (ids are UUIDs); deletions sync as tombstones. |
| `bookSettings/<sha>` | Per-book reader settings override, minus device-specific fields. |
| `session/<id>` | One reading session (book, start, seconds). Rollups (days, totals) are rebuilt from sessions on each device. |
| `settings/readerDefaults` | Global reader defaults, minus device-specific fields. |
| `settings/app` | Theme mode and colour style. |
| `settings/library` | Sort, group, view mode, cover crop. |
| `theme/<id>` | Saved custom reader themes. |
| `font/<family>` | Installed Google Fonts; each device downloads the files itself. |

**Device-specific, never synced:** brightness / system-brightness, extra-dim, keep screen on,
OLED black, app colour source (wallpaper/preset/custom accent), library grid columns, font file
paths, TOC/resource-weight caches, and file/cover paths.

## Change tracking and merge

- The dormant `sync_records` table becomes the sync ledger: per logical record, the last
  reconciled payload, its HLC, a dirty flag, and the book SHA. `sync_metadata` holds the cursor
  and pending progress offers.
- A tracker rebuilds the payloads of changed tables (Room invalidation + DataStore, debounced),
  diffs them against the ledger, stamps changes with a fresh HLC and pushes them. A record whose
  book is no longer on this device is left alone (deleting a local copy never deletes synced data).
- Incoming records with a higher HLC than the ledger are applied, and the ledger takes the remote
  payload so applying them doesn't echo back as a local change.
- Conflicts: last writer wins per record, by HLC.

## Picking up on another device

Page numbers depend on screen size, font and layout, so progress syncs as a **portable locator**:
Readium's `firstVisibleElementLocator()` (the first visible block element's CSS selector and its
surrounding text) merged with the chapter href and progression. Readium's `scrollToLocator` anchors
on that text first and falls back to progression, so another device opens on the page containing
the same paragraph whatever its screen, font size, or paged/scroll mode.

When newer progress arrives for a book:

- If this device has never opened the book, it simply adopts the synced position.
- Otherwise the local position is kept and the reader offers **"Continue from <device> at 42%?"**
  with a Jump action. Jumping adopts it; reading on locally makes the local position the newest.
