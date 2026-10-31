package com.vikash.ampli.drive.messages

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Plays queued messages one after another. Every message job runs under one parent
 * [SupervisorJob], so [cancelAll] drops the whole queue at once (the one speaking and every one
 * still waiting), not just the newest. A fresh parent is made afterwards for later messages.
 */
class MessageQueue(private val context: CoroutineContext) {
    private var parent = SupervisorJob()
    private var scope = CoroutineScope(context + parent)
    private var tail: Job? = null

    /** Messages queued or being spoken right now. */
    val pending: Int get() = parent.children.count { it.isActive }

    fun enqueue(block: suspend () -> Unit) {
        val previous = tail
        tail = scope.launch {
            previous?.join()
            block()
        }
    }

    /** Cancels every queued message. Returns true when any was waiting or being spoken. */
    fun cancelAll(): Boolean {
        val hadAny = pending > 0
        parent.cancel()
        parent = SupervisorJob()
        scope = CoroutineScope(context + parent)
        tail = null
        return hadAny
    }
}
