// Open Reader's glue around foliate-js: the book is read file by file from the app (never as one
// big blob), styled from the app's reader settings, and every position is reported to Kotlin.
// Page turns are foliate's own (its finger-following swipe and slide).
//
// The page loads before the app knows which book (so it can be made ahead of a tap) and reports
// `shell-ready`; the app then calls yomuStart({ start, settings }). `start` is { cfi } | { href } |
// { index, fraction } | { index, progress } (see progressAnchor); `settings` is described at
// readerCSS. Later changes come through yomuApplySettings / yomuGoTo.
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
    // foliate decodes links with decodeURI, which leaves reserved characters escaped: a file named
    // "In short, my life.jpg" arrives as "In short%2C my life.jpg". Fall back to the fully decoded name.
    const entryName = name => {
        if (Object.hasOwn(sizes, name)) return name
        try {
            const decoded = decodeURIComponent(name)
            return Object.hasOwn(sizes, decoded) ? decoded : null
        } catch {
            return null
        }
    }
    const get = async name => {
        const entry = entryName(name)
        if (entry == null) return null
        const res = await fetch(bookUrl(entry))
        return res.ok ? res : null
    }
    return {
        loadText: async name => (await get(name))?.text() ?? null,
        loadBlob: async name => (await get(name))?.blob() ?? null,
        getSize: name => sizes[entryName(name) ?? name] ?? 0,
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
 * A font file's absolute URL on this page's origin. The reading CSS goes into chapter documents,
 * which are blob: URLs: a path like "/assets/fonts/Lora-Regular.ttf" can't be resolved against
 * one, so the face failed to load and the text fell back to the default serif.
 */
const fontURL = src => new URL(src, location.href).href

/**
 * The page's CSS. foliate's rendering keeps the book's own layout; on top of it the reader's
 * settings force what the reader chose:
 *   colors { page, ink, link, dark } — forced over the book's (null: follow the system scheme)
 *   font { family, faces: [{ src, style }] } — forced family (null: the book's fonts)
 *   fontScale, lineHeight (null: 1.5, the book may override), paragraphSpacing (em, null: book's)
 *   align: 'publisher' | 'start' | 'justify'
 */
// The reader's Left / Justify setting only swaps body text between left-aligned and justified:
// centred and right-aligned text (titles, "Chapter 1" labels, signatures) keeps the book's own
// alignment, and Auto leaves everything to the book. CSS can't do this — a rule on <p> beats the
// alignment it would inherit from a centred block — so it is applied per element after layout.
const ALIGNED = 'p, li, blockquote, dd'
const applyAlignment = doc => {
    if (!doc?.body) return
    for (const el of doc.querySelectorAll('[data-yomu-align]')) {
        el.style.removeProperty('text-align')
        el.removeAttribute('data-yomu-align')
    }
    const mode = settings.align
    if (mode !== 'start' && mode !== 'justify') return
    const view = doc.defaultView
    for (const el of doc.querySelectorAll(ALIGNED)) {
        const current = view.getComputedStyle(el).textAlign
        const swap = mode === 'start'
            ? current === 'justify'
            : current === 'start' || current === 'left'
        if (!swap) continue
        el.style.setProperty('text-align', mode === 'start' ? 'start' : 'justify', 'important')
        el.setAttribute('data-yomu-align', '')
    }
}
const applyAlignmentEverywhere = () => {
    for (const { doc } of view.renderer?.getContents() ?? []) applyAlignment(doc)
    for (const { doc } of preview?.el?.getContents?.() ?? []) applyAlignment(doc)
}

/**
 * Whether line art changes look on [s]'s page: always on a dark one (drawn light), and on a light
 * one that isn't white (a sepia page, where its white showed as a box: it's multiplied into the
 * page). On a white page it already looks as published, so nothing waits for it there.
 */
const pageTinted = s => !!s.colors && s.adaptPictures !== false
    && (s.colors.dark || !/^#?f{6}$/i.test(s.colors.page ?? ''))

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
            font-weight: ${f.weight ?? 'normal'}; src: url("${fontURL(f.src)}"); }`).join('')
    const font = s.font ? `
        body, body *:not(code):not(pre):not(kbd):not(samp):not(tt) {
            font-family: "${s.font.family}", serif !important;
        }` : ''
    const lineHeight = s.lineHeight ?? 1.5
    const lineImportant = s.lineHeight != null ? '!important' : ''
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
            hyphens: ${hyphens};
        }
        ${s.paragraphSpacing != null ? `p { margin-top: 0 !important; margin-bottom: ${s.paragraphSpacing}em !important; }` : ''}
        [align="left"] { text-align: left; }
        [align="right"] { text-align: right; }
        [align="center"] { text-align: center; }
        [align="justify"] { text-align: justify; }
        h1, h2, h3, h4, h5, h6, hgroup { text-wrap: balance; }
        ${pageTinted(s) ? `
        img[data-yomu-pending], image[data-yomu-pending] { visibility: hidden !important; }` : ''}
        ${c && s.adaptPictures !== false ? (c.dark ? `
        img[data-yomu-ink], image[data-yomu-ink] {
            filter: invert(1) hue-rotate(180deg) !important;
            mix-blend-mode: screen;
        }` : `
        img[data-yomu-ink], image[data-yomu-ink] { mix-blend-mode: multiply; }`) : ''}
        pre { white-space: pre-wrap !important; }
        img, svg image { -webkit-touch-callout: none; -webkit-user-drag: none; }
        aside[epub|type~="endnote"],
        aside[epub|type~="footnote"],
        aside[epub|type~="note"],
        aside[epub|type~="rearnote"] {
            display: none;
        }
    `
}

// foliate's space above and below the text. Paged, the app already keeps the page clear of its bars
// and the screen's safe area, so this is only breathing room (foliate's 48px default, on top of
// those insets, wasted a band at each end). Scrolled, it is the space before a chapter's first line
// and after its last.
const PAGED_MARGIN = '16px'
const SCROLLED_MARGIN = '48px'

// Two pages side by side (a spread, like an open book) when paged on a landscape page wide enough for
// two comfortable columns: at least 840 px and 56em of the reading size (two ~25em columns with
// their margins and gutter). Portrait, and phones held upright, read one page. `settings.spread`
// false turns it off.
const SPREAD_MIN_WIDTH = 840
const SPREAD_MIN_EMS = 56
// A spread's gutter and outer margins are wider than a single page's side margins.
const SPREAD_GAP_FACTOR = 1.8
const spreads = () => settings.flow !== 'scrolled' && settings.spread !== false
    && innerWidth > innerHeight
    && innerWidth >= Math.max(SPREAD_MIN_WIDTH, SPREAD_MIN_EMS * 16 * (settings.fontScale ?? 1))
