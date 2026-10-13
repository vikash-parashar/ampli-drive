package com.vikash.ampli.drive.nav

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The next manoeuvre, exactly as Google Maps shows it in its navigation notification.
 * Typically [title] = "450 m", [text] = "Turn right onto NH 48", [subText] = ETA line.
 */
data class NextTurn(
    val title: String,
    val text: String?,
    val subText: String?,
    /** The turn arrow Google Maps draws as the notification's large icon. */
    val arrow: Bitmap?,
    val sourcePackage: String,
)

/** In-process bridge between the notification listener and the home screen. */
object NavTurnRepository {
    private val _turn = MutableStateFlow<NextTurn?>(null)
    val turn: StateFlow<NextTurn?> = _turn.asStateFlow()

    fun post(turn: NextTurn) {
        _turn.value = turn
    }

    fun clear(fromPackage: String? = null) {
        val current = _turn.value ?: return
        if (fromPackage == null || current.sourcePackage == fromPackage) _turn.value = null
    }
}
