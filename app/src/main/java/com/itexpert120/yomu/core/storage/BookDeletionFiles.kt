package com.itexpert120.yomu.core.storage

import java.io.File
import java.util.UUID

/** Staged names retain the original path so the committed library can decide recovery. */
internal object BookDeletionFiles {
    private val stagedName = Regex("^\\.(.+)\\.[0-9a-fA-F-]{36}\\.deleting$")

    fun stage(files: List<File>, rename: (File, File) -> Boolean = File::renameTo): List<Pair<File, File>> {
        val staged = mutableListOf<Pair<File, File>>()
        try {
            files.distinctBy { it.absolutePath }.filter { it.exists() }.forEach { original ->
                val trash = File(original.parentFile, ".${original.name}.${UUID.randomUUID()}.deleting")
                check(rename(original, trash)) { "Couldn't stage ${original.name} for deletion" }
                staged += original to trash
            }
            return staged
        } catch (failure: Throwable) {
            restore(staged)
            throw failure
        }
    }

    fun restore(staged: List<Pair<File, File>>) {
        // Failed restores retain their reversible name for the next database open.
        staged.asReversed().forEach { (original, trash) ->
            if (!original.exists() && trash.exists()) trash.renameTo(original)
        }
    }

    fun recover(directories: List<File>, livePaths: Set<String>) {
        directories.forEach { directory ->
            directory.listFiles()?.filter { it.isFile }?.forEach file@{ trash ->
                val name = stagedName.matchEntire(trash.name)?.groupValues?.get(1) ?: return@file
                val original = File(directory, name)
                if (original.absolutePath in livePaths) {
                    check(original.exists() || trash.renameTo(original)) {
                        "Couldn't restore library file ${original.name}; staged copy retained"
                    }
                } else {
                    trash.delete()
                }
            }
        }
    }
}
