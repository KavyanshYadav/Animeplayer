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
package dev.animeplayer.tv.ui.player.panel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.ui.components.TvListItem
import dev.animeplayer.tv.ui.focus.requestTvFocus
import dev.animeplayer.tv.ui.player.subtitles.SubtitleColor
import dev.animeplayer.tv.ui.player.subtitles.SubtitleEdge
import dev.animeplayer.tv.ui.player.subtitles.SubtitleEdgeStrength
import dev.animeplayer.tv.ui.player.subtitles.SubtitleFont
import dev.animeplayer.tv.ui.player.subtitles.SubtitleOverlay
import dev.animeplayer.tv.ui.player.subtitles.SubtitlePlate
import dev.animeplayer.tv.ui.player.subtitles.SubtitlePosition
import dev.animeplayer.tv.ui.player.subtitles.SubtitlePreset
import dev.animeplayer.tv.ui.player.subtitles.SubtitleStyle
import dev.animeplayer.tv.ui.player.subtitles.SubtitleWeight
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shared by Settings and the player so both use the live subtitle renderer. */
@Composable
internal fun SubtitleStylePanel(
    style: SubtitleStyle,
    onStyleChange: ((SubtitleStyle) -> SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
) {
    var editing by rememberSaveable { mutableStateOf<SubtitleField?>(null) }
    var returnTo by rememberSaveable { mutableStateOf<SubtitleField?>(null) }
    val fields = remember(style) { subtitleControls(style) }
    val detailColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
    val fieldFocus = remember { SubtitleField.entries.associateWith { FocusRequester() } }
    val fieldList = rememberLazyListState()
    val open = editing
    val options = remember(open, style) { open?.let { subtitleChoices(it, style) }.orEmpty() }
    var previewIndex by remember(open) { mutableIntStateOf(-1) }
    val preview = options.getOrNull(previewIndex)?.change?.invoke(style) ?: style
    val exitOptions = { editing = null }

    BackHandler(enabled = open != null, onBack = exitOptions)
    LaunchedEffect(open) {
        if (open == null && returnTo != null) {
            val previous = returnTo
            val index = fields.indexOfFirst { it.field == previous }
            if (index >= 0 && fieldList.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                fieldList.scrollToItem(index)
            }
            withFrameNanos { }
            fieldFocus[previous]?.requestTvFocus()
            returnTo = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionLeft) {
                false
            } else if (editing != null) {
                exitOptions()
                true
            } else {
                onNavigateLeft?.invoke() == true
            }
        },
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .height(if (LocalTvLayout.current.height < 500.dp) 116.dp else 148.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF15203A), Color(0xFF536584), Color(0xFFE8D9B9)))),
        ) {
            SubtitleOverlay(
                text = "I’ll meet you under the stars.",
                style = preview,
                topPadding = 15.dp,
                bottomPadding = 15.dp,
                modifier = Modifier.matchParentSize().padding(horizontal = 10.dp),
                offsetScale = 0.18f,
            )
        }
        if (open == null) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = fieldList,
                contentPadding = PaddingValues(top = 3.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(fields, key = { it.field.name }) { control ->
                    val first = if (control.field == SubtitleField.Preset && focus != null) {
                        Modifier.focusRequester(focus)
                    } else Modifier
                    TvListItem(
                        title = control.field.label,
                        trailing = control.value,
                        detailColor = detailColor,
                        modifier = first.focusRequester(fieldFocus.getValue(control.field)),
                        onClick = {
                            returnTo = control.field
                            editing = control.field
                        },
                    )
                }
            }
        } else {
            key(open) {
                val selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
                val optionFocus = remember { FocusRequester() }
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
                Text(
                    text = open.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    optionFocus.requestTvFocus()
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(top = 3.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(options.size) { index ->
                        val choice = options[index]
                        TvListItem(
                            title = choice.title,
                            subtitle = choice.description,
                            detailColor = detailColor,
                            selected = choice.selected,
                            modifier = (if (index == selectedIndex) Modifier.focusRequester(optionFocus) else Modifier)
                                .onFocusChanged { if (it.isFocused) previewIndex = index }
                                .semantics { selected = choice.selected },
                            onClick = {
                                onStyleChange(choice.change)
                                exitOptions()
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class SubtitleField(val label: String) {
    Preset("Preset"),
    Font("Typeface"),
    Size("Text size"),
    Weight("Text weight"),
    TextColor("Text color"),
    TextOpacity("Text opacity"),
    Edge("Text edge"),
    EdgeStrength("Edge strength"),
    Plate("Background"),
    PlateColor("Background color"),
    PlateOpacity("Background opacity"),
    Position("Screen position"),
    Offset("Vertical adjustment"),
}

private data class SubtitleControl(val field: SubtitleField, val value: String)
private data class SubtitleChoice(
    val title: String,
    val selected: Boolean,
    val description: String? = null,
    val change: (SubtitleStyle) -> SubtitleStyle,
)

private val presetOrder = listOf(
    SubtitlePreset.Default, SubtitlePreset.AnimeOutline, SubtitlePreset.Cinema,
    SubtitlePreset.ClosedCaptions, SubtitlePreset.HighContrast, SubtitlePreset.Josei,
    SubtitlePreset.Outline, SubtitlePreset.Plate, SubtitlePreset.Karaoke, SubtitlePreset.Sign,
)

private fun subtitleControls(style: SubtitleStyle): List<SubtitleControl> = buildList {
    val defaults = SubtitleStyle(style.preset)
    val preset = if (style == defaults) style.preset.label else "${style.preset.label} · Custom"
    add(SubtitleControl(SubtitleField.Preset, preset))
    add(SubtitleControl(SubtitleField.Font, style.font.label))
    add(SubtitleControl(SubtitleField.Size, "${style.textSize} sp"))
    add(SubtitleControl(SubtitleField.Weight, style.weight.label))
    add(SubtitleControl(SubtitleField.TextColor, style.textColorOverride?.label ?: "Preset default"))
    add(SubtitleControl(SubtitleField.TextOpacity, "${style.textOpacityPercent.coerceIn(0, 100)}%"))
    add(SubtitleControl(SubtitleField.Edge, style.edge.label))
    if (style.edge != SubtitleEdge.None) add(SubtitleControl(SubtitleField.EdgeStrength, style.edgeStrength.label))
    add(SubtitleControl(SubtitleField.Plate, style.plate.label))
    if (style.plate != SubtitlePlate.None) {
        add(SubtitleControl(SubtitleField.PlateColor, style.plateColorOverride?.label ?: "Preset default"))
        add(SubtitleControl(SubtitleField.PlateOpacity, "${(style.plateAlpha * 100).roundToInt()}%"))
    }
    add(SubtitleControl(SubtitleField.Position, style.position.label))
    add(SubtitleControl(SubtitleField.Offset, offsetLabel(signedOffset(style))))
}

private fun subtitleChoices(field: SubtitleField, style: SubtitleStyle): List<SubtitleChoice> =
    when (field) {
        SubtitleField.Preset -> presetOrder.map { preset ->
            SubtitleChoice(preset.label, style.preset == preset, presetDescription(preset)) { SubtitleStyle(preset) }
        }
        SubtitleField.Font -> choices(SubtitleFont.entries, style.font, SubtitleFont::label) { state, value ->
            state.copy(fontOverride = value.takeUnless { it == state.preset.font })
        }
        SubtitleField.Size -> choices(
            ((12..48 step 2).toList() + style.textSize).distinct().sorted(), style.textSize, { "$it sp" },
        ) { state, value -> state.copy(sizeStep = 0, sizeOverrideSp = value.takeUnless { it == state.preset.sizeSp }) }
        SubtitleField.Weight -> choices(SubtitleWeight.entries, style.weight, SubtitleWeight::label) { state, value ->
            state.copy(weight = value)
        }
        SubtitleField.TextColor -> colors(style.textColorOverride) { state, value -> state.copy(textColorOverride = value) }
        SubtitleField.TextOpacity -> choices(percentages(25), style.textOpacityPercent, { "$it%" }) { state, value ->
            state.copy(textOpacityPercent = value)
        }
        SubtitleField.Edge -> choices(SubtitleEdge.entries, style.edge, SubtitleEdge::label) { state, value ->
            state.copy(edgeOverride = value.takeUnless { it == state.preset.edge })
        }
        SubtitleField.EdgeStrength -> choices(
            SubtitleEdgeStrength.entries, style.edgeStrength, SubtitleEdgeStrength::label,
        ) { state, value -> state.copy(edgeStrength = value) }
        SubtitleField.Plate -> choices(SubtitlePlate.entries, style.plate, SubtitlePlate::label) { state, value ->
            state.copy(plateOverride = value.takeUnless { it == state.preset.plate })
        }
        SubtitleField.PlateColor -> colors(style.plateColorOverride) { state, value ->
            state.copy(plateColorOverride = value)
        }
        SubtitleField.PlateOpacity -> choices(
            listOf<Int?>(null) + percentages(0), style.plateOpacityPercent, { it?.let { "$it%" } ?: "Preset default" },
        ) { state, value -> state.copy(plateOpacityPercent = value) }
        SubtitleField.Position -> choices(SubtitlePosition.entries, style.position, SubtitlePosition::label) { state, value ->
            if (state.position == value) state
            else state.copy(positionOverride = value.takeUnless { it == state.preset.position }, positionStep = 0)
        }
        SubtitleField.Offset -> choices((3 downTo -3).toList(), signedOffset(style), ::offsetLabel) { state, value ->
            state.copy(positionStep = if (state.position == SubtitlePosition.Top) -value else value)
        }
    }

private fun <T> choices(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    change: (SubtitleStyle, T) -> SubtitleStyle,
): List<SubtitleChoice> = options.map { value ->
    SubtitleChoice(label(value), value == selected) { style -> change(style, value) }
}

private fun colors(selected: SubtitleColor?, change: (SubtitleStyle, SubtitleColor?) -> SubtitleStyle) =
    choices(listOf<SubtitleColor?>(null) + SubtitleColor.entries, selected, { it?.label ?: "Preset default" }, change)

private fun percentages(start: Int) = ((start..100 step 25).toList() + listOf(40, 60, 85)).sorted()
private fun signedOffset(style: SubtitleStyle): Int =
    style.positionStep.coerceIn(-3, 3) * if (style.position == SubtitlePosition.Top) -1 else 1

private fun offsetLabel(step: Int): String = when {
    step > 0 -> "Higher · ${step * 24} dp"
    step < 0 -> "Lower · ${abs(step) * 24} dp"
    else -> "Default"
}

private fun presetDescription(preset: SubtitlePreset): String = when (preset) {
    SubtitlePreset.Default -> "White Gothic text with a soft background"
    SubtitlePreset.AnimeOutline -> "Rounded white text with a crisp outline"
    SubtitlePreset.Cinema -> "Clean white text and a shadow over the picture"
    SubtitlePreset.ClosedCaptions -> "White text on a dark background"
    SubtitlePreset.HighContrast -> "Large gold text on a dark background"
    SubtitlePreset.Josei -> "Small rounded subtitles at the top"
    SubtitlePreset.Plate -> "Original white text on a solid background"
    SubtitlePreset.Outline -> "Original Gothic text with a crisp outline"
    SubtitlePreset.Sign -> "Large text in the middle of the screen"
    SubtitlePreset.Karaoke -> "Rounded gold text on a dark background"
}
