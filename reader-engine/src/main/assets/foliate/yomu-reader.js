// Open Reader's glue around foliate-js: the book is read file by file from the app (never as one
// big blob), styled from the app's reader settings, and every position is reported to Kotlin.
// Page turns are foliate's own (its finger-following swipe and slide).
//
// The page loads before the app knows which book (so it can be made ahead of a tap) and reports
// `shell-ready`; the app then calls yomuStart({ start, settings }). `start` is { cfi } | { href } |
// { index, fraction }; `settings` is described at readerCSS. Later changes come through
// yomuApplySettings / yomuGoTo.
import './view.js'
import { EPUB } from './epub.js'
import { Overlayer } from './overlayer.js'
import { textWalker } from './text-walker.js'

const host = globalThis.YomuHost
const t0 = performance.now()
const report = (name, detail = {}) => {
    const payload = JSON.stringify({ ...detail, ms: Math.round(performance.now() - t0) })
    if (host) host.event(name, payload)
    else console.log(name, payload)
}

const params = new URLSearchParams(location.search)
let settings = {}

const bookUrl = name => '/book/' + name.split('/').map(encodeURIComponent).join('/')

const makeLoader = async () => {
    const sizes = await (await fetch('/book/.sizes')).json()
    const exists = name => Object.hasOwn(sizes, name)
    const get = async name => {
        if (!exists(name)) return null
        const res = await fetch(bookUrl(name))
        return res.ok ? res : null
    }
    return {
        loadText: async name => (await get(name))?.text() ?? null,
        loadBlob: async name => (await get(name))?.blob() ?? null,
        getSize: name => sizes[name] ?? 0,
    }
}

const SYSTEM_COLORS = `
    html {
        color-scheme: light dark;
        --page: #FFFFFF;
        --ink: #16191C;
        --link: #2F5BB7;
    }
    @media (prefers-color-scheme: dark) {
        html {
            --page: #1A1E22;
            --ink: #EEF1F3;
            --link: #9DB8F0;
            background: var(--page) !important;
            color: var(--ink) !important;
        }
        body { background: none !important; color: inherit !important; }
        body * {
            color: inherit !important;
            background-color: transparent !important;
            border-color: currentColor !important;
        }
        a:any-link { color: var(--link) !important; }
    }
    html { background: var(--page); color: var(--ink); }
    a:any-link { color: var(--link); }
`

/**
 * The page's CSS. foliate's rendering keeps the book's own layout; on top of it the reader's
 * settings force what the reader chose:
 *   colors { page, ink, link, dark } — forced over the book's (null: follow the system scheme)
 *   font { family, faces: [{ src, style }] } — forced family (null: the book's fonts)
 *   fontScale, lineHeight (null: 1.5, the book may override), paragraphSpacing (em, null: book's)
 *   align: 'publisher' | 'start' | 'justify'
 */
const readerCSS = s => {
    const c = s.colors
    const colors = c ? `
        html {
            color-scheme: ${c.dark ? 'dark' : 'light'};
            background: ${c.page} !important;
            color: ${c.ink} !important;
        }
        body { background: none !important; color: inherit !important; }
        body * {
            color: inherit !important;
            background-color: transparent !important;
            border-color: currentColor !important;
        }
        a:any-link { color: ${c.link} !important; }
    ` : SYSTEM_COLORS
    const faces = (s.font?.faces ?? []).map(f => `
        @font-face { font-family: "${s.font.family}"; font-style: ${f.style ?? 'normal'};
            font-weight: ${f.weight ?? 'normal'}; src: url("${f.src}"); }`).join('')
    const font = s.font ? `
        body, body *:not(code):not(pre):not(kbd):not(samp):not(tt) {
            font-family: "${s.font.family}", serif !important;
        }` : ''
    const lineHeight = s.lineHeight ?? 1.5
    const lineImportant = s.lineHeight != null ? '!important' : ''
    const align = s.align === 'start' ? 'start !important' : 'justify'
    const hyphens = s.align === 'start' ? 'manual' : 'auto'
    return `
        @namespace epub "http://www.idpf.org/2007/ops";
        ${faces}
        ${colors}
        html {
            font-size: ${16 * (s.fontScale ?? 1)}px !important;
            line-height: ${lineHeight} ${lineImportant};
            orphans: 2;
            widows: 2;
            hanging-punctuation: allow-end last;
        }
        ${font}
        p, li, blockquote, dd {
            line-height: ${lineHeight} ${lineImportant};
            text-align: ${align};
            hyphens: ${hyphens};
        }
        ${s.paragraphSpacing != null ? `p { margin-top: 0 !important; margin-bottom: ${s.paragraphSpacing}em !important; }` : ''}
        [align="left"] { text-align: left; }
        [align="right"] { text-align: right; }
        [align="center"] { text-align: center; }
        [align="justify"] { text-align: justify; }
        h1, h2, h3, h4, h5, h6, hgroup { text-wrap: balance; }
        pre { white-space: pre-wrap !important; }
        aside[epub|type~="endnote"],
        aside[epub|type~="footnote"],
        aside[epub|type~="note"],
        aside[epub|type~="rearnote"] {
            display: none;
        }
    `
}

