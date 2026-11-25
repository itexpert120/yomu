// Keeps the chapter you're about to enter already laid out. A second foliate paginator sits behind
// the real one, showing the next chapter's first page (or, on a chapter's first page, the previous
// chapter's last). When a turn crosses into it, the real view is hidden for the moment it takes to
// load that chapter, and the stand-in shows through: same book, same styles, same size, so the
// swap can't be seen. It shares foliate's section cache, so the real view's load is cheaper too.
import './paginator.js'

export const installStandIn = (view, book, configure) => {
    const standIn = document.createElement('foliate-paginator')
    standIn.className = 'yomu-stand-in'
    standIn.setAttribute('aria-hidden', 'true')
    view.before(standIn)
    standIn.open(book)
    configure(standIn)

    let shown = null // { index, anchor } once laid out
    let wanted = null
    let busy = false

    const adjacent = (index, dir) => {
        for (let i = index + dir; i >= 0 && i < book.sections.length; i += dir)
            if (book.sections[i]?.linear !== 'no') return i
        return null
    }

    const show = async target => {
        wanted = target
        if (busy) return
        busy = true
        while (wanted && (wanted.index !== shown?.index || wanted.anchor !== shown?.anchor)) {
            const next = wanted
            shown = null
            await standIn.goTo(next).catch(() => {})
            shown = next
        }
        busy = false
    }

    /** Called as the real view settles: get the chapter the next turn could enter ready. */
    const follow = ({ index }) => {
        const r = view.renderer
        if (!r || index == null) return
        const atFirstPage = r.page <= 1
        const target = atFirstPage ? adjacent(index, -1) : adjacent(index, 1)
        if (target == null) return
        show({ index: target, anchor: atFirstPage ? 1 : 0 })
    }

    /** Whether a turn from [index] in [dir] enters the chapter the stand-in is showing. */
    const ready = (index, dir) => {
        const target = adjacent(index, dir)
        return target != null && shown?.index === target && shown.anchor === (dir > 0 ? 0 : 1)
    }

    /** Settings changed: restyle the stand-in too; it keeps its place in its chapter. */
    const refresh = () => configure(standIn)

    return { follow, ready, refresh }
}
