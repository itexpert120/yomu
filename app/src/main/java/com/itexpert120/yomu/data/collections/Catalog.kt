package com.itexpert120.yomu.data.collections

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookCollection
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.core.model.ShelfStatus
import com.itexpert120.yomu.core.model.shelfStatus

/** One book in the library with the ids of the collections it is in. */
data class CatalogEntry(val book: Book, val collectionIds: Set<String>) {
    /** Whether this book is listed under the library tab [key]: All, its shelf status, or a collection it is in. */
    fun belongsTo(key: String): Boolean {
        if (key == LibraryTabKeys.ALL) return true
        LibraryTabKeys.status(key)?.let { return book.shelfStatus == it }
        return LibraryTabKeys.collectionId(key)?.let { it in collectionIds } ?: false
    }
}

/**
 * The library as one in-memory value: every book with its collections, plus the collections in
 * order. Library, Book Details and Organize all read from it, so tabs, counts and the Organize
 * projection are computed one way.
 */
class Catalog(val entries: List<CatalogEntry>, val collections: List<BookCollection>) {
    private val byId: Map<String, CatalogEntry> by lazy { entries.associateBy { it.book.id.value } }

    fun entry(id: BookId): CatalogEntry? = byId[id.value]

    /**
     * How the books [ids] look in the Organize sheet: one status choice ([StatusChoice.Mixed] when
     * they disagree) and, per collection, whether all, some or none of them are in it. Unknown
     * ids are ignored.
     */
    fun organize(ids: Collection<BookId>): Organize {
        val selected = ids.mapNotNull { entry(it) }
        return Organize(
            status = StatusChoice.of(selected.map { it.book.manualStatus }),
            collections = collections.map { collection ->
                val inIt = selected.count { collection.id in it.collectionIds }
                val membership = when {
                    inIt == 0 -> Membership.None
                    inIt == selected.size -> Membership.All
                    else -> Membership.Some
                }
                OrganizeCollection(collection.id, collection.name, membership)
            },
        )
    }

    /**
     * The library tabs for a device's [order] and [hidden] tabs, with [selected] falling back to All
     * when its tab no longer shows (a collection deleted here or on a synced device, or a hidden tab).
     */
    fun tabs(order: List<String>, hidden: Set<String>, selected: String): LibraryTabs {
        val all = allTabs(order)
        val visible = all.filter { tab ->
            val hiddenByUser = isHideableTab(tab.key) && tab.key in hidden
            // On hold / Dropped show only while they hold a book or are open, so emptying one
            // doesn't yank it away mid-use.
            !hiddenByUser && (!isOptionalTab(tab.key) || tab.count > 0 || tab.key == selected)
        }
        val selectedKey = selected.takeIf { key -> visible.any { it.key == key } } ?: LibraryTabKeys.ALL
        return LibraryTabs(all = all, visible = visible, selected = selectedKey)
    }

    /**
     * Every tab, shown or not, in the user's [order]: All and the status tabs then collections by
     * default, with tabs the order doesn't mention (a new collection) keeping their default place.
     */
    private fun allTabs(order: List<String>): List<LibraryTab> {
        fun count(key: String) = entries.count { it.belongsTo(key) }
        val defaults = StatusTabs.map { (key, status) -> LibraryTab(key, status?.label ?: "All", count(key), status = status) } +
            collections.map { c ->
                val key = LibraryTabKeys.collection(c.id)
                LibraryTab(key, c.name, count(key), isCollection = true)
            }
        val rank = order.withIndex().associate { (index, key) -> key to index }
        return defaults.withIndex()
            .sortedWith(compareBy({ rank[it.value.key] ?: (order.size + it.index) }, { it.index }))
            .map { it.value }
    }

    companion object {
        val Empty = Catalog(emptyList(), emptyList())
    }
}

/** A library tab: All, a shelf status ([status]) or one of the user's collections. */
data class LibraryTab(
    val key: String,
    val label: String,
    val count: Int,
    val isCollection: Boolean = false,
    val status: ShelfStatus? = null,
) {
    val collectionId: String? get() = LibraryTabKeys.collectionId(key)
    val hideable: Boolean get() = isHideableTab(key)
    val showsOnlyWhenFilled: Boolean get() = isOptionalTab(key)
}

/** Every tab in order ([all], for the tab editor) and the ones the strip shows. */
data class LibraryTabs(val all: List<LibraryTab>, val visible: List<LibraryTab>, val selected: String)

/** Stable tab keys; they are stored device-locally (last tab, order, hidden tabs). */
object LibraryTabKeys {
    const val ALL = "all"
    const val READING = "reading"
    const val TO_READ = "toRead"
    const val FINISHED = "finished"
    const val ON_HOLD = "onHold"
    const val DROPPED = "dropped"
    private const val COLLECTION_PREFIX = "c:"

    fun collection(id: String) = COLLECTION_PREFIX + id

    /** The collection id a tab key refers to, or null for All and status tabs. */
    fun collectionId(key: String): String? = if (key.startsWith(COLLECTION_PREFIX)) key.substring(COLLECTION_PREFIX.length) else null

    /** The shelf status a tab key shows, or null for All and collection tabs. */
    fun status(key: String): ShelfStatus? = StatusTabs.firstOrNull { it.first == key }?.second
}

/** The built-in tabs (All, then each shelf status), in their default order. */
private val StatusTabs: List<Pair<String, ShelfStatus?>> = listOf(
    LibraryTabKeys.ALL to null,
    LibraryTabKeys.READING to ShelfStatus.Reading,
    LibraryTabKeys.TO_READ to ShelfStatus.ToRead,
    LibraryTabKeys.FINISHED to ShelfStatus.Finished,
    LibraryTabKeys.ON_HOLD to ShelfStatus.OnHold,
    LibraryTabKeys.DROPPED to ShelfStatus.Dropped,
)

/** Status tabs the user may turn off; All always stays. */
fun isHideableTab(key: String) = key != LibraryTabKeys.ALL && StatusTabs.any { it.first == key }

/** On hold / Dropped only show while they hold a book. */
private fun isOptionalTab(key: String) = key == LibraryTabKeys.ON_HOLD || key == LibraryTabKeys.DROPPED

/** The Organize sheet's view of some books. */
data class Organize(val status: StatusChoice, val collections: List<OrganizeCollection>)

/** Whether all, some, or none of the organized books are in a collection. */
enum class Membership { None, Some, All }

/** One collection in the Organize sheet. */
data class OrganizeCollection(val id: String, val name: String, val membership: Membership)

/** The status choice for the books being organized; [Mixed] when they disagree (none is checked). */
enum class StatusChoice(val label: String, val status: ManualStatus?) {
    Auto("Auto", null),
    OnHold(ShelfStatus.OnHold.label, ManualStatus.OnHold),
    Dropped(ShelfStatus.Dropped.label, ManualStatus.Dropped),
    Mixed("Mixed", null),
    ;

    companion object {
        val Options = listOf(Auto, OnHold, Dropped)

        fun of(statuses: Collection<ManualStatus?>): StatusChoice {
            val distinct = statuses.toSet()
            if (distinct.size > 1) return Mixed
            return when (distinct.singleOrNull()) {
                ManualStatus.OnHold -> OnHold
                ManualStatus.Dropped -> Dropped
                null -> Auto
            }
        }
    }
}