const configure = renderer => {
    renderer.setAttribute('flow', settings.flow === 'scrolled' ? 'scrolled' : 'paginated')
    // foliate's slide, unless the system has animations off.
    if (settings.animated === false) renderer.removeAttribute('animated')
    else renderer.setAttribute('animated', '')
    renderer.setAttribute('gap', settings.gap ?? '6%')
    renderer.setAttribute('max-column-count', '1')
    renderer.setStyles(readerCSS(settings))
}

const applyPageColor = () => {
    document.documentElement.style.background = settings.colors?.page ?? ''
}
applyPageColor()

const view = document.createElement('foliate-view')
document.body.append(view)

let firstPage = true
view.addEventListener('load', ({ detail: { doc, index } }) => {
    report('section-loaded', { index })
    if (params.has('csptest')) {
        // A book script, inline and from a blob: URL; the CSP must stop both.
        globalThis.__bookScripts = 0
        const inline = doc.createElement('script')
        inline.textContent = 'parent.__bookScripts++'
        doc.head.append(inline)
        const external = doc.createElement('script')
        external.src = URL.createObjectURL(new Blob(['parent.__bookScripts++'], { type: 'text/javascript' }))
        doc.head.append(external)
        setTimeout(() => report('csp-test', { bookScriptsRan: globalThis.__bookScripts }), 500)
    }
    // Paged: the outer thirds turn the page (the app draws the turn) and the middle is the app's
    // menu. Scrolled, or with tap turns off, any tap is the menu.
    doc.addEventListener('click', e => {
        if (doc.getSelection()?.type === 'Range') return
        if (e.target.closest?.('a[href]')) return
        const paged = settings.flow !== 'scrolled' && settings.tapTurns !== false
        // The chapter frame is wider than the screen and scrolled from outside, so use screen x.
        const x = e.screenX
        // foliate's own click listener (which finds a tapped highlight) runs after this one, so
        // decide once it has: a tap on a highlight is the highlight's.
        highlightTapped = false
        setTimeout(() => {
            if (highlightTapped) return
            if (paged && x < innerWidth / 3) view.prev()
            else if (paged && x > innerWidth * 2 / 3) view.next()
            else report('center-tap')
        })
    })
})

// Positions count only once the start position is on screen: before its chapter loads, foliate
// reports from its empty frame (one page, no progress).
let shown = false
view.addEventListener('relocate', ({ detail }) => {
    if (shown) reportLocation(detail)
})

const reportLocation = detail => {
    if (firstPage) {
        firstPage = false
        // Two frames: the first page has been laid out and painted.
        requestAnimationFrame(() => requestAnimationFrame(() => report('first-page')))
    }
    const r = view.renderer
    const scrolled = r.scrolled
    // Progress within the chapter, as foliate measures it: paged has a blank padding page at
    // each end of a chapter, so text pages are 1 .. pages - 2.
    const textPages = Math.max(1, r.pages - 2)
    const page = Math.min(textPages - 1, Math.max(0, r.page - 1))
    const fraction = scrolled
        ? (r.viewSize > 0 ? r.start / r.viewSize : 0)
        : (textPages > 1 ? page / (textPages - 1) : 0)
    // How far through the scrollable range: 1 at the chapter's end (`fraction` measures the top of
    // the screen, so it never gets there).
    const scrollRange = r.viewSize - r.size
    const measured = scrolled ? (scrollRange > 1 ? r.start / scrollRange : 1) : fraction
    report('relocate', {
        index: detail.section?.current,
        fraction: Math.min(1, Math.max(0, fraction)),
        measured: Math.min(1, Math.max(0, measured)),
        total: detail.fraction,
        cfi: detail.cfi,
        page,
        pages: textPages,
        scrolled,
        title: detail.tocItem?.label ?? null,
    })
}

