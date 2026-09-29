package com.stremio.mobile.presentation.tv

internal sealed interface TvSearchKeyAction {
    data class Letter(val value: Char) : TvSearchKeyAction
    data object Space : TvSearchKeyAction
    data object Backspace : TvSearchKeyAction
    data object Clear : TvSearchKeyAction
}

internal fun reduceTvSearchQuery(query: String, action: TvSearchKeyAction): String = when (action) {
    is TvSearchKeyAction.Letter -> query + action.value
    TvSearchKeyAction.Space -> if (query.isEmpty() || query.last().isWhitespace()) query else "$query "
    TvSearchKeyAction.Backspace -> {
        val count = query.codePointCount(0, query.length)
        if (count == 0) query else query.substring(0, query.offsetByCodePoints(0, count - 1))
    }
    TvSearchKeyAction.Clear -> ""
}

internal fun shouldRunTvSearch(query: String): Boolean =
    query.trim().codePointCount(0, query.trim().length) >= 2

internal val tvSearchLetterRows = listOf(
    "QWERTYUIOP".toList(),
    "ASDFGHJKL".toList(),
    "ZXCVBNM".toList(),
)

internal fun adjacentKeyboardKey(row: Int, column: Int, direction: Int): Pair<Int, Int>? {
    val nextRow = row + direction
    if (nextRow !in tvSearchLetterRows.indices) return null
    val sourceSize = tvSearchLetterRows[row].size
    val targetSize = tvSearchLetterRows[nextRow].size
    val targetColumn = if (sourceSize <= 1) 0 else
        (column.toFloat() * (targetSize - 1) / (sourceSize - 1)).toInt().coerceIn(0, targetSize - 1)
    return nextRow to targetColumn
}
