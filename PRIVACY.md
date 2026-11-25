# Open Reader Privacy Policy

**Effective date:** 26 November 2026

Open Reader is an offline-first EPUB reader developed by IT Expert 120. Privacy questions can be sent to [itexpert120@outlook.com](mailto:itexpert120@outlook.com).

## Data stored on your device

Open Reader stores imported EPUB files, covers, book metadata, reading positions, chapter progress, bookmarks, highlights and their notes, reading statistics, installed fonts, and app settings in private app storage. Open Reader has no user accounts, advertising, analytics, or tracking SDKs. Books are never uploaded, and reading history leaves the device only through optional sync, described below.

Android cloud backup and device-to-device transfer are disabled for Open Reader's private data.

## Optional network features

Open Reader uses the network only following an explicit user action:

- **Dictionary lookup:** When you choose **Look up**, Open Reader sends the selected word and the language code of the text it was selected in over HTTPS to [FreeDictionaryAPI](https://freedictionaryapi.com/) to retrieve a definition. No book, reading history, account identifier, advertising identifier, or precise location is included in the request.
- **Wikipedia:** Only when you open the **Wikipedia** tab of the look-up sheet (or look up a short phrase, which opens on that tab), Open Reader sends the selected word or phrase and a language code over HTTPS to Wikipedia (`<language>.wikipedia.org`, operated by the Wikimedia Foundation) to fetch the article summary, and fetches the article's thumbnail image, if it has one, from `upload.wikimedia.org`. The request identifies the app only as `OpenReader/<version> (Android)`; no book, reading history, or identifier is included. **Read on Wikipedia** opens the article in your browser. Wikipedia's handling is governed by the [Wikimedia Foundation Privacy Policy](https://foundation.wikimedia.org/wiki/Policy:Privacy_policy).
- **Translate and other apps' text actions:** Open Reader has no translation service of its own. When you choose **Translate** in the look-up sheet, or another app's action (such as Translate or a dictionary app) from the text-selection menu, the selected text is handed on the device to the app you picked, read-only. What that app does with it, including whether it sends it to a network service, is governed by that app's own policy.
- **Sharing and exporting:** When you share selected text, or export a book's highlights and notes, Open Reader opens Android's share sheet and the content goes only to the app you choose. Importing highlights reads only the file you pick.
- **Sync (optional, off by default):** If you turn on sync in Settings → Sync, Open Reader keeps your reading data (each book's title, subtitle, author, description and content hash, reading positions and chapter progress, bookmarks, highlights and notes, collections and shelf statuses, reading sessions, synced settings, custom reading themes, and the names of installed fonts) in step across your devices through a server operated for Open Reader on [Convex](https://www.convex.dev/). There is no account: a 12-word sync phrase, created on your device, is the only credential. Every record is encrypted on your device with keys derived from that phrase before it is sent, so the server stores only ciphertext it cannot read, addressed by a hash of a token derived from the phrase; your EPUB files and covers are never uploaded. Sync connects only while Open Reader is open. Anyone with your sync phrase can read and change the synced data, so keep it private. Turning sync off on a device stops it there; you can also delete the synced data from the server in Settings → Sync.
- **Google Fonts:** When you choose to install a font, Open Reader sends the requested font-family name over HTTPS to Google Fonts and downloads the selected font files from Google-hosted servers.
- **Web search:** When you choose **Search web**, Open Reader opens a Google search in your external browser. Open Reader does not receive the resulting page or search history.
- **Pronunciation:** Pronunciation is sent to the text-to-speech service configured on your device. Depending on that provider and your device settings, speech synthesis may be performed locally or by the provider's network service.

External services receive ordinary connection information such as an IP address and user agent. Open Reader does not operate those services and does not control independently retained server logs. Their handling is governed by their own terms and privacy policies, including [Google's Privacy Policy](https://policies.google.com/privacy).

## Sharing and sale

Open Reader does not sell personal data. It does not share books, reading history, bookmarks, highlights, or statistics. The optional, user-initiated transfers described above are limited to providing the feature requested at that moment; sync data is stored only in encrypted form.

## Security

Network requests initiated by Open Reader use HTTPS. Local information is held in Android app-private storage and is not included in Android cloud backup or device transfer. No security measure can guarantee absolute protection, particularly on a compromised device.

## Retention and deletion

Local library data remains until you delete it:

- Removing a book deletes its app-private EPUB, cover, progress, bookmarks, and highlights.
- Installed fonts can be removed from Open Reader's font library.
- Clearing Open Reader's storage or uninstalling the app deletes the remaining local database, preferences, statistics, and private files.

Synced data stays on the sync server until you delete it from Settings → Sync. Open Reader cannot delete information independently retained by external dictionary, encyclopedia, font, browser, translation, or text-to-speech providers. Contact the applicable provider for its retention and deletion practices.

## Children

Open Reader is not specifically directed to children and does not provide or distribute books. Users control the EPUB files they import. If Open Reader is offered to an audience that includes children, the Play Console target-audience declaration and applicable family requirements must be configured accordingly.

## Changes

Material changes to this policy will be published here and reflected in the in-app policy with an updated effective date.