// --- Selection and highlights ---

let highlightTapped = false
let highlights = new Map() // cfi -> { id, cfi, color }

view.addEventListener('draw-annotation', ({ detail: { draw, annotation } }) => {
    draw(Overlayer.highlight, { color: annotation.color })
})
view.addEventListener('show-annotation', ({ detail: { value } }) => {
    const highlight = highlights.get(value)
    if (!highlight) return
    highlightTapped = true
    report('highlight-tap', { id: highlight.id })
})
// A chapter's highlights are drawn when it loads (foliate draws only into loaded chapters).
view.addEventListener('create-overlay', ({ detail: { index } }) => {
    for (const h of highlights.values()) {
        if (h.index === index) view.addAnnotation({ value: h.cfi, color: h.color }).catch(() => {})
    }
})

// Highlights made under the Readium engine carry their text instead of a CFI. Each is found by its
// text when its chapter loads, then drawn (and tapped) like any other, under the CFI found.
let textHighlights = [] // [{ id, index, text: { before, highlight, after }, color }]
const anchorTextHighlights = (index, doc) => textHighlights.flatMap(h => {
    if (h.index !== index) return []
    const range = findText(doc, h.text)
    return range ? [{ id: h.id, cfi: view.getCFI(index, range), color: h.color, index }] : []
})
// 'load' comes before the chapter's overlay is made (create-overlay), which then draws these.
view.addEventListener('load', ({ detail: { doc, index } }) => {
    for (const h of anchorTextHighlights(index, doc)) highlights.set(h.cfi, h)
})

/**
 * Replaces the drawn highlights with [list]: [{ id, cfi, color }], or [{ id, index, text, color }]
 * for one positioned by its text.
 */
globalThis.yomuSetHighlights = list => {
    textHighlights = list.filter(h => !h.cfi && h.text?.highlight)
    const next = new Map(list.filter(h => h.cfi).map(h => [h.cfi, h]))
    for (const { index, doc } of view.renderer?.getContents() ?? []) {
        for (const h of anchorTextHighlights(index, doc)) next.set(h.cfi, h)
    }
    for (const cfi of highlights.keys()) {
        if (!next.has(cfi)) view.deleteAnnotation({ value: cfi }).catch(() => {})
    }
    for (const h of next.values()) {
        try {
            h.index = view.resolveNavigation(h.cfi)?.index
        } catch {
            h.index = null
        }
        const old = highlights.get(h.cfi)
        if (!old || old.color !== h.color) view.addAnnotation({ value: h.cfi, color: h.color }).catch(() => {})
    }
    highlights = next
}

const textAround = (range, length = 40) => {
    const before = range.startContainer.nodeType === Node.TEXT_NODE
        ? range.startContainer.data.slice(Math.max(0, range.startOffset - length), range.startOffset) : ''
    const after = range.endContainer.nodeType === Node.TEXT_NODE
        ? range.endContainer.data.slice(range.endOffset, range.endOffset + length) : ''
    return { before, after }
}

/** The current text selection: { index, cfi, text, before, after, fraction }, or null. */
globalThis.yomuSelection = () => {
    for (const { doc, index } of view.renderer?.getContents() ?? []) {
        const sel = doc.getSelection()
        if (!sel || sel.rangeCount === 0 || sel.isCollapsed) continue
        const range = sel.getRangeAt(0)
        return {
            index,
            cfi: view.getCFI(index, range),
            text: sel.toString(),
            ...textAround(range),
        }
    }
    return null
}

/**
 * The position on screen in a form other layouts can find again: { index, cfi, before, highlight,
 * after } — the text at the top of the screen as it stands in the book (whitespace untouched), for
 * an engine that anchors on text rather than CFIs. Null before the book shows.
 */
