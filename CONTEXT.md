# Open Reader

Open Reader helps people keep, navigate, and understand a personal library while preserving the continuity of reading each book.

## Language

### Library

**Book**:
One EPUB the person keeps in the library, identified by its content hash (sha256); importing the same file again is a **duplicate**, not a second book.
_Avoid_: Title, file, publication (Readium's in-memory form)

**Library**:
Every book on this device, browsed through **library tabs**.
_Avoid_: Bookshelf

**Shelf status**:
The one status a book shows everywhere: To read, Reading, Finished, On hold, Dropped. To read / Reading / Finished follow book progress; On hold and Dropped are a **manual status** the person sets, and a manual status always wins (including in Statistics).
_Avoid_: Reading state + manual status as separate ideas; "Unread", "Not started" (the label is "To read")

**Manual status**:
On hold or Dropped, set by hand from Organize or Book details; clearing it returns the book to its progress-derived shelf status.

**Collection**:
A named group of books the person makes; a book can be in many. Sync carries a book's manual status and collection memberships together under the legacy wire key `shelf/<sha>`.
_Avoid_: Shelf (except as that wire key), group, tag

**Library tab**:
A view of the library: All, a shelf status, or a collection. Order, hidden tabs and the last tab are device-local.

**Organize**:
Setting manual status and collection membership for one or many books.

### Reading

**Reading experience**:
An active, book-scoped interaction spanning the attempt to open a book, navigation, lookup, search, annotations, progress, and reading-time attribution. It ends when the reader leaves the book or the opening attempt is abandoned.
_Avoid_: Reader screen, open-book session

**Reading session**:
A foreground interval within a reading experience that accrues reading time for its book. Opening, failure, background, and external-app time are excluded; in-reader search, lookup, and annotation time are included.
_Avoid_: Reading experience, app session

**Reading position**:
Where the person is in a book: the engine's locator plus the derived chapter, chapter progress, pages left and book progress. Resume returns to it.
_Avoid_: Bookmark (a saved, named position), cursor

**Chapter**:
A logical section from the book's table of contents. Several chapters can start in the same **resource** (spine item, one XHTML file); those **shared-start** chapters advance together.
_Avoid_: Resource, page, spine item (when you mean a chapter)

**Chapter read**:
Whether a chapter counts as read: read by reaching its end, or marked read by hand (including "mark to here"). One rule decides it for Book details, Statistics and sync.

**Book progress**:
Progress through the whole book, weighted by chapter/resource length; 100% makes the book Finished unless a manual status is set.
_Avoid_: Total progression (Readium's raw value)

**Annotation**:
A bookmark or a highlight.

**Word lookup**:
Looking up a single selected word in the dictionary; a multi-word selection goes to web search instead.

**Reading defaults**:
The global reader settings; a book can carry a **per-book override** on top.

### Sync

**Sync group**:
The devices sharing one **sync phrase** (12 BIP39 words). The phrase is the only credential; the server stores ciphertext.

**Sync record**:
One encrypted, HLC-stamped value on the server (a book's progress, a chapter's read state, a collection, settings…). Each **record kind** knows how to snapshot local data into records and apply incoming records.

**Ledger**:
This device's last known state of every sync record, used to tell local changes from synced ones.

**Tombstone**:
A record marking a deletion. A book removed from one device keeps its synced data everywhere else (no tombstone for book-bound records then).

**First contact**:
The first sync of a device that already has data; merges by each record's own timestamps instead of overwriting.

**Progress offer**:
"Continue from <device>": a newer reading position from another device, offered rather than applied.

**Awaiting book**:
A synced record for a book this device doesn't have yet; applied once the same book (same hash) is imported.

**Synced setting / device-local setting**:
Whether a setting follows the sync group or stays on this device (e.g. font size, columns, last tab are device-local).
