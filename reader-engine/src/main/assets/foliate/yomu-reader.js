// Open Reader's glue around foliate-js: the book is read file by file from the app (never as one
// big blob), styled like the reader's settings, and every milestone is reported to Kotlin.
import './view.js'
import { EPUB } from './epub.js'

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

// foliate's own defaults (its demo reader): the book's fonts and layout, 1.4 line spacing, justified
// and hyphenated, on the light page.
const readerCSS = ({ spacing, justify, hyphenate }) => `
    @namespace epub "http://www.idpf.org/2007/ops";
    /* foliate's demo also allows dark here; books that hard-code dark text then read dark on
       dark, so the prototype stays on the light page until themes exist. */
    html {
        color-scheme: light;
    }
    p, li, blockquote, dd {
        line-height: ${spacing};
        text-align: ${justify ? 'justify' : 'start'};
        -webkit-hyphens: ${hyphenate ? 'auto' : 'manual'};
        hyphens: ${hyphenate ? 'auto' : 'manual'};
        -webkit-hyphenate-limit-before: 3;
        -webkit-hyphenate-limit-after: 2;
        -webkit-hyphenate-limit-lines: 2;
        hanging-punctuation: allow-end last;
        widows: 2;
    }
    /* prevent the above from overriding the align attribute */
    [align="left"] { text-align: left; }
    [align="right"] { text-align: right; }
    [align="center"] { text-align: center; }
    [align="justify"] { text-align: justify; }

    pre {
        white-space: pre-wrap !important;
    }
    aside[epub|type~="endnote"],
    aside[epub|type~="footnote"],
    aside[epub|type~="note"],
    aside[epub|type~="rearnote"] {
        display: none;
    }
`

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
    // Taps in the outer thirds turn the page; the middle is left for the app's menu.
    doc.addEventListener('click', e => {
        if (doc.getSelection()?.type === 'Range') return
        // The chapter frame is wider than the screen and scrolled from outside, so use screen x.
        const x = e.screenX
        const width = innerWidth
        if (x < width / 3) view.prev()
        else if (x > width * 2 / 3) view.next()
        else host?.event('center-tap', '{}')
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
    renderer.setAttribute('animated', '')
    renderer.setStyles(readerCSS({ spacing: 1.4, justify: true, hyphenate: true }))
    await view.goTo(section)
}

open().catch(e => report('error', { message: String(e?.stack ?? e) }))