globalThis.yomuPortable = () => {
    const location = view.lastLocation
    const range = location?.range
    if (!shown || !range || !location.cfi) return null
    const index = view.renderer?.getContents()?.[0]?.index
    const doc = range.startContainer.ownerDocument
    const visible = range.toString()
    const lead = visible.length - visible.trimStart().length
    const highlight = visible.slice(lead, lead + 80).trimEnd()
    if (!highlight || !doc?.body) return { index, cfi: location.cfi }
    const preceding = doc.createRange()
    preceding.setStart(doc.body, 0)
    preceding.setEnd(range.startContainer, range.startOffset)
    const before = (preceding.toString() + visible.slice(0, lead)).slice(-32)
    const after = visible.slice(lead + highlight.length, lead + highlight.length + 32)
    return { index, cfi: location.cfi, before, highlight, after }
}

/** Whether the page still shows a chapter: { shown, index, content }. */
globalThis.yomuAlive = () => {
    const contents = view.renderer?.getContents()?.[0]
    const body = contents?.doc?.body
    return {
        shown,
        index: contents?.index ?? -1,
        content: !!body && (!!body.textContent.trim() || !!body.querySelector('img, svg, image, video')),
    }
}

globalThis.yomuClearSelection = () => {
    for (const { doc } of view.renderer?.getContents() ?? []) doc.getSelection()?.removeAllRanges()
}

// --- Footnotes ---
// A tapped note reference shows the note (reported as `footnote` { html }) instead of jumping to
// it. Detection follows foliate's footnotes.js: marked up as a note reference, or a superscript
// link that isn't a back-link. When the note can't be extracted, the link is followed as usual.

const OPS = 'http://www.idpf.org/2007/ops'
const epubTypes = el => new Set(el?.getAttributeNS?.(OPS, 'type')?.split(/\s+/))
const roles = el => new Set(el?.getAttribute?.('role')?.split(/\s+/))
const isSuper = el => {
    if (!el?.matches) return false
    if (el.matches('sup')) return true
    const align = el.ownerDocument.defaultView?.getComputedStyle(el).verticalAlign ?? ''
    return align === 'super' || align === 'top' || align === 'text-top' || /^\d/.test(align)
}
const isNoteReference = a => {
    const types = epubTypes(a)
    const r = roles(a)
    if (['noteref', 'biblioref', 'glossref'].some(t => types.has(t))) return true
    if (['doc-noteref', 'doc-biblioref', 'doc-glossref'].some(t => r.has(t))) return true
    if (types.has('backlink') || r.has('doc-backlink')) return false
    return isSuper(a) || (a.children.length === 1 && isSuper(a.children[0])) || isSuper(a.parentElement)
}
const isBacklink = a => epubTypes(a).has('backlink') || roles(a).has('doc-backlink')
const INLINE = 'a, span, sup, sub, em, strong, i, b, small, big'
// Longer than this, the "note" is more likely a chapter than a footnote.
const MAX_NOTE_LENGTH = 4000

/** The HTML of the note [href] points at, or null. */
const noteContent = async href => {
    const book = await loadBook()
    const target = book.resolveHref(href)
    if (!target || target.index < 0 || !book.sections[target.index]?.createDocument) return null
    const doc = await book.sections[target.index].createDocument()
    let el = target.anchor(doc)
    if (el?.nodeType !== Node.ELEMENT_NODE) return null
    // The id is often on an inline label inside the note: take the block around it.
    while (el.matches(INLINE) && el.parentElement && el.parentElement !== doc.body) el = el.parentElement
    if (el === doc.body || el === doc.documentElement) return null
    const note = el.cloneNode(true)
    for (const link of note.querySelectorAll('a[href]')) if (isBacklink(link)) link.remove()
    const text = note.textContent.trim()
    if (!text || text.length > MAX_NOTE_LENGTH) return null
    return note.matches('li, aside, dd') ? note.innerHTML.trim() : note.outerHTML
}

view.addEventListener('link', e => {
    const { a, href } = e.detail
    if (!href?.includes('#') || !isNoteReference(a)) return
    e.preventDefault()
    noteContent(href)
        .then(html => html ? report('footnote', { html }) : view.goTo(href))
        .catch(() => view.goTo(href))
})

// --- In-book search ---
// foliate's own search opens every chapter with a locale-aware segmenter and is far too slow for a
// long book. The app scans the chapter files first (SearchText.kt) and passes only the chapters that
// can match; here they're matched with the same folding: no case, no diacritics, one space for any
// run of whitespace.

const foldChar = ch => ch.normalize('NFD').replace(/[\p{M}\p{Cf}]/gu, '').toLowerCase()