const gapFor = spread => {
    const gap = settings.gap ?? '6%'
    return spread ? `${(parseFloat(gap) * SPREAD_GAP_FACTOR).toFixed(2)}%` : gap
}
// foliate caps a column at 720px. On a wide screen that cap, not the side gap, sets the margins, so
// the reader's Margins setting (1 = Auto) scales it too: wider margins, a narrower column.
const BASE_COLUMN_PX = 720
const MIN_COLUMN_PX = 280
const maxInlineSize = () => {
    const m = Math.max(0.25, settings.margins ?? 1)
    return `${Math.max(MIN_COLUMN_PX, Math.round(BASE_COLUMN_PX * Math.pow(m, -0.75)))}px`
}
// Set before foliate lays out: from a resize listener, which runs ahead of its resize observer.
const configureColumns = renderer => {
    const spread = spreads()
    renderer.setAttribute('gap', gapFor(spread))
    renderer.setAttribute('max-inline-size', maxInlineSize())
    renderer.setAttribute('max-column-count', spread ? '2' : '1')
}
addEventListener('resize', () => {
    if (view.renderer) configureColumns(view.renderer)
    dropPreview()
})

const configure = renderer => {
    const scrolled = settings.flow === 'scrolled'
    renderer.setAttribute('flow', scrolled ? 'scrolled' : 'paginated')
    renderer.setAttribute('margin', scrolled ? SCROLLED_MARGIN : PAGED_MARGIN)
    // foliate's slide, unless the system has animations off.
    if (settings.animated === false) renderer.removeAttribute('animated')
    else renderer.setAttribute('animated', '')
    configureColumns(renderer)
    renderer.setStyles(readerCSS(settings))
}

/** How many pages show side by side: 2 in a spread, else 1. */
const columns = () => {
    const r = view.renderer
    return r && !r.scrolled ? Math.max(1, r.heads?.length ?? 1) : 1
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
    doc.addEventListener('click', e => {
        if (doc.getSelection()?.type === 'Range') return
        if (e.target.closest?.('a[href]')) return
        if (longPressed) {
            longPressed = false
            return
        }
        // The chapter frame is wider than the screen and scrolled from outside, so use screen x.
        const image = imageAt(e.target)
        if (image && !turnsPageAt(e.screenX)) openImage(image)
        else onPageTap(e.screenX)
    })
    listenForLongPresses(doc)
})

/**
 * A tap at [x] (CSS px from the page's left). Paged: the outer thirds turn the page (left and right
 * as the book reads, so a right-to-left book turns back on the right) and the middle is the app's
 * menu. Scrolled, or with tap turns off, any tap is the menu.
 */
const tapsTurn = () => settings.flow !== 'scrolled' && settings.tapTurns !== false
/** Whether a tap at [x] turns the page (an outer third, paged) rather than being the menu's. */
const turnsPageAt = x => tapsTurn() && (x < innerWidth / 3 || x > innerWidth * 2 / 3)
const onPageTap = x => {
    const paged = tapsTurn()
    // foliate's own click listener (which finds a tapped highlight) runs after the chapter's, so
    // decide once it has: a tap on a highlight is the highlight's.
    highlightTapped = false
    setTimeout(() => {
        if (highlightTapped) return
        if (paged && x < innerWidth / 3) view.goLeft()
        else if (paged && x > innerWidth * 2 / 3) view.goRight()
        else report('center-tap')
    })
}
// A tap beside the text (the page is wider than its column or spread) lands on this page, not the
// chapter's.
document.addEventListener('click', e => {
    if (!hasSelection()) onPageTap(e.clientX)
})

// Positions count only once the start position is on screen: before its chapter loads, foliate
// reports from its empty frame (one page, no progress).
let shown = false
view.addEventListener('relocate', ({ detail }) => {
    if (!shown) return
    followCrossing(detail)
    reportLocation(detail)
})

// --- Paged: turning into the adjacent section ---
// foliate turns a page by sliding its columns; at a section's last (or first) page the turn slides
// on to the blank padding page past the text, then loads the next section in place. Loaded only
// then, it arrived a beat after the old page had gone, on a blank page.
//
// So the adjacent section is laid out ahead in a second, hidden paginator (the preview), with the
// reader's settings and the same box: the next one while the reader is on a section's last pages,
// the previous one (at its end) on its first pages. As the outgoing page slides away (foliate's
// slide, a tap's or the finger's) the preview follows it in from the same side, its offset bound to
// the outgoing scroll position, so the two move as one turn. Once it covers the page, it stays there
// while foliate loads the same section underneath, and steps aside once that has laid out: being the
// same layout, nothing changes on screen. A turn that would cross before the preview is ready waits
// for it (up to PREVIEW_WAIT_MS), the outgoing page staying put; past that it falls back to sliding
// the section in once loaded. RTL books turn the other way; with animations off the turn is a cut
// straight to the preview.

const TURN_MS = 300
// foliate's page-turn curve (easeOutQuad).
const EASE_TURN = 'cubic-bezier(0.25, 0.46, 0.45, 0.94)'
// A crossing that hasn't arrived by then (it failed, or went nowhere) puts the page back.
const CROSSING_TIMEOUT_MS = 2500
// How long a turn waits for the preview before crossing without it.
const PREVIEW_WAIT_MS = 1500
let crossing = null // { from, shift, timer }: the fallback crossing, without a preview
let lastSection = null

// { el, from, index, forward, ready, loading, holding }: the preview of the section after (forward)
// or before section `from`.
let preview = null

const currentIndex = () => view.renderer?.getContents()?.[0]?.index
const horizontalPages = () => {
    const r = view.renderer
    return r && !r.scrolled && r.scrollProp === 'scrollLeft'
}

