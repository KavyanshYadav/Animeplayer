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
package dev.animeplayer.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.data.TvState
import dev.animeplayer.tv.ui.components.TvArtwork
import dev.animeplayer.tv.ui.components.TvBadge
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvButtonRow
import dev.animeplayer.tv.ui.components.TvCard
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvIcon
import dev.animeplayer.tv.ui.components.TvIconButton
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.components.TvProgressBar
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusKeys
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.toCard
import dev.animeplayer.tv.ui.theme.TvDimens

/**
 * The details of a series.
 *
 * The upper half is the catalogue — the artwork, the description, the badges. The lower
 * half is the library — the episodes that can actually be played.
 *
 * @param container the container of the dependencies.
 * @param catalogId the identifier of the series in the catalogue.
 * @param onBack called when the user asks to go back.
 * @param onPlay called with the series and the episode that was picked.
 */
@Composable
public fun DetailScreen(
    container: AppContainer,
    memory: FocusMemory,
    catalogId: String,
    onBack: () -> Unit,
    onPlay: (CatalogCard, String) -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusEntry = FocusEntry(),
    /** Called when the user picks another series — a sequel, a recommendation. */
    onOpen: (CatalogCard) -> Unit = {},
) {
    val model: DetailViewModel = viewModel<DetailViewModel>(
        factory = DetailViewModel.factory(container, catalogId),
    )
    val state by model.state.collectAsStateWithLifecycle()

    LaunchedEffect(catalogId) { model.load() }

    // The play button is the way into the screen. The focus moves there as soon as the
    // series is loaded, because the button only exists then.
    val playRequester = remember { FocusRequester() }
    entry.Register(playRequester)
    LaunchedEffect(state.media) {
        if (state.media is TvState.Ready) {
            runCatching { playRequester.requestFocus() }
        }
    }

    val media = (state.media as? TvState.Ready)?.value

    Box(modifier = modifier.fillMaxSize()) {
        // The banner of the series, behind everything.
        if (media != null) {
            TvArtwork(
                url = media.bannerUrl ?: media.posterUrl,
                tint = media.posterColor?.toColor(),
                contentDescription = media.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.6f to materialThemeBackground(),
                            1.0f to materialThemeBackground(),
                        ),
                    ),
            )
        }

        when {
            state.media is TvState.Failed -> TvErrorState(
                message = (state.media as TvState.Failed).message,
            )

            media == null -> TvLoading(message = "The series is loading.")

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(TvDimens.RailSpacing),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                item(key = "head") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TvDimens.ScreenMargin),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        TvArtwork(
                            url = media.posterUrl ?: media.bannerUrl,
                            tint = media.posterColor?.toColor(),
                            contentDescription = media.title,
                            modifier = Modifier
                                .width(220.dp)
                                .aspectRatio(16f / 9f)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(TvDimens.CardRadius)),
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = media.title,
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (media.titles.size > 1) {
                                Text(
                                    text = media.titles[1],
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                    modifier = Modifier.shadow(
                                        elevation = 6.dp,
                                        shape = androidx.compose.ui.graphics.RectangleShape,
                                        ambientColor = Color.Black,
                                        spotColor = Color.Black,
                                    ),
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                media.episodeCount?.let { TvBadge(text = "$it Episodes") }
                                media.format?.let { TvBadge(text = it.replace('_', ' ')) }
                                media.status.raw?.let { TvBadge(text = it.replace('_', ' ')) }
                                media.seasonYear?.let { TvBadge(text = it.toString()) }
                                media.score?.let { TvBadge(text = "$it %") }
                                media.durationMinutes?.let { TvBadge(text = "$it min") }
                            }

                            // When the next episode airs. A series that is still
                            // running is worth telling the user about.
                            val nextEpisode = media.nextAiringAt
                            if (nextEpisode != null) {
                                val number = media.nextAiringEpisode
                                Text(
                                    text = if (number != null) {
                                        "Episode $number airs ${dev.animeplayer.tv.ui.model.relativeTime(nextEpisode)}"
                                    } else {
                                        "The next episode airs ${dev.animeplayer.tv.ui.model.relativeTime(nextEpisode)}"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            val description = media.description?.replace('\n', ' ')
                            if (description != null) {
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            TvButtonRow {
                                TvButton(
                                    text = "Play",
                                    icon = Icons.Filled.PlayArrow,
                                    enabled = state.slug != null,
                                    modifier = Modifier.focusRequester(playRequester),
                                    onClick = {
                                        val first = (state.episodes as? TvState.Ready)?.value?.firstOrNull()
                                        if (first != null) onPlay(media.toCard(), first.number)
                                    },
                                )
                                TvIconButton(
                                    icon = if (state.isFavourite) {
                                        Icons.Filled.Favorite
                                    } else {
                                        Icons.Filled.FavoriteBorder
                                    },
                                    onClick = { model.toggleFavourite() },
                                    contentDescription = "Favourite",
                                    scrim = true,
                                )
                                TvIconButton(
                                    icon = Icons.Filled.ArrowBack,
                                    onClick = onBack,
                                    contentDescription = "Back",
                                    scrim = true,
                                )
                            }
                        }
                    }
                }

                item(key = "episodes") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                        )
                        when (val episodes = state.episodes) {
                            is TvState.Loading -> Text(
                                text = "The episodes are loading.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                            )

                            is TvState.Failed -> Text(
                                text = episodes.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                            )

                            is TvState.Ready -> LazyVerticalGrid(
                                columns = GridCells.Fixed(6),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(132.dp),
                                contentPadding = PaddingValues(horizontal = TvDimens.ScreenMargin),
                                horizontalArrangement = Arrangement.spacedBy(TvDimens.RailGap),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(episodes.value, key = { it.number }) { episode ->
                                    EpisodeTile(
                                        episode = episode,
                                        onClick = { onPlay(media.toCard(), episode.number) },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "relations") {
                    val relations = (state.relations as? TvState.Ready)?.value.orEmpty()
                    if (relations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Sequels and prequels",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = TvDimens.ScreenMargin),
                                horizontalArrangement = Arrangement.spacedBy(TvDimens.RailGap),
                            ) {
                                items(relations, key = { it.media.id }) { relation ->
                                    val card = relation.media.toCard()
                                    Column(
                                        modifier = Modifier.width(200.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        TvCard(
                                            title = card.title,
                                            artwork = card.posterUrl ?: card.bannerUrl,
                                            tint = card.posterColor,
                                            metadata = card.infoLine,
                                            width = TvDimens.PosterCardWidth,
                                            onClick = { onOpen(card) },
                                        )
                                        Text(
                                            text = relation.relationType
                                                .lowercase()
                                                .replace('_', ' ')
                                                .replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "more") {
                    val recommendations =
                        (state.recommendations as? TvState.Ready)?.value.orEmpty()
                    if (recommendations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "More like this",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = TvDimens.ScreenMargin),
                                horizontalArrangement = Arrangement.spacedBy(TvDimens.RailGap),
                            ) {
                                items(recommendations, key = { it.id }) { entry ->
                                    val card = entry.toCard()
                                    TvCard(
                                        title = card.title,
                                        artwork = card.posterUrl ?: card.bannerUrl,
                                        tint = card.posterColor,
                                        metadata = card.infoLine,
                                        statusLine = card.statusLine,
                                        width = TvDimens.PosterCardWidth,
                                        onClick = { onOpen(card) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One episode of the list.
 */
@Composable
private fun EpisodeTile(
    episode: EpisodeRow,
    onClick: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(TvDimens.CardRadius)
    val accent = MaterialTheme.colorScheme.primary

    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = dev.animeplayer.tv.ui.focus.TvFocus.shape(shape),
        scale = dev.animeplayer.tv.ui.focus.TvFocus.scale(),
        glow = dev.animeplayer.tv.ui.focus.TvFocus.glow(accent),
        border = dev.animeplayer.tv.ui.focus.TvFocus.border(shape),
        colors = dev.animeplayer.tv.ui.focus.TvFocus.colors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column {
            Box {
                TvArtwork(
                    url = episode.thumbnail,
                    tint = MaterialTheme.colorScheme.surfaceVariant,
                    contentDescription = episode.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                if (episode.thumbnail == null) {
                    Text(
                        text = episode.number,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                TvBadge(
                    text = "EP ${episode.number}",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                )
                val airDateLine = episode.airDateLine
                if (airDateLine != null) {
                    TvBadge(
                        text = airDateLine,
                        tone = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                    )
                }
                if (episode.isWatched) {
                    TvBadge(
                        text = "✓",
                        tone = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    )
                } else if (episode.watched > 0f) {
                    TvProgressBar(
                        progress = episode.watched,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                    )
                }
            }
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The background colour of the theme. */
@Composable
private fun materialThemeBackground(): Color = MaterialTheme.colorScheme.background

/** `#RRGGBB` to a colour. */
private fun String.toColor(): Color? {
    val hex = removePrefix("#")
    if (hex.length != 6) return null
    val value = hex.toLongOrNull(16) ?: return null
    return Color(
        red = ((value shr 16) and 0xFF) / 255f,
        green = ((value shr 8) and 0xFF) / 255f,
        blue = (value and 0xFF) / 255f,
    )
}