/** strs folded into one string, with each folded unit's source: string index, offset, length. */
const fold = strs => {
    const parts = []
    const node = [], offset = [], length = []
    let space = false
    const push = (text, n, i, len) => {
        parts.push(text)
        for (let k = 0; k < text.length; k++) {
            node.push(n)
            offset.push(i)
            length.push(len)
        }
    }
    strs.forEach((s, n) => {
        for (let i = 0; i < s.length;) {
            const code = s.codePointAt(i)
            const len = code > 0xFFFF ? 2 : 1
            if (code < 128) {
                if (code === 32 || (code >= 9 && code <= 13)) {
                    if (!space) push(' ', n, i, len)
                    space = true
                } else {
                    push(code >= 65 && code <= 90 ? String.fromCharCode(code + 32) : s[i], n, i, len)
                    space = false
                }
            } else {
                const ch = s.slice(i, i + len)
                if (/\s/u.test(ch)) {
                    if (!space) push(' ', n, i, len)
                    space = true
                } else {
                    const folded = foldChar(ch)
                    if (folded) {
                        push(folded, n, i, len)
                        space = false
                    }
                }
            }
            i += len
        }
    })
    return { text: parts.join(''), node, offset, length }
}

const EXCERPT = 50
const squash = s => s.replace(/\s+/g, ' ')
// Whole words of the context around a match, at most EXCERPT characters.
const excerptBefore = s => {
    const text = squash(s).trimStart()
    return text.length <= EXCERPT ? text : '…' + text.slice(-EXCERPT).replace(/^\S*\s/, '')
}
const excerptAfter = s => {
    const text = squash(s).trimEnd()
    return text.length <= EXCERPT ? text : text.slice(0, EXCERPT).replace(/\s\S*$/, '') + '…'
}

const BLOCK = new Set(['p', 'div', 'li', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'td', 'th', 'tr', 'dt', 'dd',
    'blockquote', 'section', 'article', 'aside', 'header', 'footer', 'nav', 'pre', 'figcaption', 'body'])
const blockOf = node => {
    let el = node.parentElement
    while (el && !BLOCK.has(el.localName)) el = el.parentElement
    return el
}

/** Up to [max] matches of [needle] (folded) in [doc], section [index]. */
const searchDocument = (doc, index, needle, max) => {
    const hits = []
    const matches = textWalker(doc, function* (strs, makeRange) {
        const { text, node, offset, length } = fold(strs)
        const range = (a, b) => makeRange(node[a], offset[a], node[b], offset[b] + length[b])
        // The context shown with a hit breaks lines between blocks (matching doesn't: like the
        // app's scan, it reads the text nodes back to back).
        let context = null
        const starts = []
        const contextOf = () => {
            const parts = []
            let total = 0, lastBlock
            strs.forEach((s, n) => {
                const block = blockOf(makeRange(n, 0, n, 0).startContainer)
                if (n > 0 && block !== lastBlock) {
                    parts.push('\n')
                    total++
                }
                lastBlock = block
                starts.push(total)
                parts.push(s)
                total += s.length
            })
            return parts.join('')
        }
        for (let at = text.indexOf(needle); at > -1; at = text.indexOf(needle, at + needle.length)) {
            const last = at + needle.length - 1
            const hitRange = range(at, last)
            context ??= contextOf()
            const start = starts[node[at]] + offset[at]
            const end = starts[node[last]] + offset[last] + length[last]
            yield {
                range: hitRange,
                progression: text.length ? at / text.length : 0,
                before: excerptBefore(context.slice(Math.max(0, start - EXCERPT * 3), start)),
                match: context.slice(start, end),
                after: excerptAfter(context.slice(end, end + EXCERPT * 3)),
            }
        }
    })
    for (const { range, ...hit } of matches) {
        hits.push({
            ...hit,
            index,
            cfi: view.getCFI(index, range),
            title: view.getProgressOf(index, range)?.tocItem?.label ?? null,
        })
        if (hits.length >= max) break
    }
    return hits
}

let searchToken = 0

/**
 * Searches chapters [sections] (in order) for [query], reporting `search-hits` { id, hits } per
 * chapter with matches and `search-done` { id } at the end, after [limit] hits, or once cancelled.
 */