const previewElement = () => {
    if (preview?.el) return preview.el
    const el = document.createElement('foliate-paginator')
    el.id = 'yomu-preview'
    el.bookDir = view.book.dir
    el.sections = view.book.sections
    // As foliate's view does for its own renderer: the book's language and direction, unless set.
    el.addEventListener('load', ({ detail: { doc, index } }) => {
        doc.documentElement.lang ||= view.language?.canonical ?? ''
        if (!view.language?.isCJK) doc.documentElement.dir ||= view.language?.direction ?? ''
        applyAlignment(doc)
        markPictures(doc, index)
    })
    // The section's highlights too, so none appears only once the reader's own page takes over.
    el.addEventListener('create-overlayer', ({ detail: { doc, index, attach } }) => {
        const overlayer = new Overlayer()
        attach(overlayer)
        for (const h of highlights.values()) {
            if (h.index !== index) continue
            try {
                overlayer.add(h.cfi, view.resolveNavigation(h.cfi).anchor(doc), drawHighlight, { style: h.style, color: h.color, note: h.note })
            } catch {
                // One that doesn't resolve here is drawn once the page takes over.
            }
        }
    })
    // Above the reader's page, under the app's own overlays.
    view.after(el)
    configure(el)
    return el
}

/** Lays out the section after (or before) the one being read in the preview, if it isn't already. */
const preparePreview = forward => {
    if (!horizontalPages() || preview?.holding) return null
    const from = currentIndex()
    const index = from == null ? null : adjacentIndex(forward ? 1 : -1)
    if (index == null) return null
    if (preview && preview.from === from && preview.index === index) return preview
    const el = previewElement()
    const p = { el, from, index, forward, ready: false, holding: false }
    preview = p
    hidePreview()
    p.loading = (async () => {
        try {
            await el.goTo({ index, anchor: forward ? 0 : 1 })
            const doc = el.getContents()?.[0]?.doc
            if (doc?.fonts) await within(doc.fonts.ready, 1500)
            // Its pictures marked before it can slide in (see markPictures).
            await within(picturesMarked(doc), PICTURES_BUDGET_MS * 3)
            await nextFrame()
            await nextFrame()
            if (preview === p) p.ready = true
        } catch (e) {
            report('error', { message: 'preview: ' + String(e?.stack ?? e) })
        }
    })()
    return p
}

/** Prepares what the next turn may cross into: near a section's end the next, near its start the previous. */
const prepareAdjacent = () => {
    const r = view.renderer
    if (!horizontalPages() || !(r.pages > 2)) return
    if (r.page >= r.pages - 3) preparePreview(true)
    else if (r.page <= 2) preparePreview(false)
}

const hidePreview = () => {
    const el = preview?.el
    if (!el) return
    el.style.visibility = 'hidden'
    el.style.transform = ''
}

/**
 * Keeps the preview beside the outgoing page as it slides (a turn, a swipe, a snap back): fully off
 * screen while the reader's page is on text, fully on once it is on the blank padding page.
 */
const followTurn = () => {
    const p = preview
    const r = view.renderer
    if (!p?.ready || p.holding || crossing || !horizontalPages() || currentIndex() !== p.from) return
    const size = r.size
    if (!(size > 0) || !(r.pages > 2)) return
    // How far the turn into the padding page has gone, 0..1.
    const progress = Math.min(1, Math.max(0, p.forward
        ? (r.start - (r.pages - 2) * size) / size
        : (size - r.start) / size))
    if (progress <= 0.001) {
        hidePreview()
        return
    }
    // Forward, the next page comes in from the right (from the left in a right-to-left book).
    const side = (p.forward ? 1 : -1) * (view.book?.dir === 'rtl' ? -1 : 1)
    if (progress >= 0.999) {
        holdPreview(p.forward)
        return
    }
    p.el.style.transform = `translateX(${side * (1 - progress) * size}px)`
    p.el.style.visibility = 'visible'
}

/** The turn has reached the padding page: the preview covers it until foliate has the section. */
const holdPreview = forward => {
    const p = preview
    if (!p?.ready || p.forward !== forward || currentIndex() !== p.from) return false
    p.holding = true
    p.el.style.transform = ''
    p.el.style.visibility = 'visible'
    p.timer = setTimeout(() => {
        // The section never came: show the reader's page again.
        if (preview === p && p.holding) releasePreview(p)
    }, CROSSING_TIMEOUT_MS)
    return true
}

const releasePreview = p => {
    clearTimeout(p.timer)
    p.holding = false
    hidePreview()
}

const followCrossing = detail => {
    const r = view.renderer
    const index = detail.section?.current
    const p = preview
    if (p?.holding) {
        // foliate has laid the section out where the preview shows it: swap once it has settled.
        if (index === p.index && r.page >= 1 && r.page <= r.pages - 2) {
            Promise.all([settled(), within(picturesOnPage(), PICTURES_BUDGET_MS)]).then(() => {
                if (preview !== p || !p.holding) return
                releasePreview(p)
                // Ready for a turn straight back.
                prepareAdjacent()
            })
        }
    } else if (crossing) {
        if (index !== crossing.from && r.page >= 1 && r.page <= r.pages - 2) finishCrossing(crossing)
    } else if (!r.scrolled && index === lastSection && r.pages > 2 && (r.page <= 0 || r.page >= r.pages - 1)) {
        const forward = r.page > 0
        if (!holdPreview(forward)) beginCrossing(forward ? 1 : -1, index)
    }
    lastSection = index
    if (!preview?.holding && !crossing) prepareAdjacent()
}

const beginCrossing = (dir, from) => {
    if (settings.animated === false) return
    const shift = dir * (view.book?.dir === 'rtl' ? -1 : 1) * innerWidth
    const c = { from, shift }
    c.timer = setTimeout(() => {
        if (crossing !== c) return
        crossing = null
        view.style.transform = ''
    }, CROSSING_TIMEOUT_MS)
    crossing = c
    view.style.transform = `translateX(${shift}px)`
}

const finishCrossing = async c => {
    clearTimeout(c.timer)
    // Only the final layout slides in: fonts in, and foliate re-anchored.
    await settled()
    if (crossing !== c) return
    crossing = null
    view.style.transform = ''
    view.animate([{ transform: `translateX(${c.shift}px)` }, { transform: 'translateX(0)' }], { duration: TURN_MS, easing: EASE_TURN })
}

/** Waits (a while at most) for the preview a turn [forward] from here would cross into. */
const awaitPreview = async forward => {
    const p = preparePreview(forward)
    if (!p || p.ready) return
    await within(p.loading, PREVIEW_WAIT_MS)
}

