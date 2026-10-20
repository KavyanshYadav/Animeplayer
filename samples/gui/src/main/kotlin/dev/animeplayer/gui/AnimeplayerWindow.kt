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
package dev.animeplayer.gui

import dev.animeplayer.core.model.Anime
import dev.animeplayer.core.model.AudioTrack
import dev.animeplayer.core.model.Episode
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.model.QualityPreference
import dev.animeplayer.core.spi.PlaybackRequest
import dev.animeplayer.core.spi.PlaybackSession
import dev.animeplayer.core.spi.WatchHistoryEntry
import dev.animeplayer.core.spi.WatchHistoryStore
import dev.animeplayer.network.KtorHttpTransport
import dev.animeplayer.network.TransportConfig
import dev.animeplayer.network.createDefaultHttpClient
import dev.animeplayer.player.mpv.MpvPlayerEngine
import dev.animeplayer.sources.hianime.HianimeConfig
import dev.animeplayer.sources.hianime.HianimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Component
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import kotlin.time.Clock

/**
 * The desktop GUI.
 *
 * It drives the library the same way any consumer would: search, pick a series, pick
 * an episode, resolve it, and hand the result to a player.
 *
 * The video itself is shown by `mpv` in its own window — the GUI is the remote control
 * for it. That is deliberate: `mpv` is the reference player on Linux, it handles every
 * codec, and the library's [MpvPlayerEngine] already speaks its JSON IPC protocol, so
 * the GUI gets real playback state, seeking and subtitle control for free.
 */