globalThis.yomuSearch = async ({ id, query, sections, limit }) => {
    const token = ++searchToken
    const needle = fold([query]).text.trim()
    let found = 0
    try {
        const book = await loadBook()
        for (const index of needle ? sections : []) {
            if (token !== searchToken || found >= limit) break
            const section = book.sections[index]
            if (!section?.createDocument) continue
            const doc = await section.createDocument()
            if (token !== searchToken) break
            const hits = searchDocument(doc, index, needle, limit - found)
            found += hits.length
            if (hits.length) report('search-hits', { id, hits })
        }
    } catch (e) {
        report('error', { message: 'search: ' + String(e?.stack ?? e) })
    }
    report('search-done', { id })
}

globalThis.yomuCancelSearch = () => {
    searchToken++
}

// Search hits are underlined in whichever chapter is on screen. Keyed with foliate's search prefix,
// so a tap on one isn't reported as a tap on an annotation.
const SEARCH_KEY = 'foliate-search:'
const SEARCH_COLOR = '#E7C75B'
let searchHits = new Map() // section index -> [cfi]

const drawSearchHits = ({ index, overlayer, doc }) => {
    if (!overlayer || !doc) return
    for (const cfi of searchHits.get(index) ?? []) {
        try {
            const range = view.resolveNavigation(cfi).anchor(doc)
            overlayer.add(SEARCH_KEY + cfi, range, Overlayer.underline, { color: SEARCH_COLOR, width: 3 })
        } catch {
            // A hit that no longer resolves is simply not drawn.
        }
    }
}

/** Underlines [cfis] (replacing the previous set; [] clears them). */
globalThis.yomuSetSearchHits = cfis => {
    for (const { index, overlayer } of view.renderer?.getContents() ?? []) {
        for (const cfi of searchHits.get(index) ?? []) overlayer?.remove(SEARCH_KEY + cfi)
    }
    searchHits = new Map()
    for (const cfi of cfis) {
        let index
        try {
            index = view.resolveNavigation(cfi)?.index
        } catch {
            continue
        }
        if (index == null) continue
        if (!searchHits.has(index)) searchHits.set(index, [])
        searchHits.get(index).push(cfi)
    }
    for (const content of view.renderer?.getContents() ?? []) drawSearchHits(content)
}

// The overlay is attached right after this event, so draw once it is.
view.addEventListener('create-overlay', ({ detail: { index } }) => Promise.resolve().then(() => {
    for (const content of view.renderer?.getContents() ?? []) {
        if (content.index === index) drawSearchHits(content)
    }
}))

// --- Scrolled flow: continuing into the adjacent chapter, and the scrollbar ---
// Each chapter scrolls on its own. Pulling on past either end and letting go continues into the
// adjacent chapter: reported as `continue` { index, forward } for the app to navigate (so it can
// count the chapter as read through). A badge with an arrow grows with the pull and fills once
// letting go will continue.

// CSS px of finger travel past the edge; well beyond a scrolling flick's overshoot.
const PULL = 140
const isScrolled = () => settings.flow === 'scrolled' && view.renderer?.scrolled
const adjacentIndex = dir => {
    const current = view.renderer?.getContents()?.[0]?.index
    const sections = view.book?.sections ?? []
    if (current == null) return null
    for (let i = current + dir; i >= 0 && i < sections.length; i += dir) {
        if (sections[i]?.linear !== 'no') return i
    }
    return null
}
const atEdge = dir => {
    const r = view.renderer
    return dir > 0 ? r.viewSize - r.end <= 2 : r.start <= 1
}
const hasSelection = () => (view.renderer?.getContents() ?? [])
    .some(({ doc }) => doc.getSelection()?.type === 'Range')

const SVG = 'http://www.w3.org/2000/svg'
const pullHint = document.createElement('div')
pullHint.id = 'yomu-pull'
const pullArrow = document.createElementNS(SVG, 'svg')
pullArrow.setAttribute('viewBox', '0 0 24 24')
const pullArrowPath = document.createElementNS(SVG, 'path')
pullArrowPath.setAttribute('d', 'M12 19V5M5 12l7-7 7 7')
pullArrow.append(pullArrowPath)
pullHint.append(pullArrow)
document.body.append(pullHint)

const pull = { active: false, dir: 0, from: 0, last: 0, distance: 0 }