/**
 * Turns that would cross into the adjacent section wait for its preview first, so the incoming
 * page can follow the outgoing one: a tap or key at a section's last (or first) page, and a swipe
 * released past it.
 */
const guardTurns = renderer => {
    const atLast = () => renderer.page >= renderer.pages - 2
    const atFirst = () => renderer.page <= 1
    const next = renderer.next.bind(renderer)
    const prev = renderer.prev.bind(renderer)
    const snap = renderer.snap.bind(renderer)
    renderer.next = async distance => {
        if (horizontalPages() && atLast()) await awaitPreview(true)
        return next(distance)
    }
    renderer.prev = async distance => {
        if (horizontalPages() && atFirst()) await awaitPreview(false)
        return prev(distance)
    }
    renderer.snap = async (vx, vy) => {
        if (horizontalPages() && renderer.pages > 2) {
            const size = renderer.size
            if (renderer.start > (renderer.pages - 2) * size + 1) await awaitPreview(true)
            else if (renderer.start < size - 1) await awaitPreview(false)
        }
        return snap(vx, vy)
    }
}

const reportLocation = detail => {
    if (firstPage) {
        firstPage = false
        // Two frames: the first page has been laid out and painted.
        requestAnimationFrame(() => requestAnimationFrame(() => report('first-page')))
    }
    const r = view.renderer
    const scrolled = r.scrolled
    // Progress within the chapter, as foliate measures it: paged has a blank padding page (or
    // spread) at each end of a chapter, so text screens are 1 .. pages - 2.
    const textScreens = Math.max(1, r.pages - 2)
    const screen = Math.min(textScreens - 1, Math.max(0, r.page - 1))
    const fraction = scrolled
        ? (r.viewSize > 0 ? r.start / r.viewSize : 0)
        : (textScreens > 1 ? screen / (textScreens - 1) : 0)
    // How far through the scrollable range: 1 at the chapter's end (`fraction` measures the top of
    // the screen, so it never gets there).
    const scrollRange = r.viewSize - r.size
    const measured = scrolled ? (scrollRange > 1 ? r.start / scrollRange : 1) : fraction
    // Counted in pages, not screens: a spread shows two, and `page` is the last one in view, so
    // what's left is what's after this screen.
    const perScreen = columns()
    report('relocate', {
        pageLabel: printedPages(detail, perScreen),
        index: detail.section?.current,
        fraction: Math.min(1, Math.max(0, fraction)),
        measured: Math.min(1, Math.max(0, measured)),
        total: detail.fraction,
        cfi: detail.cfi,
        page: screen * perScreen + perScreen - 1,
        pages: textScreens * perScreen,
        columns: perScreen,
        scrolled,
        title: detail.tocItem?.label ?? null,
        // A section the contents don't list (a title page, the book's own contents page) by its
        // own heading, so it can still be named.
        heading: detail.tocItem?.label ? null : sectionHeading(detail.section?.current),
    })
}

/**
 * The printed page(s) on screen, from the book's page list: the page the screen starts on, and
 * across a spread the one it ends on too ("112–113"). Null when the book has no page list.
 */
/** Section [index]'s first heading, else its document title; null when it has neither. */
const sectionHeading = index => {
    const doc = view.renderer?.getContents()?.find(c => c.index === index)?.doc
    if (!doc) return null
    const text = el => el?.textContent?.replace(/\s+/g, ' ').trim()
    const heading = text(doc.querySelector('h1, h2, h3')) || text(doc.querySelector('title'))
    return heading ? heading.slice(0, 80) : null
}

const printedPages = (detail, perScreen) => {
    const start = detail.pageItem?.label?.trim()
    if (!start) return null
    if (perScreen < 2 || !detail.range) return start
    try {
        const end = detail.range.cloneRange()
        end.collapse(false)
        const last = view.getProgressOf(detail.section?.current, end)?.pageItem?.label?.trim()
        return last && last !== start ? `${start}–${last}` : start
    } catch {
        return start
    }
}

/** The book's page list, flattened: [{ label, href }] (hrefs are paths inside the EPUB). */
const pageList = book => {
    const out = []
    const walk = items => {
        for (const item of items ?? []) {
            const label = item.label?.trim()
            if (label && item.href) out.push({ label, href: item.href })
            walk(item.subitems)
        }
    }
    walk(book.pageList)
    return out
}

// --- Selection and highlights ---

let highlightTapped = false
let highlights = new Map() // cfi -> { id, cfi, color, style, note }

const STYLES = new Set(['highlight', 'underline', 'squiggly', 'strikethrough'])
const SVG_NS = 'http://www.w3.org/2000/svg'
/**
 * Draws a highlight in its style (the Overlayer function of that name), with a small dot at the
 * end of its last line when it carries a note.
 */
const drawHighlight = (rects, { style, color, note }) => {
    const line = STYLES.has(style) && style !== 'highlight'
    const g = Overlayer[line ? style : 'highlight'](rects, line ? { color, width: 2.5 } : { color })
    const last = rects[rects.length - 1]
    if (!note || !last) return g
    const group = document.createElementNS(SVG_NS, 'g')
    const dot = document.createElementNS(SVG_NS, 'circle')
    dot.setAttribute('cx', last.right + 4)
    dot.setAttribute('cy', last.top + 3)
    dot.setAttribute('r', 3.5)
    dot.setAttribute('fill', color)
    dot.setAttribute('stroke', settings.colors?.ink ?? 'gray')
    dot.setAttribute('stroke-width', 1)
    group.append(g, dot)
    return group
}
const annotationOf = h => ({ value: h.cfi, color: h.color, style: h.style, note: !!h.note })
const sameLook = (a, b) => a.color === b.color && a.style === b.style && !!a.note === !!b.note

view.addEventListener('draw-annotation', ({ detail: { draw, annotation } }) => {
    draw(drawHighlight, { style: annotation.style, color: annotation.color, note: annotation.note })
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
        if (h.index === index) view.addAnnotation(annotationOf(h)).catch(() => {})
    }
})

// Highlights made under the Readium engine carry their text instead of a CFI. Each is found by its
// text when its chapter loads, then drawn (and tapped) like any other, under the CFI found.
let textHighlights = [] // [{ id, index, text: { before, highlight, after }, color }]
const anchorTextHighlights = (index, doc) => textHighlights.flatMap(h => {
    if (h.index !== index) return []
    const range = findText(doc, h.text)
    return range ? [{ id: h.id, cfi: view.getCFI(index, range), color: h.color, style: h.style, note: h.note, index }] : []
})
// 'load' comes before the chapter's overlay is made (create-overlay), which then draws these.
view.addEventListener('load', ({ detail: { doc, index } }) => {
    for (const h of anchorTextHighlights(index, doc)) highlights.set(h.cfi, h)
    markPictures(doc, index)
})