public class AnimeplayerWindow(
    private val source: HianimeSource,
    private val playerEngine: MpvPlayerEngine,
    private val history: WatchHistoryStore?,
) : JFrame("animeplayer") {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

    private val searchField = JTextField(28)
    private val searchButton = JButton("Search")
    private val qualityBox = JComboBox(arrayOf("Best", "1080p", "720p", "480p", "360p", "Worst"))
    private val audioBox = JComboBox(arrayOf("Sub", "Dub"))

    private val seriesModel = DefaultListModel<Anime>()
    private val seriesList = JList(seriesModel)
    private val episodeModel = DefaultListModel<Episode>()
    private val episodeList = JList(episodeModel)

    private val playButton = JButton("Play")
    private val pauseButton = JButton("Pause")
    private val stopButton = JButton("Stop")
    private val seekSlider = JSlider(0, 1000, 0)
    private val positionLabel = JLabel("00:00 / 00:00")
    private val subtitleBox = JComboBox<String>()
    private val statusLabel = JLabel(" ")
    private val progressBar = JProgressBar()

    private var session: PlaybackSession? = null
    private var stateJob: Job? = null
    private var currentAnime: Anime? = null
    private var currentEpisode: Episode? = null
    private var currentSource: PlayableSource? = null
    private var durationMs: Long = 0L
    private var seeking = false

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        layout = BorderLayout(8, 8)

        add(buildTopBar(), BorderLayout.NORTH)
        add(buildLists(), BorderLayout.CENTER)
        add(buildBottomBar(), BorderLayout.SOUTH)

        wireUp()

        setSize(960, 620)
        setLocationRelativeTo(null)
    }

    private fun buildTopBar(): JPanel {
        val panel = JPanel(BorderLayout(8, 0))
        panel.add(searchField, BorderLayout.CENTER)

        val controls = JPanel()
        controls.add(searchButton)
        controls.add(JLabel("Quality"))
        controls.add(qualityBox)
        controls.add(JLabel("Audio"))
        controls.add(audioBox)
        panel.add(controls, BorderLayout.EAST)
        return panel
    }

    private fun buildLists(): JPanel {
        val panel = JPanel(GridLayout(1, 2, 8, 0))

        val seriesPanel = JPanel(BorderLayout())
        seriesPanel.add(title("Series"), BorderLayout.NORTH)
        seriesList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        seriesList.setCellRenderer(seriesRenderer())
        seriesPanel.add(seriesList, BorderLayout.CENTER)

        val episodePanel = JPanel(BorderLayout())
        episodePanel.add(title("Episodes"), BorderLayout.NORTH)
        episodeList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        episodeList.setCellRenderer(episodeRenderer())
        episodePanel.add(episodeList, BorderLayout.CENTER)

        panel.add(seriesPanel)
        panel.add(episodePanel)
        return panel
    }

    private fun buildBottomBar(): JPanel {
        val panel = JPanel(BorderLayout(8, 4))

        val transport = JPanel()
        transport.add(playButton)
        transport.add(pauseButton)
        transport.add(stopButton)

        seekSlider.preferredSize = Dimension(320, 20)
        transport.add(seekSlider)
        transport.add(positionLabel)
        transport.add(JLabel("Subtitle"))
        subtitleBox.preferredSize = Dimension(140, 24)
        transport.add(subtitleBox)

        panel.add(transport, BorderLayout.NORTH)
        panel.add(progressBar, BorderLayout.CENTER)
        statusLabel.font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        panel.add(statusLabel, BorderLayout.SOUTH)
        return panel
    }

    /** Shows the title instead of the raw data class. */
    private fun seriesRenderer() = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is Anime) {
                text = value.title + (value.japaneseTitle?.let { "  ·  $it" } ?: "")
            }
            return component
        }
    }

    /** Shows the episode number instead of the raw data class. */
    private fun episodeRenderer() = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is Episode) {
                text = "Episode ${value.number}" + (value.title?.let { "  ·  $it" } ?: "")
            }
            return component
        }
    }

    private fun title(text: String): JLabel {
        val label = JLabel(text)
        label.border = BorderFactory.createEmptyBorder(4, 2, 4, 2)
        label.font = label.font.deriveFont(Font.BOLD)
        return label
    }

    private fun wireUp() {
        searchButton.addActionListener { search() }
        searchField.addActionListener { search() }

        seriesList.addListSelectionListener { event ->
            if (event.valueIsAdjusting) return@addListSelectionListener
            seriesList.selectedIndex.takeIf { it >= 0 }?.let { loadEpisodes(seriesModel[it]) }
        }

        episodeList.addListSelectionListener { event ->
            if (event.valueIsAdjusting) return@addListSelectionListener
            episodeList.selectedIndex.takeIf { it >= 0 }?.let { playEpisode(episodeModel[it]) }
        }

        playButton.addActionListener { scope.launch { session?.play() } }
        pauseButton.addActionListener { scope.launch { session?.pause() } }
        stopButton.addActionListener { stopPlayback() }

        seekSlider.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                seeking = true
            }

            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                seeking = false
                val target = seekSlider.value * (durationMs / 1000L)
                scope.launch { session?.seekTo(target) }
            }
        })

        subtitleBox.addActionListener {
            val index = subtitleBox.selectedIndex
            scope.launch {
                val track = currentSource?.subtitles?.getOrNull(index)
                session?.selectSubtitle(track)
            }
        }

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) {
                stopPlayback()
                scope.cancel()
                dispose()
            }
        })
    }

    private fun search() {
        val query = searchField.text.trim()
        if (query.isEmpty()) return

        statusLabel.text = "Searching for '$query'…"
        progressBar.isIndeterminate = true
        searchButton.isEnabled = false

        scope.launch {
            try {
                val results = source.search(query)
                seriesModel.clear()
                results.forEach { seriesModel.addElement(it) }
                episodeModel.clear()
                statusLabel.text = if (results.isEmpty()) {
                    "No results for '$query'."
                } else {
                    "${results.size} results. Pick a series."
                }
            } catch (error: Exception) {
                statusLabel.text = "Search failed: ${error.message}"
            } finally {
                progressBar.isIndeterminate = false
                searchButton.isEnabled = true
            }
        }
    }

    private fun loadEpisodes(anime: Anime) {
        currentAnime = anime
        episodeModel.clear()
        statusLabel.text = "Loading the episodes of ${anime.title}…"
        progressBar.isIndeterminate = true

        scope.launch {
            try {
                val list = source.episodes(anime.id)
                list.episodes.forEach { episodeModel.addElement(it) }
                statusLabel.text = "${anime.title} — ${list.episodes.size} episodes."
            } catch (error: Exception) {
                statusLabel.text = "Could not load the episodes: ${error.message}"
            } finally {
                progressBar.isIndeterminate = false
            }
        }
    }

    private fun playEpisode(episode: Episode) {
        val anime = currentAnime ?: return
        currentEpisode = episode

        statusLabel.text = "Resolving ${anime.title} episode ${episode.number}…"
        progressBar.isIndeterminate = true

        scope.launch {
            try {
                val playable = source.resolve(
                    id = anime.id,
                    episode = episode,
                    audioTrack = if (audioBox.selectedItem == "Dub") AudioTrack.Dub else AudioTrack.Sub,
                    quality = qualityPreference(),
                )
                currentSource = playable
                populateSubtitles(playable)
                startPlayback(anime, episode, playable)
            } catch (error: Exception) {
                statusLabel.text = "Could not resolve the episode: ${error.message}"
            } finally {
                progressBar.isIndeterminate = false
            }
        }
    }

    private fun qualityPreference(): QualityPreference = when (val selected = qualityBox.selectedItem) {
        "Best" -> QualityPreference.Best
        "Worst" -> QualityPreference.Worst
        else -> QualityPreference.Label(Regex(Regex.escape(selected.toString())))
    }

    private fun populateSubtitles(source: PlayableSource) {
        subtitleBox.removeAllItems()
        subtitleBox.addItem("Off")
        source.subtitles.forEach { subtitleBox.addItem(it.label) }
        val defaultIndex = source.subtitles.indexOfFirst { it.isDefault }
        subtitleBox.selectedIndex = if (defaultIndex >= 0) defaultIndex + 1 else 0
    }

    private suspend fun startPlayback(anime: Anime, episode: Episode, playable: PlayableSource) {
        stopPlayback()

        if (!playerEngine.isAvailable) {
            statusLabel.text = "mpv was not found on the PATH — install it with 'pacman -S mpv'."
            return
        }

        val newSession = playerEngine.prepare(
            PlaybackRequest(
                source = playable,
                title = "${anime.title} Episode ${episode.number}",
            ),
        )
        session = newSession

        statusLabel.text = buildString {
            append(anime.title).append(" episode ").append(episode.number)
            append("  [").append(playable.variant.quality.label).append(']')
            append("  ·  referer ").append(playable.referer)
            playable.skip?.intro?.let {
                append("  ·  intro ").append(it.startSeconds).append('-').append(it.endSeconds).append('s')
            }
        }

        stateJob = scope.launch {
            newSession.state.collectLatest { state ->
                durationMs = state.durationMs
                if (!seeking && state.durationMs > 0) {
                    seekSlider.value = ((state.positionMs * 1000) / state.durationMs).toInt()
                        .coerceIn(0, 1000)
                }
                positionLabel.text = "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}"
                state.error?.let { statusLabel.text = "Playback error: $it" }
                saveProgress(anime, episode, state.positionMs, state.durationMs)
            }
        }

        scope.launch { newSession.play() }
    }

    private fun saveProgress(anime: Anime, episode: Episode, positionMs: Long, durationMs: Long) {
        val store = history ?: return
        scope.launch {
            runCatching {
                store.upsert(
                    WatchHistoryEntry(
                        animeId = anime.id,
                        title = anime.title,
                        episodeNumber = episode.number,
                        updatedAt = Clock.System.now(),
                        positionMs = positionMs,
                        durationMs = durationMs,
                    ),
                )
            }
        }
    }

    private fun stopPlayback() {
        stateJob?.cancel()
        stateJob = null
        session?.close()
        session = null
        durationMs = 0L
        seekSlider.value = 0
        positionLabel.text = "00:00 / 00:00"
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}

/**
 * Starts the GUI.
 */
public fun main() {
    val client = createDefaultHttpClient()
    val transport = KtorHttpTransport(
        client = client,
        config = TransportConfig(userAgent = HianimeConfig.DEFAULT_USER_AGENT),
    )
    val source = HianimeSource(transport, HianimeConfig())
    val history = openHistoryStore()

    SwingUtilities.invokeLater {
        AnimeplayerWindow(source, MpvPlayerEngine(), history).isVisible = true
    }
}
