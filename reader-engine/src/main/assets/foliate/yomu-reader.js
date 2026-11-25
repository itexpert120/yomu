// Open Reader's glue around foliate-js: the book is read file by file from the app (never as one
// big blob), styled, and every milestone is reported to Kotlin, which also draws the page turns.
import './view.js'
import { EPUB } from './epub.js'
import { installPageTurns } from './yomu-turns.js'

const host = globalThis.YomuHost
const t0 = performance.now()
const report = (name, detail = {}) => {
    const payload = JSON.stringify({ ...detail, ms: Math.round(performance.now() - t0) })
    if (host) host.event(name, payload)
    else console.log(name, payload)
}

const params = new URLSearchParams(location.search)
const section = parseInt(params.get('section') ?? '0', 10)

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

// foliate's default rendering (the book's fonts and layout; justified, hyphenated) with the
// Foliate app's spacing, on Open Reader's page colours. Light keeps the publisher's colours, as
// foliate does; dark has to override them, or books that set dark text read dark on dark.
const readerCSS = ({ lineHeight, justify, hyphenate }) => `
    @namespace epub "http://www.idpf.org/2007/ops";
    html {
        color-scheme: light dark;
        --page: #FFFFFF;
        --ink: #16191C;
        --link: #2F5BB7;
        line-height: ${lineHeight};
        orphans: 2;
        widows: 2;
        hanging-punctuation: allow-end last;
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
    p, li, blockquote, dd {
        line-height: ${lineHeight};
        text-align: ${justify ? 'justify' : 'start'};
        hyphens: ${hyphenate ? 'auto' : 'manual'};
    }
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

const view = document.createElement('foliate-view')
document.body.append(view)
installPageTurns(view, report)

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
    // Taps in the outer thirds turn the page; the middle is left for the app's menu.
    doc.addEventListener('click', e => {
        if (doc.getSelection()?.type === 'Range') return
        // The chapter frame is wider than the screen and scrolled from outside, so use screen x.
        const x = e.screenX
        const width = innerWidth
        // The app draws the turn.
        if (x < width / 3) report('tap', { dir: -1 })
        else if (x > width * 2 / 3) report('tap', { dir: 1 })
        else report('center-tap')
    })
})
view.addEventListener('relocate', ({ detail }) => {
    if (firstPage) {
        firstPage = false
        // Two frames: the first page has been laid out and painted.
        requestAnimationFrame(() => requestAnimationFrame(() => report('first-page')))
    }
    report('relocate', { index: detail.section?.current, fraction: detail.fraction })
})

const open = async () => {
    report('script')
    const book = await new EPUB(await makeLoader()).init()
    report('book', { sections: book.sections.length })
    await view.open(book)
    const renderer = view.renderer
    renderer.setAttribute('flow', 'paginated')
    // No 'animated': foliate jumps between pages and the app draws the turn.
    renderer.setAttribute('gap', '6%')
    renderer.setStyles(readerCSS({ lineHeight: 1.5, justify: true, hyphenate: true }))
    await view.goTo(section)
}

open().catch(e => report('error', { message: String(e?.stack ?? e) }))