// --- Pictures on dark pages ---
// Line art published black on white (ornaments, scene breaks, glyphs set in the text, a handwritten
// note) shows its white as a box on any page that isn't white: glaring on a dark theme, a pale
// rectangle on sepia. The app decides which pictures are line
// art (PictureTone.kt: colourless with light corners; a few lines small and marked or mostly
// two-tone, or bigger and nearly pure two-tone) from what the page measures of each one; line art
// is marked data-yomu-ink and the page's CSS takes it onto the page's own colour while "Adapt
// pictures to dark pages" is on: on a dark page inverted and screened (its black becomes the page),
// on a light one multiplied (its white becomes the page). Both blend against the page colour on
// `html`: everything between is transparent (readerCSS), so nothing isolates them. Illustrations, photos and anything in
// colour stay as published.
//
// Decisions are kept per picture file (its path in the EPUB, not the blob URL, which changes with
// every load), so a section's pictures are marked as it loads, before it's shown: a section seen
// before, or laid out ahead by the crossing preview, shows its ornaments inverted from its first
// frame. A picture not decided yet stays hidden until it is (it's measured at its own size, which
// for an ornament takes a moment), so it never shows un-inverted; the first page and a crossing
// wait PICTURES_BUDGET_MS for decisions before showing.

const SAMPLE_MAX = 256
const PENDING_MAX_MS = 3000
// Class or id words publishers use for decorations.
const DECORATION = /orn|sborn|scene|break|divider|decor|fleuron|dinkus|separator|music/i
const debugPictures = params.has('debug')
// Picture path in the EPUB -> whether it's line art (true / false), once decided.
const pathTones = new Map()
// Section index -> Promise of its pictures' paths, in document order.
const sectionPicturePaths = new Map()
// Section index -> those paths, once known (for marking synchronously).
const knownPicturePaths = new Map()

const adaptingPictures = () => pageTinted(settings)

/** The paths of section [index]'s pictures, in document order (from the section's own markup). */
const picturePathsOf = index => {
    let paths = sectionPicturePaths.get(index)
    if (!paths) {
        const section = view.book?.sections?.[index]
        paths = (async () => {
            const doc = await section.createDocument()
            return [...doc.querySelectorAll('img, image')].map(el => {
                const src = el.getAttribute('src') || el.getAttribute('href') || el.getAttributeNS(XLINK_NS, 'href')
                return src ? section.resolveHref(src) : null
            })
        })().catch(() => [])
        paths.then(list => knownPicturePaths.set(index, list))
        sectionPicturePaths.set(index, paths)
    }
    return paths
}

const markupHints = el => {
    let decoration = false, illustration = false
    for (let n = el, depth = 0; n?.nodeType === Node.ELEMENT_NODE && depth < 5; n = n.parentElement, depth++) {
        const names = `${n.getAttribute('class') ?? ''} ${n.id ?? ''}`
        const types = `${n.getAttributeNS(OPS, 'type') ?? ''} ${n.getAttribute('role') ?? ''}`
        if (/\b(cover|illustration|doc-cover)\b/i.test(types) || /cover/i.test(names)) illustration = true
        if (DECORATION.test(names) || /\b(presentation|decorative|separator)\b/i.test(types)) decoration = true
    }
    if (el.closest?.('figure')?.querySelector('figcaption')) illustration = true
    return {
        decorationHint: decoration,
        illustrationHint: illustration,
        emptyAlt: el.localName === 'img' && el.getAttribute('alt')?.trim() === '',
    }
}

const lineHeightAt = el => {
    const style = el.ownerDocument.defaultView.getComputedStyle(el.parentElement ?? el)
    return parseFloat(style.lineHeight) || (parseFloat(style.fontSize) || 16) * 1.5
}

/** Picture [el]'s pixels decoded, or null when they can't be (tried twice). */
const decodedPicture = async el => {
    const src = imageSource(el)
    if (!src) return null
    const img = el.localName === 'img' ? el : Object.assign(new Image(), { src })
    for (let attempt = 0; attempt < 2 && !(img.complete && img.naturalWidth); attempt++) {
        await img.decode().catch(() => nextFrame())
    }
    return img.naturalWidth && img.naturalHeight ? img : null
}

/** PictureTone's stats for picture [el] (see PictureTone.kt), or null when it can't be read. */
const pictureStats = async el => {
    const img = await decodedPicture(el)
    if (!img) return null
    const w = img.naturalWidth, h = img.naturalHeight
    // Small pictures at their own size: scaled down, their thin lines would blur into greys.
    const scale = Math.min(1, SAMPLE_MAX / Math.max(w, h))
    const cw = Math.max(1, Math.round(w * scale)), ch = Math.max(1, Math.round(h * scale))
    const canvas = document.createElement('canvas')
    canvas.width = cw
    canvas.height = ch
    const context = canvas.getContext('2d', { willReadFrequently: true })
    context.drawImage(img, 0, 0, cw, ch)
    const data = context.getImageData(0, 0, cw, ch).data
    const k = Math.max(2, Math.round(Math.min(cw, ch) * 0.12))
    let opaque = 0, chromaSum = 0, colorful = 0, dark = 0, light = 0, corner = 0, cornerLight = 0
    for (let y = 0, i = 0; y < ch; y++) {
        for (let x = 0; x < cw; x++, i += 4) {
            const r = data[i] / 255, g = data[i + 1] / 255, b = data[i + 2] / 255, a = data[i + 3] / 255
            const lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            const chroma = Math.max(r, g, b) - Math.min(r, g, b)
            if ((x < k || x >= cw - k) && (y < k || y >= ch - k)) {
                corner++
                if (a < 0.5 || lum > 0.8) cornerLight++
            }
            if (a < 0.5) continue
            opaque++
            chromaSum += chroma
            if (chroma > 0.15) colorful++
            if (lum < 0.2) dark++
            else if (lum > 0.8) light++
        }
    }
    const rect = el.getBoundingClientRect()
    const line = lineHeightAt(el)
    return {
        opaque: opaque / (cw * ch),
        saturation: opaque ? chromaSum / opaque : 0,
        colorful: opaque ? colorful / opaque : 0,
        dark: opaque ? dark / opaque : 0,
        light: opaque ? light / opaque : 0,
        corners: corner ? cornerLight / corner : 0,
        naturalWidth: w,
        naturalHeight: h,
        renderedWidthLines: rect.width / line,
        renderedHeightLines: rect.height / line,
        ...markupHints(el),
    }
}

