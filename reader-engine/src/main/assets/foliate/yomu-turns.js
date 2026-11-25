// The page side of page turns. Turns are drawn natively by the app (FoliatePageTurns.kt) from a
// snapshot of the page; here foliate only jumps between pages, never animating, and says when each
// new page has been painted so the app knows the snapshot can come off. foliate's own touch
// scrolling is switched off: the app owns horizontal drags.

const painted = () => new Promise(resolve =>
    requestAnimationFrame(() => requestAnimationFrame(resolve)))

export const installPageTurns = (view, report) => {
    let standIn = null
    let section = null // the chapter on screen
    let jumping = false

    /** Resolves on the next relocate that [accept]s it, or false after a timeout. */
    const relocated = accept => new Promise(resolve => {
        const timer = setTimeout(() => {
            view.removeEventListener('relocate', on)
            resolve(false)
        }, 3000)
        const on = ({ detail }) => {
            if (!accept(detail)) return
            clearTimeout(timer)
            view.removeEventListener('relocate', on)
            resolve(true)
        }
        view.addEventListener('relocate', on)
    })

    let restoring = null // the real view catching up behind the stand-in

    /** Jumps one page in [dir] and reports `jumped` with [id] once that page is on screen. */
    globalThis.yomuJump = async (dir, id) => {
        await restoring
        const r = view.renderer
        if (!r || (dir > 0 ? r.atEnd : r.atStart)) return report('jumped', { id, moved: false })
        jumping = true
        const t = performance.now()
        const from = section
        // At a chapter's edge foliate first scrolls onto a blank padding page, then loads the next
        // chapter: only a relocate in the new chapter means the jump is done.
        const crossing = dir > 0 ? r.page >= r.pages - 2 : r.page <= 1
        const covered = crossing && standIn?.ready(from, dir)
        const moved = relocated(detail => !crossing || detail.section?.current !== from)
        if (covered) {
            // The stand-in already shows the page: the turn can go now, while the real view loads
            // the chapter behind it.
            view.style.visibility = 'hidden'
            if (dir > 0) view.next()
            else view.prev()
            await painted()
            report('jumped', { id, moved: true, crossing, covered: true, took: Math.round(performance.now() - t) })
            restoring = moved.then(painted).then(() => {
                view.style.visibility = ''
                return painted()
            }).then(() => {
                restoring = null
                jumping = false
                report('restored', { took: Math.round(performance.now() - t) })
                if (standIn && section != null) standIn.follow({ index: section })
            })
            return
        }
        if (dir > 0) view.next()
        else view.prev()
        const ok = await moved
        await painted()
        jumping = false
        report('jumped', { id, moved: ok, crossing, covered: false, took: Math.round(performance.now() - t) })
        if (standIn && section != null) standIn.follow({ index: section })
    }

    // Every settled page is reported once painted, so the app can snapshot it ahead of a turn.
    let seq = 0
    view.addEventListener('relocate', ({ detail }) => {
        section = detail.section?.current ?? section
        const mine = ++seq
        painted().then(() => {
            if (mine !== seq) return
            report('settled', { seq: mine })
            if (standIn && !jumping) standIn.follow({ index: section })
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

    return { setStandIn: x => (standIn = x) }
}
