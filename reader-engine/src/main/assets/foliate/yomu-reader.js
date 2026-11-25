// Open Reader's glue around foliate-js: the book is read file by file from the app (never as one
// big blob), styled from the app's reader settings, and every position is reported to Kotlin,
// which also draws the page turns.
//
// The app passes `config` (JSON) in the URL: { start, settings }. `start` is { cfi } | { href } |
// { index, fraction }; `settings` is described at readerCSS. Later changes come through
// yomuApplySettings / yomuGoTo.
import './view.js'
import { EPUB } from './epub.js'
import { installPageTurns } from './yomu-turns.js'
import { installStandIn } from './yomu-standin.js'

const host = globalThis.YomuHost
const t0 = performance.now()
const report = (name, detail = {}) => {
    const payload = JSON.stringify({ ...detail, ms: Math.round(performance.now() - t0) })
    if (host) host.event(name, payload)
    else console.log(name, payload)
}

const params = new URLSearchParams(location.search)
const config = params.has('config') ? JSON.parse(params.get('config')) : {}
const start = config.start ?? { index: parseInt(params.get('section') ?? '0', 10), fraction: 0 }
let settings = config.settings ?? {}

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
    // No 'animated': foliate jumps between pages and the app draws the turn.
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
const turns = installPageTurns(view, report)
let standIn = null

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
        if (paged && x < innerWidth / 3) report('tap', { dir: -1 })
        else if (paged && x > innerWidth * 2 / 3) report('tap', { dir: 1 })
        else report('center-tap')
    })
})

view.addEventListener('relocate', ({ detail }) => {
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
    report('relocate', {
        index: detail.section?.current,
        fraction: Math.min(1, Math.max(0, fraction)),
        total: detail.fraction,
        cfi: detail.cfi,
        page,
        pages: textPages,
        scrolled,
        title: detail.tocItem?.label ?? null,
    })
})

globalThis.yomuApplySettings = next => {
    settings = next
    applyPageColor()
    if (view.renderer) configure(view.renderer)
    standIn?.refresh()
}

const goTo = target => {
    if (target?.cfi) return view.goTo(target.cfi)
    if (target?.href) return view.goTo(target.href)
    return view.renderer.goTo({ index: target?.index ?? 0, anchor: target?.fraction ?? 0 })
}

globalThis.yomuGoTo = target => goTo(target).catch(e => report('error', { message: String(e?.stack ?? e) }))

const open = async () => {
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
    configure(view.renderer)
    try {
        await goTo(start)
    } catch (e) {
        // A position that no longer resolves (say, a stale CFI) opens the chapter instead.
        report('error', { message: 'start: ' + String(e?.stack ?? e) })
        await view.renderer.goTo({ index: start?.index ?? 0, anchor: 0 })
    }
    if (!params.has('nostandin')) {
        standIn = installStandIn(view, book, configure)
        turns.setStandIn(standIn)
    }
}

open().catch(e => report('error', { message: String(e?.stack ?? e) }))