/** Decides picture [el] (at [path]); null when it couldn't be measured (try again next time). */
const decidePicture = async (el, path) => {
    const stats = await pictureStats(el)
    const ink = stats ? host?.pictureTone?.(JSON.stringify(stats)) === true : null
    if (debugPictures) console.debug('picture', path, ink, JSON.stringify(stats))
    if (ink != null && path) pathTones.set(path, ink)
    return ink
}

const showDecided = (el, ink) => {
    el.toggleAttribute('data-yomu-ink', ink === true)
    el.removeAttribute('data-yomu-pending')
}

// How long the first page, or a crossing, waits for pictures to be decided before showing anyway
// (what's still undecided then stays hidden until it is).
const PICTURES_BUDGET_MS = 100
// Document -> Promise, settled once its pictures are marked.
const docPictures = new WeakMap()
// Only a dark page waits: on a light one nothing is hidden or changed.
const picturesMarked = doc => (adaptingPictures() && doc && docPictures.get(doc)) || Promise.resolve()
const picturesOnPage = () => Promise.all((view.renderer?.getContents() ?? []).map(({ doc }) => picturesMarked(doc)))

/**
 * Marks section [index]'s pictures in [doc] (call it from the section's `load`, before it's shown):
 * the decided ones straight away; on a dark page the rest are hidden until measured and decided.
 * On a light page they're decided too, unhidden, so turning the page dark later is instant.
 */
const markPictures = (doc, index) => {
    if (settings.adaptPictures === false || !doc?.querySelectorAll) return Promise.resolve()
    const pictures = [...doc.querySelectorAll('img, svg image')]
    if (!pictures.length) return Promise.resolve()
    const hide = adaptingPictures()
    const paths = knownPicturePaths.get(index)
    const undecided = []
    pictures.forEach((el, i) => {
        const tone = paths ? pathTones.get(paths[i]) : undefined
        if (tone === undefined) {
            if (hide) el.setAttribute('data-yomu-pending', '')
            undecided.push([el, i])
        } else {
            showDecided(el, tone)
        }
    })
    if (!undecided.length) return Promise.resolve()
    // Never left hidden for good: what can't be decided shows as published.
    const timer = setTimeout(() => undecided.forEach(([el]) => el.removeAttribute('data-yomu-pending')), PENDING_MAX_MS)
    const work = picturePathsOf(index).then(async list => {
        // Measured once laid out (their size on the page counts).
        await nextFrame()
        await Promise.all(undecided.map(async ([el, i]) => {
            const path = list[i] ?? null
            const known = path ? pathTones.get(path) : undefined
            showDecided(el, known !== undefined ? known : await decidePicture(el, path))
        }))
        clearTimeout(timer)
    }).catch(() => {})
    docPictures.set(doc, work)
    return work
}

/**
 * Replaces the drawn highlights with [list]: [{ id, cfi, color, style, note }], or
 * [{ id, index, text, color, style, note }] for one positioned by its text. `style` names an
 * Overlayer drawing (highlight / underline / squiggly / strikethrough); `note` is true when it has one.
 */
globalThis.yomuSetHighlights = async list => {
    // Their chapters are resolved against the book, which may still be loading.
    await loadBook()
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
        if (!old || !sameLook(old, h)) view.addAnnotation(annotationOf(h)).catch(() => {})
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

const XML_NS = 'http://www.w3.org/XML/1998/namespace'
/**
 * The language of [node]: the nearest `lang` / `xml:lang` around it, else the book's, as a primary
 * subtag ("en" for "en-GB"). Null when neither says.
 */
const languageOf = node => {
    let tag = null
    for (let el = node?.nodeType === Node.ELEMENT_NODE ? node : node?.parentElement; el && !tag; el = el.parentElement) {
        tag = el.getAttribute('lang') || el.getAttributeNS(XML_NS, 'lang') || null
    }
    tag ??= view.language?.canonical ?? [view.book?.metadata?.language].flat()[0] ?? null
    if (!tag) return null
    try {
        return new Intl.Locale(tag).language
    } catch {
        return tag.split(/[-_]/)[0].toLowerCase() || null
    }
}

/** The current text selection: { index, cfi, text, before, after, language }, or null. */
globalThis.yomuSelection = () => {
    for (const { doc, index } of view.renderer?.getContents() ?? []) {
        const sel = doc.getSelection()
        if (!sel || sel.rangeCount === 0 || sel.isCollapsed) continue
        const range = sel.getRangeAt(0)
        return {
            index,
            cfi: view.getCFI(index, range),
            text: sel.toString(),
            language: languageOf(range.commonAncestorContainer),
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

const IMAGE_FILE = /\.(jpe?g|png|gif|webp|svg|avif|bmp)$/i
view.addEventListener('link', e => {
    const { a, href } = e.detail
    // A picture linked to its full-size file opens in the viewer, not as a page of its own.
    const image = a.querySelector?.('img, image')
    if (image && IMAGE_FILE.test(href?.split('#')[0] ?? '')) {
        e.preventDefault()
        openImage(image)
        return
    }
    if (!href?.includes('#') || !isNoteReference(a)) {
        // foliate follows it: the app keeps where the reader was, to offer the way back.
        report('link-jump')
        return
    }
    e.preventDefault()
    const follow = () => {
        report('link-jump')
        view.goTo(href)
    }
    noteContent(href)
        .then(html => html ? report('footnote', { html }) : follow())
        .catch(follow)
})

// --- Images ---
// A tap on a picture (where a tap isn't a page turn) or a long press on it anywhere opens it in the
// app's viewer: its bytes go over as `image` { data (base64), type, description }. Other formats
// (SVG, say) go as PNG, drawn at up to twice their size, so the viewer and the gallery get a bitmap.

const XLINK_NS = 'http://www.w3.org/1999/xlink'
const LONG_PRESS_MS = 450
const LONG_PRESS_SLOP = 10
const MAX_IMAGE_BYTES = 24 * 1024 * 1024
const MAX_RASTER = 4096

const imageAt = el => {
    const image = el?.closest?.('img, image')
    if (image) return image
    return el?.closest?.('svg')?.querySelector?.('image') ?? null
}
const imageSource = el => el.localName === 'img'
    ? (el.currentSrc || el.src)
    : (el.href?.baseVal || el.getAttribute('href') || el.getAttributeNS(XLINK_NS, 'href'))

const rasterize = async src => {
    const img = new Image()
    img.src = src
    await img.decode()
    const scale = Math.min(2, MAX_RASTER / Math.max(1, img.naturalWidth, img.naturalHeight))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round((img.naturalWidth || 512) * scale))
    canvas.height = Math.max(1, Math.round((img.naturalHeight || 512) * scale))
    canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height)
    return new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
}

const base64 = bytes => {
    let binary = ''
    for (let i = 0; i < bytes.length; i += 0x8000) {
        binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000))
    }
    return btoa(binary)
}