const showPull = () => {
    const progress = Math.min(1, pull.distance / PULL)
    const armed = pull.distance >= PULL
    const ink = settings.colors?.ink ?? 'gray'
    pullHint.className = pull.dir > 0 ? 'next' : 'prev'
    pullHint.style.transition = 'none'
    pullHint.style.opacity = String(progress)
    pullHint.style.scale = String(0.6 + 0.4 * progress)
    pullHint.style.color = armed ? (settings.colors?.page ?? 'white') : ink
    pullHint.style.background = armed ? ink : `color-mix(in srgb, ${ink} 16%, transparent)`
}
const hidePull = () => {
    pullHint.style.transition = ''
    pullHint.style.opacity = '0'
}

const onPullStart = e => {
    pull.active = isScrolled() && e.touches.length === 1
    pull.dir = 0
    pull.distance = 0
    pull.last = e.touches[0]?.screenY ?? 0
}
const onPullMove = e => {
    if (!pull.active) return
    if (e.touches.length > 1) {
        pull.active = false
        hidePull()
        return
    }
    const y = e.touches[0].screenY
    const dy = y - pull.last
    pull.last = y
    if (!pull.dir) {
        // Finger moving up reads on (forward), down reads back.
        const dir = dy < 0 ? 1 : dy > 0 ? -1 : 0
        if (dir && atEdge(dir) && adjacentIndex(dir) != null && !hasSelection()) {
            pull.dir = dir
            pull.from = y
        }
        return
    }
    pull.distance = Math.max(0, (pull.from - y) * pull.dir)
    if (pull.distance === 0) {
        // Pulled back to where it started: scrolling again, not continuing.
        pull.dir = 0
        hidePull()
        return
    }
    showPull()
}
const onPullEnd = () => {
    if (!pull.active) return
    pull.active = false
    hidePull()
    const index = pull.dir && pull.distance >= PULL ? adjacentIndex(pull.dir) : null
    if (index != null) report('continue', { index, forward: pull.dir > 0 })
    pull.dir = 0
}
const listenForPulls = target => {
    target.addEventListener('touchstart', onPullStart, { passive: true })
    target.addEventListener('touchmove', onPullMove, { passive: true })
    target.addEventListener('touchend', onPullEnd, { passive: true })
    target.addEventListener('touchcancel', onPullEnd, { passive: true })
}
// Below a short chapter the touches land on this page rather than the chapter's.
listenForPulls(document)
view.addEventListener('load', ({ detail: { doc } }) => listenForPulls(doc))

// The chapter scrolls inside foliate's (closed) shadow root, out of reach of the WebView's own
// scrollbar, so the page draws one: shown while scrolling, faded out shortly after.
const scrollbar = document.createElement('div')
scrollbar.id = 'yomu-scrollbar'
document.body.append(scrollbar)
let scrollbarTimer = 0
const showScrollbar = () => {
    const r = view.renderer
    if (!isScrolled() || settings.showScrollbar === false || !(r.viewSize > r.size + 1)) {
        scrollbar.style.opacity = '0'
        return
    }
    const track = innerHeight
    const height = Math.max(32, track * r.size / r.viewSize)
    const top = (track - height) * Math.min(1, r.start / (r.viewSize - r.size))
    scrollbar.style.height = `${height}px`
    scrollbar.style.transform = `translateY(${top}px)`
    scrollbar.style.background = settings.colors?.ink ?? 'gray'
    scrollbar.style.opacity = '0.4'
    clearTimeout(scrollbarTimer)
    scrollbarTimer = setTimeout(() => scrollbar.style.opacity = '0', 900)
}

globalThis.yomuApplySettings = async next => {
    await preloadFonts(next)
    settings = next
    applyPageColor()
    if (view.renderer) configure(view.renderer)
}

const commonPrefix = (a, b) => {
    let n = 0
    while (n < a.length && n < b.length && a[n] === b[n]) n++
    return n
}
const commonSuffix = (a, b) => {
    let n = 0
    while (n < a.length && n < b.length && a[a.length - 1 - n] === b[b.length - 1 - n]) n++
    return n
}

/**
 * The range of [highlight] in [doc]'s text (as it stands, whitespace untouched, the way the Readium
 * engine quotes it); where it occurs more than once, the one whose surroundings best match
 * [before]/[after]. Null when it isn't there.
 */
