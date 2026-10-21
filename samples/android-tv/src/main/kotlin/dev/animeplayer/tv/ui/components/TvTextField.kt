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
package dev.animeplayer.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.focus.TvFocus

/**
 * A search field.
 *
 * The Material 3 drop-down of the desktop client has no place on a television: a
 * drop-down is a mouse shape. The search of the television client is a field that opens
 * the on-screen keyboard, and the filters are chips that open a full screen list.
 *
 * The field has two faces. While the user is not typing it is a plain control, so the
 * D-pad moves through it like through any other control. When the user presses the
 * button on it, the keyboard opens and the field becomes a real text field.
 *
 * @param value the text of the field.
 * @param onValueChange called with the new text.
 * @param placeholder the text shown while the field is empty.
 * @param onSearch called when the user presses the search key.
 * @param onEditingChanged called when the keyboard opens or closes.
 */
@Composable
public fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Type a title",
    onSearch: () -> Unit = {},
    onClear: (() -> Unit)? = null,
    onEditingChanged: ((editing: Boolean) -> Unit)? = null,
) {
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val inputRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(10.dp)
    val accent = MaterialTheme.colorScheme.primary
    var focused by remember { mutableStateOf(false) }

    // The keyboard opens as soon as the field accepts the input.
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { inputRequester.requestFocus() }
            keyboard?.show()
            onEditingChanged?.invoke(true)
        }
    }

    Surface(
        onClick = { editing = true },
        modifier = modifier
            .graphicsLayer {
                scaleX = if (focused) 1.02f else 1f
                scaleY = if (focused) 1.02f else 1f
            },
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(focused = 1.02f),
        glow = TvFocus.glow(accent),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIcon(icon = Icons.Filled.Search, size = 20.dp)

            if (editing) {
                Box(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            editing = false
                            onSearch()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inputRequester),
                    )
                }
            } else {
                Text(
                    text = value.ifEmpty { placeholder },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .onFocusChanged { focused = it.isFocused },
                )
            }

            if (value.isNotEmpty() && onClear != null) {
                TvIconButton(
                    icon = Icons.Filled.Close,
                    onClick = onClear,
                    contentDescription = "Clear",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
