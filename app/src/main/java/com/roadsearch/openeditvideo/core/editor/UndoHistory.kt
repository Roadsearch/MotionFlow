package com.roadsearch.openeditvideo.core.editor

/**
 * Bounded undo/redo history of immutable snapshots.
 * Recording a new snapshot invalidates the redo branch, exactly like the editor's history.
 */
class UndoHistory<T>(private val maxSize: Int) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Stores the state as it was BEFORE an edit. */
    fun record(snapshot: T) {
        undoStack.addLast(snapshot)
        if (undoStack.size > maxSize) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Returns the state to restore, keeping [current] for redo; null when there is nothing to undo. */
    fun undo(current: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        return undoStack.removeLast()
    }

    /** Returns the state to restore, keeping [current] for undo; null when there is nothing to redo. */
    fun redo(current: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        return redoStack.removeLast()
    }

    /** Drops the latest recorded snapshot (cancelled gesture) and returns it so the caller can restore it. */
    fun discardLast(): T? {
        if (undoStack.isEmpty()) return null
        val snapshot = undoStack.removeLast()
        redoStack.clear()
        return snapshot
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