let openingImage = false
const openImage = async el => {
    if (openingImage) return
    const src = imageSource(el)
    if (!src) return
    openingImage = true
    try {
        let blob = await (await fetch(src)).blob()
        if (!/^image\/(jpeg|png|gif|webp)$/.test(blob.type)) blob = await rasterize(src)
        if (!blob || blob.size > MAX_IMAGE_BYTES) return
        const description = (el.getAttribute('alt') || el.getAttribute('title')
            || el.closest('figure')?.querySelector('figcaption')?.textContent || '').trim()
        report('image', {
            data: base64(new Uint8Array(await blob.arrayBuffer())),
            type: blob.type,
            description: description || null,
        })
    } catch (e) {
        report('error', { message: 'image: ' + String(e?.stack ?? e) })
    } finally {
        openingImage = false
    }
}

// Set by a long press that opened a picture, so the click that follows it does nothing.
let longPressed = false
const listenForLongPresses = doc => {
    let timer = 0
    let start = null
    const cancel = () => {
        clearTimeout(timer)
        start = null
    }
    doc.addEventListener('touchstart', e => {
        cancel()
        longPressed = false
        const image = e.touches.length === 1 ? imageAt(e.target) : null
        if (!image) return
        start = { x: e.touches[0].screenX, y: e.touches[0].screenY }
        timer = setTimeout(() => {
            start = null
            longPressed = true
            openImage(image)
        }, LONG_PRESS_MS)
    }, { passive: true })
    doc.addEventListener('touchmove', e => {
        const t = e.touches[0]
        if (start && t && Math.hypot(t.screenX - start.x, t.screenY - start.y) > LONG_PRESS_SLOP) cancel()
    }, { passive: true })
    doc.addEventListener('touchend', cancel, { passive: true })
    doc.addEventListener('touchcancel', cancel, { passive: true })
    // No system menu (or drag) for a held picture: the viewer has Save and Share.
    doc.addEventListener('contextmenu', e => {
        if (imageAt(e.target)) e.preventDefault()
    })
}

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
// Each chapter scrolls on its own. Pulling on past either end drags the chapter with the finger,
// against a growing resistance, and a badge with an arrow grows in the gap it leaves, filling once
// letting go will continue. Letting go short of that springs the chapter back. Letting go past it
// carries the chapter on out the way it was pulled, and the adjacent chapter arrives from the same
// side (rising into place going forward, settling down going back). The page navigates itself and
// reports `continue` { index, forward } first, so the app counts the chapter as read through.

// CSS px of finger travel past the edge; well beyond a scrolling flick's overshoot.
const PULL = 140
// The chapter follows the finger less and less, approaching this many px beyond the system bar (or
// cutout) at that edge: that much travel opens a gap there before any of it is clear of the bar.
const PULL_TRAVEL = 200
const pullOffset = (distance, inset) => PULL_TRAVEL * (1 - Math.exp(-distance / PULL_TRAVEL))
    + inset * Math.min(1, distance / PULL)
// The badge's size (CSS px) and the space it keeps from the text.
const BADGE = 48
const BADGE_MARGIN = 8
// Leaving: how much further the chapter goes; arriving: how far the next one travels into place
// (the reader's chapter reveal travels the same 40).
const EXIT_TRAVEL = 64
const ENTER_TRAVEL = 40
// The Material 3 motion curves: emphasized accelerate to leave, emphasized decelerate to arrive,
// and a decelerating spring-like curve to settle back.
const EASE_EXIT = 'cubic-bezier(0.3, 0, 0.8, 0.15)'
const EASE_ENTER = 'cubic-bezier(0.05, 0.7, 0.1, 1)'
const EASE_SETTLE = 'cubic-bezier(0.2, 0, 0, 1)'

// The part of the page under the system bars or a display cutout, in CSS px (set by the app;
// `env(safe-area-inset-*)` is 0 in an Android WebView).
let safeArea = { top: 0, bottom: 0 }
globalThis.yomuSetSafeArea = area => {
    safeArea = { top: Math.max(0, area?.top ?? 0), bottom: Math.max(0, area?.bottom ?? 0) }
}

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
// True from letting go past the threshold until the next chapter has arrived.
let continuing = false

// Where the chapter is drawn: moved by a pull, and by the move into the next chapter.
const pose = { y: 0, opacity: 1 }
let poseMotion = null
const poseStyle = ({ y, opacity }) => ({ transform: `translateY(${y}px)`, opacity: String(opacity) })
const setPose = next => {
    poseMotion?.cancel()
    poseMotion = null
    Object.assign(pose, next)
    view.style.transform = pose.y ? `translateY(${pose.y}px)` : ''
    view.style.opacity = pose.opacity === 1 ? '' : String(pose.opacity)
}
/** Animates the chapter to [next] (and leaves it there); instant with animations off. */
const animatePose = async (next, duration, easing) => {
    const from = { ...pose }
    setPose(next)
    if (settings.animated === false) return
    const motion = poseMotion = view.animate([poseStyle(from), poseStyle(pose)], { duration, easing })
    try {
        await motion.finished
    } catch {
        // Replaced by a newer pose.
    }
}

