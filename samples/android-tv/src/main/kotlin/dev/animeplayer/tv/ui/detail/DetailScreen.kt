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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.animeplayer.core.model.CatalogMedia
import dev.animeplayer.tv.AppContainer
import dev.animeplayer.tv.data.TvState
import dev.animeplayer.tv.ui.components.TvArtwork
import dev.animeplayer.tv.ui.components.TvBadge
import dev.animeplayer.tv.ui.components.TvButton
import dev.animeplayer.tv.ui.components.TvButtonRow
import dev.animeplayer.tv.ui.components.TvCard
import dev.animeplayer.tv.ui.components.TvErrorState
import dev.animeplayer.tv.ui.components.TvIconButton
import dev.animeplayer.tv.ui.components.TvLoading
import dev.animeplayer.tv.ui.components.TvProgressBar
import dev.animeplayer.tv.ui.focus.FocusEntry
import dev.animeplayer.tv.ui.focus.FocusKeys
import dev.animeplayer.tv.ui.focus.FocusMemory
import dev.animeplayer.tv.ui.focus.TvFocus
import dev.animeplayer.tv.ui.focus.TvRail
import dev.animeplayer.tv.ui.model.CatalogCard
import dev.animeplayer.tv.ui.model.toCard
import dev.animeplayer.tv.ui.theme.LocalTvLayout
import dev.animeplayer.tv.ui.theme.TvDimens

private val EpisodeCardWidth: Dp = 276.dp

