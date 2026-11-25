// The page side of page turns. Turns are drawn natively by the app (FoliatePageTurns.kt) from a
// snapshot of the page; here foliate only jumps between pages, never animating, and says when each
// new page has been painted so the app knows the snapshot can come off. foliate's own touch
// scrolling is switched off: the app owns horizontal drags.

const painted = () => new Promise(resolve =>
    requestAnimationFrame(() => requestAnimationFrame(resolve)))

export const installPageTurns = (view, report) => {
    const relocated = () => new Promise(resolve => {
        const timer = setTimeout(() => resolve(false), 2000)
        view.addEventListener('relocate', () => {
            clearTimeout(timer)
            resolve(true)
        }, { once: true })
    })

    /** Jumps one page in [dir] and reports `jumped` with [id] once that page is on screen. */
    globalThis.yomuJump = async (dir, id) => {
        const r = view.renderer
        if (!r || (dir > 0 ? r.atEnd : r.atStart)) return report('jumped', { id, moved: false })
        const moved = relocated()
        if (dir > 0) view.next()
        else view.prev()
        const ok = await moved
        await painted()
        report('jumped', { id, moved: ok })
    }

    // Every settled page is reported once painted, so the app can snapshot it ahead of a turn.
    let seq = 0
    view.addEventListener('relocate', () => {
        const mine = ++seq
        painted().then(() => {
            if (mine === seq) report('settled', { seq: mine })
        })
    })

    // Capturing at each window runs before foliate's own handlers, which never see a touch.
    const block = e => e.stopPropagation()
    const listen = target => {
        for (const type of ['touchstart', 'touchmove', 'touchend', 'touchcancel'])
            target.addEventListener(type, block, { capture: true })
    }
    listen(window)
    view.addEventListener('load', ({ detail: { doc } }) => listen(doc.defaultView))
}
