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
package dev.animeplayer.core.model

/**
 * How the caller wants a rendition to be picked when an episode offers several.
 */
public sealed interface QualityPreference {
    /** The highest available quality. */
    public data object Best : QualityPreference

    /** The lowest available quality. */
    public data object Worst : QualityPreference

    /** The exact pixel height, for example `720`. */
    public data class Height(val height: Int) : QualityPreference

    /** A regular expression matched against [StreamVariant.quality] label. */
    public data class Label(val pattern: Regex) : QualityPreference
}

/**
 * Applies a [QualityPreference] to a list of variants.
 *
 * The list is expected to be ordered best first. When the preference cannot be satisfied the
 * best available variant is returned instead — callers are told about the fallback through
 * [QualitySelection.wasSatisfied].
 */
public object QualitySelector {
    public fun select(
        variants: List<StreamVariant>,
        preference: QualityPreference,
    ): QualitySelection {
        if (variants.isEmpty()) return QualitySelection(null, false)
        val sorted = variants.sortedByDescending { it.quality.height }

        val chosen: StreamVariant? = when (preference) {
            QualityPreference.Best -> sorted.first()
            QualityPreference.Worst -> sorted.last()
            is QualityPreference.Height -> sorted.firstOrNull { it.quality.height == preference.height }
            is QualityPreference.Label -> sorted.firstOrNull { preference.pattern.containsMatchIn(it.quality.label) }
        }

        return if (chosen != null) {
            QualitySelection(chosen, true)
        } else {
            QualitySelection(sorted.first(), false)
        }
    }
}

/**
 * The outcome of a quality selection.
 *
 * @property variant the selected variant, or `null` when no variants were available.
 * @property wasSatisfied `false` when the requested quality was unavailable and the best
 *   available rendition was used as a fallback.
 */
public data class QualitySelection(
    public val variant: StreamVariant?,
    public val wasSatisfied: Boolean,
)