/**
 * The details of a series, arranged like a streaming catalogue: a cinematic feature
 * banner, clear actions, then a horizontal rail of episode artwork.
 *
 * @param container the container of the dependencies.
 * @param memory remembers the focused episode while the viewer visits the player.
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

    val playRequester = remember { FocusRequester() }
    entry.Register(playRequester)
    val media = (state.media as? TvState.Ready)?.value
    val episodes = (state.episodes as? TvState.Ready)?.value.orEmpty()
    val firstEpisode = episodes.firstOrNull()
    val canPlay = state.slug != null && firstEpisode != null
    val layout = LocalTvLayout.current
    val heroHeight = (layout.height * 0.78f).coerceIn(360.dp, 520.dp)

    LaunchedEffect(state.media, canPlay) {
        if (state.media is TvState.Ready && canPlay) {
            runCatching { playRequester.requestFocus() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
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
                item(key = "feature") {
                    DetailHero(
                        media = media,
                        height = heroHeight,
                        playRequester = playRequester,
                        canPlay = canPlay,
                        isFavourite = state.isFavourite,
                        onPlay = {
                            firstEpisode?.let { onPlay(media.toCard(), it.number) }
                        },
                        onFavourite = { model.toggleFavourite() },
                        onBack = onBack,
                    )
                }

                item(key = "episodes") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = TvDimens.ScreenMargin),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Episodes",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = "Choose an episode to start watching",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.episodes is TvState.Ready && episodes.isNotEmpty()) {
                                Text(
                                    text = "${episodes.size} EPISODES",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        when (val episodeState = state.episodes) {
                            TvState.Loading -> EpisodeSkeletonRail()
                            is TvState.Failed -> Text(
                                text = episodeState.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                            )

                            is TvState.Ready -> {
                                if (episodeState.value.isEmpty()) {
                                    Text(
                                        text = "No episodes are available yet.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = TvDimens.ScreenMargin),
                                    )
                                } else {
                                    TvRail(
                                        items = episodeState.value,
                                        key = { it.number },
                                        screen = FocusKeys.detail("episodes"),
                                        memory = memory,
                                        contentPadding = PaddingValues(
                                            horizontal = TvDimens.ScreenMargin,
                                            vertical = 8.dp,
                                        ),
                                        itemSpacing = 18.dp,
                                        onNavigateUp = {
                                            runCatching { playRequester.requestFocus() }
                                        },
                                    ) { episode, itemModifier ->
                                        EpisodeTile(
                                            episode = episode,
                                            modifier = itemModifier,
                                            onClick = { onPlay(media.toCard(), episode.number) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "relations") {
                    val relations = (state.relations as? TvState.Ready)?.value.orEmpty()
                    if (relations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Related series",
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
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                            text = relation.relationType.toDisplayLabel(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "recommendations") {
                    val recommendations =
                        (state.recommendations as? TvState.Ready)?.value.orEmpty()
                    if (recommendations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                items(recommendations, key = { it.id }) { item ->
                                    val card = item.toCard()
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

@Composable
private fun DetailHero(
    media: CatalogMedia,
    height: Dp,
    playRequester: FocusRequester,
    canPlay: Boolean,
    isFavourite: Boolean,
    onPlay: () -> Unit,
    onFavourite: () -> Unit,
    onBack: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val metadata = buildList {
        media.seasonYear?.let { add(it.toString()) }
        media.format?.let { add(it.toDisplayLabel()) }
        media.episodeCount?.let { add("$it episodes") }
        media.durationMinutes?.let { add("$it min") }
        media.score?.let { add("$it% score") }
    }.joinToString("  •  ")
    val genres = media.genres.take(4).joinToString("  ·  ")

    Box(modifier = Modifier.fillMaxWidth().height(height)) {
        TvArtwork(
            url = media.bannerUrl ?: media.posterUrl,
            tint = media.posterColor?.toColor(),
            contentDescription = media.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.78f),
                            Color.Black.copy(alpha = 0.42f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.42f to Color.Transparent,
                        0.76f to Color.Black.copy(alpha = 0.60f),
                        1f to background,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = TvDimens.ScreenMargin, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = "ANIME SERIES",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = media.title.ifBlank { "Untitled series" },
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 760.dp),
            )
            media.titles.getOrNull(1)?.takeIf { it.isNotBlank() && it != media.title }?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (genres.isNotBlank()) {
                Text(
                    text = genres,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            media.nextAiringAt?.let { nextEpisode ->
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

            media.description?.replace('\n', ' ')?.trim()?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }

            TvButtonRow(modifier = Modifier.padding(top = 4.dp)) {
                TvButton(
                    text = "Play",
                    icon = Icons.Filled.PlayArrow,
                    enabled = canPlay,
                    modifier = Modifier.focusRequester(playRequester),
                    onClick = onPlay,
                )
                TvButton(
                    text = if (isFavourite) "In My List" else "My List",
                    icon = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    prominent = false,
                    onClick = onFavourite,
                )
                TvIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    contentDescription = "Back",
                    scrim = true,
                )
            }
        }
    }
}

@Composable
private fun EpisodeTile(
    episode: EpisodeRow,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(TvDimens.CardRadius)
    val metadata = buildList {
        episode.duration?.takeIf { it > 0 }?.let { add("$it min") }
        episode.airDateLine?.let { add(it) }
        if (episode.isWatched) add("Watched")
    }.joinToString("  ·  ")

    Surface(
        onClick = onClick,
        modifier = modifier.width(EpisodeCardWidth),
        shape = TvFocus.shape(shape),
        scale = TvFocus.scale(),
        glow = TvFocus.glow(),
        border = TvFocus.border(shape),
        colors = TvFocus.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                TvArtwork(
                    url = episode.thumbnail,
                    tint = MaterialTheme.colorScheme.surfaceVariant,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent),
                            ),
                        ),
                )
                TvBadge(
                    text = "EP ${episode.number}",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                )
                if (episode.isWatched) {
                    TvBadge(
                        text = "WATCHED",
                        tone = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    )
                } else if (episode.watched > 0f) {
                    TvProgressBar(
                        progress = episode.watched,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
                if (episode.thumbnail.isNullOrBlank()) {
                    Text(
                        text = episode.number,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = episode.title.ifBlank { "Episode ${episode.number}" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                episode.description?.replace('\n', ' ')?.trim()?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeSkeletonRail() {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = TvDimens.ScreenMargin),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(4) {
            Column(
                modifier = Modifier.width(EpisodeCardWidth),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(TvDimens.CardRadius))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.46f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)),
                )
            }
        }
    }
}

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

private fun String.toDisplayLabel(): String =
    lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
