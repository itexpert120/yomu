package com.itexpert120.yomu.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Delivery is acknowledged after navigation, never merely by collecting a request. */
internal class PendingBookOpens(initial: List<String> = emptyList(), private val persist: (List<String>) -> Unit = {}) {
    private val pending = MutableStateFlow(initial)
    val state = pending.asStateFlow()
    fun add(bookId: String) = update(pending.value + bookId)
    fun acknowledge(bookId: String) {
        if (pending.value.firstOrNull() == bookId) update(pending.value.drop(1))
    }
    private fun update(next: List<String>) {
        persist(next)
        pending.value = next
    }
}