const showPull = () => {
    const progress = Math.min(1, pull.distance / PULL)
    const armed = pull.distance >= PULL
    const ink = settings.colors?.ink ?? 'gray'
    const scale = 0.6 + 0.4 * progress
    // Placed in the gap the chapter leaves at the edge it's pulled from (measured from that edge):
    // centred in the part clear of the system bar and cutout once that is big enough, else as far
    // from the edge as the gap allows. Never over the text.
    const inset = pull.dir > 0 ? safeArea.bottom : safeArea.top
    const gap = Math.abs(pose.y)
    const half = BADGE / 2 * scale
    const center = gap - inset >= 2 * (half + BADGE_MARGIN)
        ? (inset + gap) / 2
        : Math.max(gap / 2, gap - half - BADGE_MARGIN)
    pullHint.className = pull.dir > 0 ? 'next' : 'prev'
    pullHint.style.top = pull.dir > 0 ? 'auto' : `${center - BADGE / 2}px`
    pullHint.style.bottom = pull.dir > 0 ? `${center - BADGE / 2}px` : 'auto'
    pullHint.style.transition = 'none'
    pullHint.style.opacity = String(progress)
    pullHint.style.scale = String(scale)
    pullHint.style.color = armed ? (settings.colors?.page ?? 'white') : ink
    pullHint.style.background = armed ? ink : `color-mix(in srgb, ${ink} 16%, transparent)`
}
const hidePull = () => {
    pullHint.style.transition = ''
    pullHint.style.opacity = '0'
}
const settleBack = () => {
    hidePull()
    if (pose.y) animatePose({ y: 0, opacity: 1 }, 350, EASE_SETTLE)
}

const onPullStart = e => {
    pull.active = !continuing && isScrolled() && e.touches.length === 1
    pull.dir = 0
    pull.distance = 0
    pull.last = e.touches[0]?.screenY ?? 0
}
const onPullMove = e => {
    if (!pull.active) return
    if (e.touches.length > 1) {
        pull.active = false
        pull.dir = 0
        settleBack()
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
    // The chapter is being dragged, not scrolled.
    if (e.cancelable) e.preventDefault()
    pull.distance = Math.max(0, (pull.from - y) * pull.dir)
    setPose({ y: -pull.dir * pullOffset(pull.distance, pull.dir > 0 ? safeArea.bottom : safeArea.top), opacity: 1 })
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
    const dir = pull.dir
    const index = dir && pull.distance >= PULL ? adjacentIndex(dir) : null
    pull.dir = 0
    if (index != null) continueInto(index, dir)
    else settleBack()
}
// The system took the gesture (say, a back swipe): never continue.
const onPullCancel = () => {
    if (!pull.active) return
    pull.active = false
    pull.dir = 0
    settleBack()
}

/** Carries on out of this chapter (the way it was pulled) and brings in section [index]. */
const continueInto = async (index, dir) => {
    continuing = true
    hidePull()
    report('continue', { index, forward: dir > 0 })
    try {
        await animatePose({ y: pose.y - dir * EXIT_TRAVEL, opacity: 0 }, 150, EASE_EXIT)
        await view.renderer.goTo({ index, anchor: dir > 0 ? 0 : 1 })
        await settled()
        setPose({ y: dir * ENTER_TRAVEL, opacity: 0 })
        await animatePose({ y: 0, opacity: 1 }, 450, EASE_ENTER)
    } catch (e) {
        report('error', { message: 'continue: ' + String(e?.stack ?? e) })
    } finally {
        setPose({ y: 0, opacity: 1 })
        continuing = false
    }
}

const listenForPulls = target => {
    target.addEventListener('touchstart', onPullStart, { passive: true })
    // Not passive: while the chapter is dragged, the page mustn't scroll as well.
    target.addEventListener('touchmove', onPullMove, { passive: false })
    target.addEventListener('touchend', onPullEnd, { passive: true })
    target.addEventListener('touchcancel', onPullCancel, { passive: true })
}
// Below a short chapter the touches land on this page rather than the chapter's.
listenForPulls(document)
view.addEventListener('load', ({ detail: { doc } }) => listenForPulls(doc))
view.addEventListener('load', ({ detail: { doc } }) => applyAlignment(doc))

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
    // A new reading font is loaded first, so the restyle lands with it; otherwise (a theme, an
    // alignment) it all lands in this task, in one frame.
    if (JSON.stringify(next.font) !== JSON.stringify(settings.font)) await preloadFonts(next)
    settings = next
    applyPageColor()
    if (view.renderer) configure(view.renderer)
    applyAlignmentEverywhere()
    dropPreview()
    // Turned dark (or adapting turned on): mark what's already on the page.
    for (const { doc, index } of view.renderer?.getContents() ?? []) markPictures(doc, index)
}

/** Forgets the laid-out preview (the settings or the page size changed); the next relocate makes another. */
const dropPreview = () => {
    const p = preview
    if (!p || p.holding) return
    preview = null
    try {
        p.el.destroy()
    } catch {
        // It never loaded a section.
    }
    p.el.remove()
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

/**
 * The anchor for [progress] of a section in the terms `relocate` reports it: the last page in
 * view's (page + 1) / pages when paged (two pages to a spread), how far through the scrollable
 * range when scrolled. Decided once the section is laid out.
 */
const progressAnchor = progress => () => {
    const r = view.renderer
    const p = Math.min(1, Math.max(0, progress))
    if (r.scrolled) {
        const range = r.viewSize - r.size
        return range > 1 && r.viewSize > 0 ? p * range / r.viewSize : 0
    }
    const textScreens = Math.max(1, r.pages - 2)
    if (textScreens <= 1) return 0
    const perScreen = columns()
    const pages = textScreens * perScreen
    const page = Math.min(pages - 1, Math.max(0, Math.round(p * pages - 1)))
    return Math.floor(page / perScreen) / (textScreens - 1)
}

const goTo = target => {
    if (target?.progress != null) return view.renderer.goTo({ index: target.index ?? 0, anchor: progressAnchor(target.progress) })
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
    view.renderer.addEventListener('scroll', followTurn)
    if (typeof view.renderer.snap === 'function') guardTurns(view.renderer)
    report('view-open')
    const pages = pageList(book)
    if (pages.length) report('page-list', { pages })
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
    new FontFace(s.font.family, `url("${fontURL(f.src)}")`, { style: f.style ?? 'normal', weight: f.weight ?? 'normal' })
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
    // Reveal only the final layout: no reflow after the page shows, and pictures already as they'll
    // stay (decided within the budget; any still undecided stay hidden until they are).
    await settled()
    await within(picturesOnPage(), PICTURES_BUDGET_MS)
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