const findText = (doc, { before = '', highlight, after = '' } = {}) => {
    const body = doc?.body
    if (!body || !highlight) return null
    const walker = doc.createTreeWalker(body, NodeFilter.SHOW_TEXT)
    const nodes = []
    const starts = []
    const parts = []
    let length = 0
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        nodes.push(node)
        starts.push(length)
        parts.push(node.nodeValue)
        length += node.nodeValue.length
    }
    const text = parts.join('')
    let best = -1
    let bestScore = -1
    for (let at = text.indexOf(highlight); at > -1; at = text.indexOf(highlight, at + 1)) {
        const end = at + highlight.length
        const score = commonSuffix(text.slice(Math.max(0, at - before.length), at), before)
            + commonPrefix(text.slice(end, end + after.length), after)
        if (score > bestScore) {
            best = at
            bestScore = score
        }
    }
    if (best < 0) return null
    // The text node holding offset [at]; [end] takes the node a range ending there ends in.
    const locate = (at, end) => {
        let i = nodes.length - 1
        while (i > 0 && (end ? starts[i] >= at : starts[i] > at)) i--
        return [nodes[i], at - starts[i]]
    }
    const range = doc.createRange()
    range.setStart(...locate(best, false))
    range.setEnd(...locate(best + highlight.length, true))
    return range
}

const goTo = target => {
    if (target?.cfi) return view.goTo(target.cfi)
    if (target?.href) return view.goTo(target.href)
    // A position quoted by its text (from the Readium engine): found once the chapter loads, else
    // its progression.
    if (target?.text?.highlight) {
        return view.renderer.goTo({
            index: target.index ?? 0,
            anchor: doc => findText(doc, target.text) ?? target.fraction ?? 0,
        })
    }
    return view.renderer.goTo({ index: target?.index ?? 0, anchor: target?.fraction ?? 0 })
}

globalThis.yomuGoTo = target => goTo(target).catch(e => report('error', { message: String(e?.stack ?? e) }))

// Loading the book needs no page size, so it can run before the reader is on screen (even before
// the tap, for the book the app expects you to open).
let bookLoad = null
const loadBook = () => bookLoad ??= (async () => {
    report('script')
    const book = await new EPUB(await makeLoader()).init()
    report('book', { sections: book.sections.length, title: book.metadata?.title ?? null })
    if (params.has('trace')) for (const [i, sec] of book.sections.entries()) {
        const load = sec.load
        sec.load = async () => {
            const t = performance.now()
            const src = await load()
            report('section-src', { index: i, took: Math.round(performance.now() - t) })
            return src
        }
    }
    await view.open(book)
    view.renderer.addEventListener('scroll', showScrollbar)
    report('view-open')
    return book
})()

globalThis.yomuLoadBook = () => {
    loadBook().catch(e => report('error', { message: String(e?.stack ?? e) }))
}

const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve))
const within = (promise, ms) => Promise.race([promise, new Promise(resolve => setTimeout(resolve, ms))])

/**
 * Fetches the reading font's files into this page's cache, so a chapter's first layout already
 * has them. Otherwise the chapter lays out in a fallback face, then reflows when the font arrives
 * and foliate re-anchors: the page visibly shifts after it shows.
 */
const preloadFonts = s => within(Promise.all((s.font?.faces ?? []).map(f =>
    new FontFace(s.font.family, `url("${f.src}")`, { style: f.style ?? 'normal', weight: f.weight ?? 'normal' })
        .load().catch(() => null))), 1500)

/** Waits until the shown chapter's fonts are in and foliate has re-laid it out. */
const settled = async () => {
    const doc = view.renderer?.getContents()?.[0]?.doc
    if (doc?.fonts) await within(doc.fonts.ready, 1500)
    // foliate expands and re-anchors on fonts.ready; give that a frame to land.
    await nextFrame()
    await nextFrame()
}

const open = async ({ start, settings: initial }) => {
    settings = initial ?? {}
    applyPageColor()
    const [book] = await Promise.all([loadBook(), preloadFonts(settings)])
    configure(view.renderer)
    try {
        await goTo(start)
    } catch (e) {
        // A position that no longer resolves (say, a stale CFI) opens the chapter instead.
        report('error', { message: 'start: ' + String(e?.stack ?? e) })
        await view.renderer.goTo({ index: start?.index ?? 0, anchor: 0 })
    }
    // Reveal only the final layout: no reflow after the page shows.
    await settled()
    shown = true
    if (view.lastLocation) reportLocation(view.lastLocation)
}

let started = false
globalThis.yomuStart = config => {
    if (started) return
    started = true
    open(config).catch(e => report('error', { message: String(e?.stack ?? e) }))
}
report('shell-ready')
