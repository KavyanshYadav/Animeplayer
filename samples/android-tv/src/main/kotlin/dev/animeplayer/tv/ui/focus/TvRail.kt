/*
 * Copyright (C) 2026 animeplayer contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. See <https://www.gnu.org/licenses/> for the full text.
 */
package dev.animeplayer.tv.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * A horizontal rail of focusable items.
 *
 * The rail is the horizontal half of the spatial model of a television interface: the
 * D-pad moves from item to item inside the rail, and up and down move from rail to rail.
 *
 * Three details make the difference between a rail that feels native and one that does
 * not:
 *
 *  * the focused item is scrolled fully into view and the next one peeks in, so the user
 *    always knows there is more;
 *  * the rail remembers where the focus was, so coming back from the player does not throw
 *    the user back to the first card;
 *  * the items are reachable with the D-pad alone — there is no scroll bar and no button.
 *
 * @param items the items of the rail.
 * @param key the identity of an item.
 * @param screen the key of the screen, used by the [FocusMemory].
 * @param memory the memory of the focus.
 * @param entryFocusRequester an optional requester for the first item of the rail.
 * @param onFocusChanged called with the item that received the focus, and with `null`
 *   when the focus left the rail.
 */
@Composable
public fun <T> TvRail(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    screen: String? = null,
    memory: FocusMemory? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 48.dp),
    itemSpacing: Dp = 12.dp,
    entryFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onFocusChanged: (T?) -> Unit = {},
    content: @Composable (T, Modifier) -> Unit,
) {
    val listState: LazyListState = rememberLazyListState()
    val requesters = remember { mutableMapOf<Any, FocusRequester>() }
    val currentOnFocusChanged by rememberUpdatedState(onFocusChanged)
    var focusedIndex by remember { mutableIntStateOf(-1) }

    // The focus of the rail is restored when the screen is entered again. The request
    // happens after the first layout, when the requesters are attached.
    LaunchedEffect(screen, items.size) {
        val target = screen?.let { memory?.recall(it) } ?: return@LaunchedEffect
        val requester = requesters.entries.firstOrNull { it.key.toString() == target }?.value
            ?: return@LaunchedEffect
        runCatching { requester.requestFocus() }
    }

    // The rail follows the focus. When the focused item is the last one on the screen, the
    // rail scrolls a little further, so the next item peeks in and the user knows the rail
    // continues.
    LaunchedEffect(listState) {
        snapshotFlow { focusedIndex }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { index ->
                val layout = listState.layoutInfo
                val visible = layout.visibleItemsInfo
                if (visible.isEmpty()) return@collect
                val last = visible.last().index
                if (index == last && last + 1 < layout.totalItemsCount) {
                    listState.animateScrollToItem(last + 1)
                }
            }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        itemsIndexed(items = items, key = { _, item -> key(item) }) { index, item ->
            val itemKey = key(item)
            val requester = remember(itemKey, index, entryFocusRequester) {
                if (index == 0) entryFocusRequester ?: FocusRequester() else FocusRequester()
            }
            requesters[itemKey] = requester

            val itemModifier = Modifier
                .focusRequester(requester)
                .onPreviewKeyEvent { event ->
                    if (index == 0 && event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionUp && onNavigateUp != null
                    ) {
                        onNavigateUp()
                        true
                    } else {
                        false
                    }
                }
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        focusedIndex = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == itemKey }?.index ?: focusedIndex
                        screen?.let { memory?.remember(it, itemKey.toString()) }
                        currentOnFocusChanged(item)
                    } else {
                        currentOnFocusChanged(null)
                    }
                }
            content(item, itemModifier)
        }
    }
}
